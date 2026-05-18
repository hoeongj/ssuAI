package com.ssuai.domain.saint.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.auth.saint.SaintSessionStore;
import com.ssuai.domain.saint.config.SaintScheduleProperties;
import com.ssuai.domain.saint.dto.ScheduleEntry;
import com.ssuai.domain.saint.dto.ScheduleResponse;
import com.ssuai.domain.saint.dto.TermSchedule;
import com.ssuai.domain.saint.service.SaintScheduleParser;
import com.ssuai.domain.saint.service.WebDynproResponseUnwrapper;
import com.ssuai.domain.saint.service.WebDynproSapEventEncoder;
import com.ssuai.global.exception.ConnectorParseException;
import com.ssuai.global.exception.ConnectorTimeoutException;
import com.ssuai.global.exception.ConnectorUnavailableException;
import com.ssuai.global.exception.SaintSessionExpiredException;

/**
 * Talks to the SAP WebDynpro {@code ZCMW2102} component at
 * {@code ecc.ssu.ac.kr:8443} to fetch the student's cumulative timetable.
 *
 * <p>Two-step protocol (Task 16 spec §3.3 + §3.4):
 *
 * <ol>
 *   <li>{@code GET ZCMW2102} with the portal cookies attached. The
 *       Chrome-like client receives a SAP WebDynpro JavaScript bootstrap
 *       containing the initial {@code sap-wd-secure-id}. The connector
 *       sends {@code Form_Request} to obtain the rendered HTML for the
 *       currently selected term. The displayed {@code (학년도, 학기)} pair
 *       is parsed from the page's dropdowns — never derived from the wall
 *       clock — so the label we emit matches what u-SAINT actually shows.</li>
 *   <li>For each prior term we still want, {@code POST ZCMW2102} with
 *       a {@code SAPEVENTQUEUE} that simulates pressing the
 *       "이전학기" button ({@code WDA7}). The response is a WebDynpro
 *       {@code <updates><full-update>...CDATA...} envelope carrying
 *       the re-rendered page (timetable + dropdowns + new secure-id).
 *       PREV cycles 1학기 → 겨울학기 of the previous year, otherwise
 *       term-1 within the same year — confirmed by the 2026-05-17
 *       spike. The original spec §3.4 claim "WDA7 = previous year,
 *       term unchanged" is retired.</li>
 * </ol>
 *
 * <p>Cookie handling — the first GET typically picks up additional ecc
 * session cookies ({@code SAP_SESSIONID_SSP_100}, {@code sap-usercontext})
 * via {@code Set-Cookie}. We merge those into the cookie header used by
 * subsequent POSTs so SAP's session affinity holds across the iterate.
 *
 * <p>Partial-failure policy — if CSRF rotation breaks mid-iterate, or
 * the response stops carrying parseable (year, term) dropdowns, we stop
 * walking back and return whatever terms we already collected rather
 * than failing the whole request. The student would rather see recent
 * terms than a blank screen while we investigate.
 */
@Component
@ConditionalOnProperty(name = "ssuai.connector.saint-schedule", havingValue = "real")
public class RealSaintScheduleConnector implements SaintScheduleConnector {

    private static final Logger log = LoggerFactory.getLogger(RealSaintScheduleConnector.class);

    private static final String BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String PREV_TERM_BUTTON_ID = "WDA7";
    private static final String TIMETABLE_TABLE_SELECTOR = "tbody[id$=-contentTBody]";
    // 4 terms × ~6 years of enrollment + slack. The real worst case for
    // a 4-year student is 16 hops; we keep some margin for re-admitted
    // students whose enrollment year is older.
    private static final int MAX_PREV_TERM_HOPS = 32;

    private final SaintScheduleProperties properties;
    @SuppressWarnings("unused") // retained for future cache TTL keying
    private final Clock clock;
    private final HttpClient httpClient;

    @Autowired
    public RealSaintScheduleConnector(SaintScheduleProperties properties) {
        this(properties, Clock.systemUTC(), defaultHttpClient(properties));
    }

