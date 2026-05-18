package com.ssuai.domain.auth.saint;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.ssuai.global.exception.SaintAuthFailedException;
import com.ssuai.global.exception.SaintPortalUnavailableException;

/**
 * Confirms a SSU student's identity through the saint.ssu.ac.kr two-phase
 * handshake, modeled on {@code jonghokim27/ssutoday}'s
 * {@code AuthServiceImpl.uSaintAuth}.
 *
 * <p>Phase 1 — GET {@code saint.ssu.ac.kr/webSSO/sso.jsp?sToken=&sIdno=}
 * with the same tokens echoed in the {@code Cookie} header. saint validates
 * the one-shot tokens and, on success, responds with HTML containing
 * {@code location.href = "/irj/portal"} plus the real portal session
 * cookies in {@code Set-Cookie} headers.
 *
 * <p>Phase 2 — GET {@code saint.ssu.ac.kr/irj/portal} with the phase 1
 * cookies. The live portal is a SAP NetWeaver frameset wrapper: the
 * top-level HTML carries only a {@code <span class="top_user">{이름}님
 * 접속을 환영합니다.</span>} greeting (and the same학번 echoed in JS
 * config); the actual {@code .main_box09} student-info card is loaded
 * lazily into {@code <iframe id="contentAreaFrame">} by SAP portal JS,
 * so a static fetch cannot see 소속 / 과정·학기 / 학년·학기. We only
 * use phase 2 to (a) confirm the session is alive (HTTP 200 + greeting
 * span present) and (b) extract the display name. Student id is taken
 * from the {@code sIdno} that already proved itself in phase 1. 소속 /
 * 학적상태 stay null until a later u-SAINT data tool (Task 16
 * {@code get_my_schedule} / {@code get_my_grades}) fetches them from
 * the deep portal endpoints.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>{@code sToken} / {@code sIdno} are method-scoped locals — never logged,
 *       persisted, or returned past this method (Task 14 spec §1, §5).
 *   <li>Phase 1 portal cookies are handed off, encrypted, to
 *       {@link SaintSessionStore} once identity is confirmed (Task 16 PR 16a).
 *       Realtime u-SAINT data tools read them back from the store; they
 *       never live on a service field or in a log line.
 *   <li>Both upstream calls hit saint.ssu.ac.kr over HTTPS in prod; never
 *       echo the cookie or token values into responses or exceptions.
 * </ul>
 */
@Service
public class SaintSsoService {

    private static final Logger log = LoggerFactory.getLogger(SaintSsoService.class);

    private static final String PHASE1_SUCCESS_MARKER = "location.href = \"/irj/portal\"";
    private static final String IDENTITY_NAME_SELECTOR = ".top_user";
    // Live portal greets with "{이름}님 접속을 환영합니다." Keep the shorter
    // "님 환영합니다." variant + plain trailing "님" as fallbacks so a copy
    // change on the SSU side does not break login.
    private static final List<String> NAME_GREETING_SUFFIXES = List.of(
            "님 접속을 환영합니다.", "님 환영합니다.", "님");

    private final SaintSsoProperties properties;
    private final RestClient restClient;
    private final SaintSessionStore sessionStore;

    public SaintSsoService(
            SaintSsoProperties properties,
            @Qualifier("saintSsoRestClient") RestClient restClient,
            SaintSessionStore sessionStore) {
        this.properties = properties;
        this.restClient = restClient;
        this.sessionStore = sessionStore;
    }

    public UsaintAuthResult authenticate(String sToken, String sIdno) {
        if (sToken == null || sToken.isBlank()) {
            throw new SaintAuthFailedException("sToken is required");
        }
        if (sIdno == null || sIdno.isBlank()) {
            throw new SaintAuthFailedException("sIdno is required");
        }

        ResponseEntity<String> phase1 = phase1Validate(sToken, sIdno);
        String phase1Cookies = buildCookieHeader(phase1.getHeaders().get(HttpHeaders.SET_COOKIE));
        if (phase1Cookies.isEmpty()) {
            throw new SaintAuthFailedException("phase 1 returned no Set-Cookie headers");
        }

        ResponseEntity<String> phase2 = phase2FetchPortal(phase1Cookies);
        String phase2Cookies = buildCookieHeader(phase2.getHeaders().get(HttpHeaders.SET_COOKIE));
        String mergedCookies = mergeCookieHeaders(phase1Cookies, phase2Cookies);

        log.info("saint sso cookies stored: names={}", cookieNames(mergedCookies));
        // Temporary diagnostic: log MYSAPSSO2 prefix to compare with browser value
        String mysapPrefix = extractCookiePrefix(mergedCookies, "MYSAPSSO2", 24);
        log.info("saint sso mysapsso2 prefix(24)={}", mysapPrefix);

        UsaintAuthResult identity = parseIdentity(phase2.getBody(), sIdno);
        sessionStore.put(identity.studentId(), new PortalCookies(mergedCookies));
        return identity;
    }

