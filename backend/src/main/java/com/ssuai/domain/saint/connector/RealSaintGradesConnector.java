package com.ssuai.domain.saint.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.auth.saint.SaintSessionStore;
import com.ssuai.domain.saint.config.SaintGradesProperties;
import com.ssuai.domain.saint.dto.CourseGrade;
import com.ssuai.domain.saint.dto.GpaSummary;
import com.ssuai.domain.saint.dto.GradesResponse;
import com.ssuai.domain.saint.dto.TermGpa;
import com.ssuai.domain.saint.service.GradesParser;
import com.ssuai.domain.saint.service.WebDynproResponseUnwrapper;
import com.ssuai.domain.saint.service.WebDynproSapEventEncoder;
import com.ssuai.global.exception.ConnectorParseException;
import com.ssuai.global.exception.ConnectorTimeoutException;
import com.ssuai.global.exception.ConnectorUnavailableException;
import com.ssuai.global.exception.SaintSessionExpiredException;

/**
 * Talks to the SAP WebDynpro {@code ZCMB3W0017} component at
 * {@code ecc.ssu.ac.kr:8443} to fetch a student's cumulative grades.
 *
 * <p>Two-step protocol (Task 16 spec §3.5.1 + §3.5.2):
 *
 * <ol>
 *   <li>{@code GET ZCMB3W0017} — the Chrome-like client receives a SAP
 *       WebDynpro JavaScript bootstrap. The connector extracts the initial
 *       {@code sap-wd-secure-id}, sends {@code Form_Request}, then unwraps
 *       the returned {@code <updates><full-update><content-update>[CDATA HTML]}
 *       envelope. Parser pulls the 학기별 GPA history table + 학적부/증명
 *       summary blocks plus the current-default-term detail rows.
 *   <li>For each prior term we still want, {@code POST ZCMB3W0017} with
 *       a {@code SAPEVENTQUEUE} that simulates pressing 이전학기
 *       ({@code WD01F0}). Each response carries a re-rendered page with
 *       the detail table populated for that hop's term. The history /
 *       summaries are unchanged across hops, so we parse them only from
 *       the first GET.
 * </ol>
 *
 * <p>Term mapping: detail rows themselves don't carry a 학기 label, so
 * the N-th prev-press response is mapped to {@code history.get(N)} —
 * mirroring the schedule connector's "WDA7 N회 후 학년도 = currentYear-N"
 * external-tracking pattern.
 *
 * <p>Partial-failure policy: if CSRF rotation breaks mid-iterate we keep
 * whatever terms we already pulled and return. Students would rather see
 * recent terms than a blank page while we investigate.
 */
@Component
@ConditionalOnProperty(name = "ssuai.connector.saint-grades", havingValue = "real")
public class RealSaintGradesConnector implements SaintGradesConnector {

    private static final Logger log = LoggerFactory.getLogger(RealSaintGradesConnector.class);

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String PREV_TERM_BUTTON_ID = "WD01F0";
    private static final int MAX_PREV_HOPS = 20;
    private static final int MAX_INIT_GET_REDIRECTS = 10;

    private final SaintGradesProperties properties;
    private final HttpClient httpClient;
    private final HttpClient httpClientNoRedirect;

    @Autowired
    public RealSaintGradesConnector(SaintGradesProperties properties) {
        this(properties, defaultHttpClient(properties),
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(properties.getTimeout())
                        .build());
    }

    RealSaintGradesConnector(SaintGradesProperties properties, HttpClient httpClient) {
        this(properties, httpClient, httpClient);
    }

