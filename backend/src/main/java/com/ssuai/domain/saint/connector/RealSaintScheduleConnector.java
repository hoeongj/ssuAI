package com.ssuai.domain.saint.connector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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
 *       response is the rendered HTML for the current term and contains
 *       a fresh {@code sap-wd-secure-id} hidden input (the CSRF token
 *       the next POST must echo).</li>
 *   <li>For each prior year we still want, {@code POST ZCMW2102} with
 *       a {@code SAPEVENTQUEUE} that simulates pressing the
 *       "previous year" button ({@code WDA7}). The response is a
 *       WebDynpro {@code <updates><full-update>...CDATA...} envelope
 *       carrying the re-rendered page plus a new secure-id.</li>
 * </ol>
 *
 * <p>Cookie handling — the first GET typically picks up additional ecc
 * session cookies ({@code SAP_SESSIONID_SSP_100}, {@code sap-usercontext})
 * via {@code Set-Cookie}. We merge those into the cookie header used by
 * subsequent POSTs so SAP's session affinity holds across the iterate.
 *
 * <p>Partial-failure policy — if CSRF rotation breaks mid-iterate, we
 * stop walking back and return whatever terms we already collected
 * rather than failing the whole request. The student would rather see
 * recent terms than a blank screen while we investigate.
 */
@Component
@ConditionalOnProperty(name = "ssuai.connector.saint-schedule", havingValue = "real")
public class RealSaintScheduleConnector implements SaintScheduleConnector {

    private static final Logger log = LoggerFactory.getLogger(RealSaintScheduleConnector.class);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String PREV_YEAR_BUTTON_ID = "WDA7";
    private static final String TIMETABLE_TABLE_SELECTOR = "tbody[id$=-contentTBody]";
    private static final int MAX_PREV_YEAR_HOPS = 10;

    private final SaintScheduleProperties properties;
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
        LocalDate today = LocalDate.now(clock.withZone(KST));
        int currentYear = today.getYear();
        int currentTerm = SaintScheduleHelpers.termFor(today);

        HttpResponse<String> firstResponse = httpGet(cookies.rawCookieHeader());
        String currentHtml = firstResponse.body();
        guardAuthOrThrow(currentHtml, studentId);

        String mergedCookieHeader = mergeSetCookies(cookies.rawCookieHeader(),
                firstResponse.headers().allValues("Set-Cookie"));
        Optional<String> secureId = WebDynproResponseUnwrapper.extractSecureId(currentHtml);

        List<TermSchedule> terms = new ArrayList<>();
        List<ScheduleEntry> currentEntries = SaintScheduleParser.parse(currentHtml);
        terms.add(new TermSchedule(currentYear, currentTerm, currentEntries));

        int year = currentYear;
        int hops = 0;
        while (year > enrollmentYear && hops < MAX_PREV_YEAR_HOPS) {
            if (secureId.isEmpty()) {
                log.warn("saint schedule iterate halted: studentFp={} reason=missing-secure-id year={}",
                        SaintSessionStore.fingerprint(studentId), year);
                break;
            }
            String xmlEnvelope = httpPostButtonPress(mergedCookieHeader, secureId.get(),
                    PREV_YEAR_BUTTON_ID);
            String prevHtml = WebDynproResponseUnwrapper.extractHtml(xmlEnvelope);
            secureId = WebDynproResponseUnwrapper.extractSecureId(prevHtml);
            List<ScheduleEntry> prevEntries = SaintScheduleParser.parse(prevHtml);
            year--;
            terms.add(new TermSchedule(year, currentTerm, prevEntries));
            hops++;
        }
        log.info("saint schedule fetched: studentFp={} terms={} entries={}",
                SaintSessionStore.fingerprint(studentId), terms.size(),
                terms.stream().mapToInt(t -> t.entries().size()).sum());
        return new ScheduleResponse(enrollmentYear, currentYear, currentTerm, terms);
    }

    private HttpResponse<String> httpGet(String cookieHeader) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTimetableUrl()))
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "ko")
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
                .timeout(properties.getTimeout())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = send(request);
        return response.body();
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
                throw new ConnectorUnavailableException();
            }
            throw new ConnectorParseException();
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ConnectorTimeoutException(exception);
        } catch (IOException exception) {
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
            log.info("saint schedule auth gate tripped: studentFp={}",
                    SaintSessionStore.fingerprint(studentId));
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