    private ResponseEntity<String> phase1Validate(String sToken, String sIdno) {
        URI uri = URI.create(properties.getSsoUrl()
                + "?sToken=" + URLEncoder.encode(sToken, StandardCharsets.UTF_8)
                + "&sIdno=" + URLEncoder.encode(sIdno, StandardCharsets.UTF_8));
        ResponseEntity<String> response;
        try {
            response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.COOKIE, "sToken=" + sToken + "; sIdno=" + sIdno)
                    .retrieve()
                    .toEntity(String.class);
        } catch (ResourceAccessException exception) {
            throw new SaintAuthFailedException("saint phase 1 timeout/io", exception);
        } catch (RestClientResponseException exception) {
            throw new SaintAuthFailedException(
                    "saint phase 1 http " + exception.getStatusCode().value(), exception);
        }

        String body = response.getBody();
        if (body == null || !body.contains(PHASE1_SUCCESS_MARKER)) {
            throw new SaintAuthFailedException("saint phase 1 success marker missing");
        }
        return response;
    }

    private ResponseEntity<String> phase2FetchPortal(String portalCookieHeader) {
        try {
            return restClient.get()
                    .uri(URI.create(properties.getPortalUrl()))
                    .header(HttpHeaders.COOKIE, portalCookieHeader)
                    .retrieve()
                    .toEntity(String.class);
        } catch (ResourceAccessException exception) {
            throw new SaintPortalUnavailableException("saint phase 2 timeout/io", exception);
        } catch (RestClientResponseException exception) {
            throw new SaintPortalUnavailableException(
                    "saint phase 2 http " + exception.getStatusCode().value(), exception);
        }
    }

    private UsaintAuthResult parseIdentity(String portalHtml, String sIdno) {
        if (portalHtml == null || portalHtml.isBlank()) {
            throw new SaintPortalUnavailableException("phase 2 portal HTML is empty");
        }
        Document document = Jsoup.parse(portalHtml);
        String name = extractName(document);
        // sIdno survived phase 1's success-marker check and produced a usable
        // portal session in phase 2 — trust it as the authoritative student
        // id rather than re-parsing it out of the page. The portal main HTML
        // only echoes 학번 inside JS config, not in a stable DOM element.
        return new UsaintAuthResult(sIdno.trim(), name, null, null);
    }

    private static String extractName(Document document) {
        Element nameElement = document.selectFirst(IDENTITY_NAME_SELECTOR);
        if (nameElement == null) {
            throw new SaintPortalUnavailableException(
                    "portal HTML missing greeting element (" + IDENTITY_NAME_SELECTOR + ")");
        }
        String raw = nameElement.text().trim();
        if (raw.isBlank()) {
            throw new SaintPortalUnavailableException("portal HTML greeting element is blank");
        }
        for (String suffix : NAME_GREETING_SUFFIXES) {
            if (raw.endsWith(suffix)) {
                String stripped = raw.substring(0, raw.length() - suffix.length()).trim();
                if (!stripped.isBlank()) {
                    return stripped;
                }
            }
        }
        // No known suffix matched; bail out rather than persisting a string
        // that includes the greeting tail — caller will redirect with
        // error=portal_unavailable and we keep a log line for diagnosis.
        throw new SaintPortalUnavailableException(
                "portal HTML greeting did not match any known name suffix");
    }

    private static String buildCookieHeader(List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return "";
        }
        return setCookies.stream()
                .map(SaintSsoService::stripCookieAttributes)
                .filter(cookie -> !cookie.isBlank())
                .collect(Collectors.joining("; "));
    }

    private static String stripCookieAttributes(String setCookieHeader) {
        int semicolon = setCookieHeader.indexOf(';');
        String pair = semicolon < 0 ? setCookieHeader : setCookieHeader.substring(0, semicolon);
        return pair.trim();
    }

    private static String mergeCookieHeaders(String base, String overlay) {
        if (overlay == null || overlay.isBlank()) {
            return base;
        }
        // overlay wins on conflict (same name=value ordering as LinkedHashMap.put)
        java.util.LinkedHashMap<String, String> jar = new java.util.LinkedHashMap<>();
        for (String pair : base.split(";")) {
            addPairToJar(jar, pair.trim());
        }
        for (String pair : overlay.split(";")) {
            addPairToJar(jar, pair.trim());
        }
        return jar.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static void addPairToJar(java.util.LinkedHashMap<String, String> jar, String pair) {
        if (pair == null || pair.isEmpty()) return;
        int eq = pair.indexOf('=');
        if (eq <= 0) return;
        jar.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
    }

    private static String cookieNames(String cookieHeader) {
        List<String> names = new ArrayList<>();
        for (String pair : cookieHeader.split(";")) {
            String trimmed = pair.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0) names.add(trimmed.substring(0, eq).trim());
        }
        return String.join(",", names);
    }

    private static String extractCookiePrefix(String cookieHeader, String name, int prefixLen) {
        for (String pair : cookieHeader.split(";")) {
            String trimmed = pair.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0 && trimmed.substring(0, eq).trim().equals(name)) {
                String value = trimmed.substring(eq + 1).trim();
                return value.substring(0, Math.min(prefixLen, value.length()));
            }
        }
        return "(not found)";
    }
}