    RealSaintGradesConnector(SaintGradesProperties properties,
            HttpClient httpClient, HttpClient httpClientNoRedirect) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.httpClientNoRedirect = httpClientNoRedirect;
    }

    private record InitGetResult(String html, String cookieHeader) {}

    @Override
    public GradesResponse fetchGrades(String studentId, PortalCookies cookies) {
        InitGetResult initGet = httpGetFollowCookies(cookies.rawCookieHeader(),
                properties.getGradesUrl(), "saint grades");
        String rawFirstResponse = initGet.html();
        String mergedCookieHeader = initGet.cookieHeader();

        Optional<String> bootstrapSecureId = WebDynproResponseUnwrapper.extractSecureIdFromAny(rawFirstResponse);
        if (bootstrapSecureId.isEmpty()) {
            String snippet = rawFirstResponse == null ? "(null)"
                    : rawFirstResponse.substring(0, Math.min(300, rawFirstResponse.length())).replaceAll("\\s+", " ");
            log.info("saint grades bootstrap no secure-id: studentFp={} snippet='{}'",
                    SaintSessionStore.fingerprint(studentId), snippet);
            throw new SaintSessionExpiredException("ecc did not provide sap-wd-secure-id on first GET");
        }

        String firstHtml = firstRenderableHtml(rawFirstResponse);
        if (!containsGradesTables(firstHtml)) {
            String initXml = httpPostInitialLoad(mergedCookieHeader, bootstrapSecureId.get(), "ZCMB3W0017",
                    properties.getGradesUrl());
            try {
                firstHtml = WebDynproResponseUnwrapper.extractHtml(initXml);
            } catch (IllegalArgumentException ex) {
                log.info("saint grades init POST non-wrapper: studentFp={}",
                        SaintSessionStore.fingerprint(studentId));
                throw new SaintSessionExpiredException("ecc init POST did not return expected XML wrapper");
            }
        }
        guardAuthOrThrow(firstHtml, studentId);

        Optional<String> secureId = WebDynproResponseUnwrapper.extractSecureId(firstHtml);

        List<TermGpa> history = GradesParser.parseTermHistory(firstHtml);
        GpaSummary academicRecord = GradesParser.parseAcademicSummary(firstHtml);
        GpaSummary certificate = GradesParser.parseCertificateSummary(firstHtml);

        Map<String, List<CourseGrade>> details = new LinkedHashMap<>();
        if (!history.isEmpty()) {
            List<CourseGrade> defaultDetails = GradesParser.parseDetailRows(firstHtml);
            if (!defaultDetails.isEmpty()) {
                details.put(history.get(0).termKey(), defaultDetails);
            }
        }

        int hops = 0;
        for (int i = 1; i < history.size() && hops < MAX_PREV_HOPS; i++) {
            if (secureId.isEmpty()) {
                log.warn("saint grades iterate halted: studentFp={} reason=missing-secure-id index={}",
                        SaintSessionStore.fingerprint(studentId), i);
                break;
            }
            String xmlEnvelope;
            try {
                xmlEnvelope = httpPostButtonPress(mergedCookieHeader, secureId.get(),
                        PREV_TERM_BUTTON_ID);
            } catch (SaintSessionExpiredException exception) {
                log.info("saint grades iterate halted: studentFp={} reason=session-expired index={}",
                        SaintSessionStore.fingerprint(studentId), i);
                break;
            }
            String prevHtml;
            try {
                prevHtml = WebDynproResponseUnwrapper.extractHtml(xmlEnvelope);
            } catch (IllegalArgumentException exception) {
                log.warn("saint grades iterate halted: studentFp={} reason=non-wrapper-response index={}",
                        SaintSessionStore.fingerprint(studentId), i);
                break;
            }
            secureId = WebDynproResponseUnwrapper.extractSecureId(prevHtml);
            List<CourseGrade> rows = GradesParser.parseDetailRows(prevHtml);
            if (!rows.isEmpty()) {
                details.put(history.get(i).termKey(), rows);
            }
            hops++;
        }
        log.info("saint grades fetched: studentFp={} history={} detailTerms={} hops={}",
                SaintSessionStore.fingerprint(studentId), history.size(), details.size(), hops);
        return new GradesResponse(history, academicRecord, certificate, details);
    }

    private InitGetResult httpGetFollowCookies(String startCookieHeader, String startUrl, String logPrefix) {
        String cookieHeader = startCookieHeader;
        String url = startUrl;
        for (int hop = 0; hop <= MAX_INIT_GET_REDIRECTS; hop++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cookie", cookieHeader)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ko")
                    .header("User-Agent", BROWSER_UA)
                    .timeout(properties.getTimeout())
                    .GET()
                    .build();
            HttpResponse<String> response;
            try {
                response = httpClientNoRedirect.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (java.net.http.HttpTimeoutException e) {
                throw new ConnectorTimeoutException(e);
            } catch (IOException e) {
                log.warn("{} connector IOException on GET", logPrefix, e);
                throw new ConnectorUnavailableException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectorUnavailableException(e);
            }
            cookieHeader = mergeSetCookies(cookieHeader, response.headers().allValues("Set-Cookie"));
            int status = response.statusCode();
            if (status / 100 == 3) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    log.warn("{} connector redirect missing Location: status={}", logPrefix, status);
                    throw new ConnectorUnavailableException();
                }
                if (!location.startsWith("http")) {
                    URI base = URI.create(url);
                    location = base.getScheme() + "://" + base.getAuthority() + location;
                }
                url = location;
                continue;
            }
            if (status / 100 == 2) {
                return new InitGetResult(response.body(), cookieHeader);
            }
            if (status / 100 == 5) {
                log.warn("{} connector 5xx on GET: status={}", logPrefix, status);
                throw new ConnectorUnavailableException();
            }
            log.warn("{} connector unexpected status on GET: status={}", logPrefix, status);
            throw new ConnectorUnavailableException();
        }
        log.warn("{} connector too many redirects on GET", logPrefix);
        throw new ConnectorUnavailableException();
    }

    private String httpPostInitialLoad(String cookieHeader, String secureId, String appName, String url) {
        String queue = WebDynproSapEventEncoder.encodeInitialLoad();
        String body = formEncoded(Map.of(
                "sap-charset", "utf-8",
                "sap-wd-secure-id", secureId,
                "fesrAppName", appName,
                "fesrUseBeacon", "true",
                "SAPEVENTQUEUE", queue));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Cookie", cookieHeader)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("Accept", "application/xml,text/html")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-XHR-Logon", "accept")
                .header("User-Agent", BROWSER_UA)
                .timeout(properties.getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request).body();
    }

    private String httpPostButtonPress(String cookieHeader, String secureId, String buttonId) {
        String queue = WebDynproSapEventEncoder.encodeButtonPress(buttonId);
        String body = formEncoded(Map.of(
                "sap-charset", "utf-8",
                "sap-wd-secure-id", secureId,
                "fesrAppName", "ZCMB3W0017",
                "fesrUseBeacon", "true",
                "SAPEVENTQUEUE", queue));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getGradesUrl()))
                .header("Cookie", cookieHeader)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("Accept", "application/xml,text/html")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-XHR-Logon", "accept")
                .header("User-Agent", BROWSER_UA)
                .timeout(properties.getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request).body();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status / 100 == 2) {
                return response;
            }
            if (status == 401 || status == 403) {
                log.info("saint grades connector auth rejected: status={}", status);
                throw new SaintSessionExpiredException("ecc rejected WebDynpro request with " + status);
            }
            if (status / 100 == 5) {
                String snippet = response.body() == null ? "(null)"
                        : response.body().substring(0, Math.min(300, response.body().length()));
                log.warn("saint grades connector 5xx: status={} body='{}'", status, snippet);
                throw new ConnectorUnavailableException();
            }
            String body4xx = response.body() == null ? "(null)"
                    : response.body().substring(0, Math.min(500, response.body().length())).replaceAll("\\s+", " ");
            log.warn("saint grades connector unexpected status={} body='{}'", status, body4xx);
            throw new ConnectorParseException();
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ConnectorTimeoutException(exception);
        } catch (IOException exception) {
            log.warn("saint grades connector IOException", exception);
            throw new ConnectorUnavailableException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConnectorUnavailableException(exception);
        }
    }

    private void guardAuthOrThrow(String html, String studentId) {
        if (html == null || html.isBlank()) {
            throw new SaintSessionExpiredException("ecc returned empty body");
        }
        if (!containsGradesTables(html)) {
            String snippet = html.substring(0, Math.min(500, html.length())).replaceAll("\\s+", " ");
            log.info("saint grades auth gate tripped: studentFp={} htmlSnippet='{}'",
                    SaintSessionStore.fingerprint(studentId), snippet);
            throw new SaintSessionExpiredException(
                    "ecc did not return the grades tables (likely logon redirect)");
        }
    }

    private static String firstRenderableHtml(String responseBody) {
        try {
            return WebDynproResponseUnwrapper.extractHtml(responseBody);
        } catch (IllegalArgumentException ignored) {
            return responseBody;
        }
    }

    private static boolean containsGradesTables(String html) {
        return html != null && !html.isBlank()
                && org.jsoup.Jsoup.parse(html).selectFirst("tbody[id$=-contentTBody]") != null;
    }

    static String mergeSetCookies(String existing, List<String> setCookieHeaders) {
        LinkedHashMap<String, String> jar = new LinkedHashMap<>();
        if (existing != null && !existing.isBlank()) {
            for (String pair : existing.split(";")) {
                addPair(jar, pair.trim());
            }
        }
        if (setCookieHeaders != null) {
            for (String setCookie : setCookieHeaders) {
                if (setCookie == null || setCookie.isBlank()) {
                    continue;
                }
                int semi = setCookie.indexOf(';');
                String pair = semi < 0 ? setCookie : setCookie.substring(0, semi);
                addPair(jar, pair.trim());
            }
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : jar.entrySet()) {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private static void addPair(LinkedHashMap<String, String> jar, String pair) {
        if (pair == null || pair.isEmpty()) {
            return;
        }
        int eq = pair.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String name = pair.substring(0, eq).trim();
        String value = pair.substring(eq + 1).trim();
        if (name.isEmpty()) {
            return;
        }
        jar.put(name, value);
    }

    private static String formEncoded(Map<String, String> form) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static HttpClient defaultHttpClient(SaintGradesProperties properties) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(properties.getTimeout())
                .build();
    }
}