    RealSaintScheduleConnector(SaintScheduleProperties properties, Clock clock, HttpClient httpClient) {
        this.properties = properties;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    @Override
    public ScheduleResponse fetchSchedule(String studentId, PortalCookies cookies) {
        int enrollmentYear = SaintScheduleHelpers.parseEnrollmentYear(studentId);

        HttpResponse<String> firstResponse = httpGet(cookies.rawCookieHeader());
        String bootstrapHtml = firstResponse.body();

        String mergedCookieHeader = mergeSetCookies(cookies.rawCookieHeader(),
                firstResponse.headers().allValues("Set-Cookie"));

        // Chrome UA causes SAP WDA to return a JS bootstrap page on first GET.
        // We need a Form_Request POST to trigger the full server-side render.
        Optional<String> bootstrapSecureId = WebDynproResponseUnwrapper.extractSecureId(bootstrapHtml);
        if (bootstrapSecureId.isEmpty()) {
            String snippet = bootstrapHtml == null ? "(null)"
                    : bootstrapHtml.substring(0, Math.min(300, bootstrapHtml.length())).replaceAll("\\s+", " ");
            log.info("saint schedule bootstrap no secure-id: studentFp={} snippet='{}'",
                    SaintSessionStore.fingerprint(studentId), snippet);
            throw new SaintSessionExpiredException("ecc did not provide sap-wd-secure-id on first GET");
        }

        String initXml = httpPostInitialLoad(mergedCookieHeader, bootstrapSecureId.get(), "ZCMW2102",
                properties.getTimetableUrl());
        String currentHtml;
        try {
            currentHtml = WebDynproResponseUnwrapper.extractHtml(initXml);
        } catch (IllegalArgumentException ex) {
            log.info("saint schedule init POST non-wrapper: studentFp={}",
                    SaintSessionStore.fingerprint(studentId));
            throw new SaintSessionExpiredException("ecc init POST did not return expected XML wrapper");
        }
        guardAuthOrThrow(currentHtml, studentId);

        Optional<String> secureId = WebDynproResponseUnwrapper.extractSecureId(currentHtml);

        int displayedYear = SaintScheduleParser.parseDisplayedYear(currentHtml);
        int displayedTerm = SaintScheduleParser.parseDisplayedTerm(currentHtml);
        if (displayedYear < 0 || displayedTerm < 0) {
            log.warn("saint schedule first-get missing dropdowns: studentFp={} year={} term={}",
                    SaintSessionStore.fingerprint(studentId), displayedYear, displayedTerm);
            throw new ConnectorParseException();
        }

        int currentYear = displayedYear;
        int currentTerm = displayedTerm;
        List<TermSchedule> terms = new ArrayList<>();
        terms.add(new TermSchedule(displayedYear, displayedTerm,
                SaintScheduleParser.parse(currentHtml)));

        int hops = 0;
        while (hops < MAX_PREV_TERM_HOPS && !atEnrollmentStart(displayedYear, displayedTerm, enrollmentYear)) {
            if (secureId.isEmpty()) {
                log.warn("saint schedule iterate halted: studentFp={} reason=missing-secure-id year={} term={}",
                        SaintSessionStore.fingerprint(studentId), displayedYear, displayedTerm);
                break;
            }
            String xmlEnvelope = httpPostButtonPress(mergedCookieHeader, secureId.get(),
                    PREV_TERM_BUTTON_ID);
            String prevHtml = WebDynproResponseUnwrapper.extractHtml(xmlEnvelope);
            secureId = WebDynproResponseUnwrapper.extractSecureId(prevHtml);

            int prevYear = SaintScheduleParser.parseDisplayedYear(prevHtml);
            int prevTerm = SaintScheduleParser.parseDisplayedTerm(prevHtml);
            if (prevYear < 0 || prevTerm < 0) {
                // u-SAINT 가 dropdown 없는 응답을 내려보내면 cycle 정확도 보장 못 함.
                // expected 위치를 fallback 으로 사용해서 (year, term) label 은 유지.
                SaintScheduleHelpers.TermPosition expected =
                        SaintScheduleHelpers.previousTerm(displayedYear, displayedTerm);
                prevYear = expected.year();
                prevTerm = expected.term();
                log.warn("saint schedule iterate dropdown-missing: studentFp={} fallbackYear={} fallbackTerm={}",
                        SaintSessionStore.fingerprint(studentId), prevYear, prevTerm);
            }

            terms.add(new TermSchedule(prevYear, prevTerm, SaintScheduleParser.parse(prevHtml)));
            displayedYear = prevYear;
            displayedTerm = prevTerm;
            hops++;
        }
        log.info("saint schedule fetched: studentFp={} terms={} entries={} hops={}",
                SaintSessionStore.fingerprint(studentId), terms.size(),
                terms.stream().mapToInt(t -> t.entries().size()).sum(), hops);
        return new ScheduleResponse(enrollmentYear, currentYear, currentTerm, terms);
    }

    private static boolean atEnrollmentStart(int year, int term, int enrollmentYear) {
        return year <= enrollmentYear && term <= SaintScheduleHelpers.TERM_SPRING;
    }

    private HttpResponse<String> httpGet(String cookieHeader) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTimetableUrl()))
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ko")
                .header("User-Agent", BROWSER_UA)
                .timeout(properties.getTimeout())
                .GET()
                .build();
        return send(request);
    }

    private String httpPostButtonPress(String cookieHeader, String secureId, String buttonId) {
        String queue = WebDynproSapEventEncoder.encodeButtonPress(buttonId);
        String body = formEncoded(Map.of(
                "sap-charset", "utf-8",
                "sap-wd-secure-id", secureId,
                "fesrAppName", "ZCMW2102",
                "fesrUseBeacon", "true",
                "SAPEVENTQUEUE", queue
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTimetableUrl()))
                .header("Cookie", cookieHeader)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("Accept", "application/xml,text/html")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-XHR-Logon", "accept")
                .header("User-Agent", BROWSER_UA)
                .timeout(properties.getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request);
        return response.body();
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

    private HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status / 100 == 2) {
                return response;
            }
            if (status / 100 == 5) {
                String snippet = response.body() == null ? "(null)"
                        : response.body().substring(0, Math.min(300, response.body().length()));
                log.warn("saint schedule connector 5xx: status={} body='{}'", status, snippet);
                throw new ConnectorUnavailableException();
            }
            log.warn("saint schedule connector unexpected status={}", status);
            throw new ConnectorParseException();
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ConnectorTimeoutException(exception);
        } catch (IOException exception) {
            log.warn("saint schedule connector IOException", exception);
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
        if (Jsoup.parse(html).selectFirst(TIMETABLE_TABLE_SELECTOR) == null) {
            String snippet = html.substring(0, Math.min(500, html.length())).replaceAll("\\s+", " ");
            log.info("saint schedule auth gate tripped: studentFp={} htmlSnippet='{}'",
                    SaintSessionStore.fingerprint(studentId), snippet);
            throw new SaintSessionExpiredException(
                    "ecc did not return the timetable container (likely logon redirect)");
        }
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
        // Server-set cookies are allowed to update existing values so the
        // ecc session migrates onto the freshest SAP_SESSIONID. Merge order
        // is "existing first, then Set-Cookie wins on conflict" — which is
        // what put() does on a LinkedHashMap.
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

    private static HttpClient defaultHttpClient(SaintScheduleProperties properties) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(properties.getTimeout())
                .build();
    }
}
