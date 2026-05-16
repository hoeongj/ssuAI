package com.ssuai.domain.auth.saint;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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
 * cookies. saint returns the dashboard HTML; Jsoup pulls the student name
 * out of the {@code .main_box09 .box_top .main_title} greeting and reads
 * the {@code <ul class="main_box09_con"> <li><dl><dt>키</dt><dd>값</dd></dl></li> }
 * student-info card to recover 학번 / 소속 / 과정/학기 by key (not by
 * positional index — order changes in the live portal would silently mis-
 * assign fields otherwise). Task 14 §risks documented that ssutoday's
 * old positional {@code main_box09_con} parse no longer matches the
 * current portal; that fixture has been replaced.
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

    private static final String PHASE1_SUCCESS_MARKER = "location.href = \"/irj/portal\"";
    private static final String IDENTITY_NAME_SELECTOR = ".main_box09 .box_top .main_title span";
    private static final String IDENTITY_ROW_SELECTOR = ".main_box09 ul.main_box09_con li dl";
    private static final String IDENTITY_KEY_SELECTOR = "dt";
    private static final String IDENTITY_VALUE_SELECTOR = "dd";
    private static final String NAME_GREETING_SUFFIX = "님 환영합니다.";
    private static final String NAME_SUFFIX_TITLE = "님";
    private static final String FIELD_KEY_STUDENT_ID = "학번";
    private static final String FIELD_KEY_MAJOR = "소속";
    private static final String FIELD_KEY_ENROLLMENT_STATUS = "과정/학기";

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
        String portalCookieHeader = buildCookieHeader(phase1.getHeaders().get(HttpHeaders.SET_COOKIE));
        if (portalCookieHeader.isEmpty()) {
            throw new SaintAuthFailedException("phase 1 returned no Set-Cookie headers");
        }

        String portalHtml = phase2FetchPortal(portalCookieHeader);
        UsaintAuthResult identity = parseIdentity(portalHtml);
        sessionStore.put(identity.studentId(), new PortalCookies(portalCookieHeader));
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

    private String phase2FetchPortal(String portalCookieHeader) {
        try {
            return restClient.get()
                    .uri(URI.create(properties.getPortalUrl()))
                    .header(HttpHeaders.COOKIE, portalCookieHeader)
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException exception) {
            throw new SaintPortalUnavailableException("saint phase 2 timeout/io", exception);
        } catch (RestClientResponseException exception) {
            throw new SaintPortalUnavailableException(
                    "saint phase 2 http " + exception.getStatusCode().value(), exception);
        }
    }

    private UsaintAuthResult parseIdentity(String portalHtml) {
        if (portalHtml == null || portalHtml.isBlank()) {
            throw new SaintPortalUnavailableException("portal HTML is empty");
        }
        Document document = Jsoup.parse(portalHtml);

        String name = extractName(document);
        Map<String, String> fields = extractIdentityFields(document);

        String studentId = fields.getOrDefault(FIELD_KEY_STUDENT_ID, "").trim();
        String major = fields.getOrDefault(FIELD_KEY_MAJOR, "").trim();
        String enrollmentStatus = fields.getOrDefault(FIELD_KEY_ENROLLMENT_STATUS, "").trim();

        if (studentId.isBlank()) {
            throw new SaintPortalUnavailableException(
                    "portal HTML missing 학번 row in main_box09_con");
        }
        return new UsaintAuthResult(
                studentId,
                name,
                major.isBlank() ? null : major,
                enrollmentStatus.isBlank() ? null : enrollmentStatus);
    }

    private static String extractName(Document document) {
        Element nameElement = document.selectFirst(IDENTITY_NAME_SELECTOR);
        if (nameElement == null) {
            throw new SaintPortalUnavailableException(
                    "portal HTML missing name element (" + IDENTITY_NAME_SELECTOR + ")");
        }
        String raw = nameElement.text().trim();
        if (raw.isBlank()) {
            throw new SaintPortalUnavailableException("portal HTML name element is blank");
        }
        // Greeting is "{이름}님 환영합니다." in the live portal; strip the
        // suffix so we persist just the name. Fall back to plain "님"
        // trimming in case the trailing sentence ever drops.
        String stripped = raw;
        if (stripped.endsWith(NAME_GREETING_SUFFIX)) {
            stripped = stripped.substring(0, stripped.length() - NAME_GREETING_SUFFIX.length());
        } else if (stripped.endsWith(NAME_SUFFIX_TITLE)) {
            stripped = stripped.substring(0, stripped.length() - NAME_SUFFIX_TITLE.length());
        }
        stripped = stripped.trim();
        if (stripped.isBlank()) {
            throw new SaintPortalUnavailableException("portal HTML name resolved to blank");
        }
        return stripped;
    }

    private static Map<String, String> extractIdentityFields(Document document) {
        Elements rows = document.select(IDENTITY_ROW_SELECTOR);
        if (rows.isEmpty()) {
            throw new SaintPortalUnavailableException(
                    "portal HTML missing identity rows (" + IDENTITY_ROW_SELECTOR + ")");
        }
        Map<String, String> fields = new HashMap<>();
        for (Element row : rows) {
            Element key = row.selectFirst(IDENTITY_KEY_SELECTOR);
            Element value = row.selectFirst(IDENTITY_VALUE_SELECTOR);
            if (key == null || value == null) {
                continue;
            }
            String keyText = key.text().trim();
            String valueText = value.text().trim();
            if (!keyText.isEmpty()) {
                fields.put(keyText, valueText);
            }
        }
        return fields;
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
}
