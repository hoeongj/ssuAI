package com.ssuai.domain.auth.saint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ssuai.global.exception.SaintAuthFailedException;
import com.ssuai.global.exception.SaintPortalUnavailableException;

class SaintSsoServiceTests {

    private static final String SSO_URL = "https://saint.test.local/webSSO/sso.jsp";
    private static final String PORTAL_URL = "https://saint.test.local/irj/portal";
    private static final Instant T0 = Instant.parse("2026-05-16T10:00:00Z");
    private static final MediaType TEXT_HTML_UTF8 = MediaType.parseMediaType("text/html;charset=UTF-8");

    private SaintSsoProperties properties;
    private MockRestServiceServer mockServer;
    private SaintSessionStore sessionStore;
    private SaintSsoService service;

    @BeforeEach
    void setUp() {
        properties = new SaintSsoProperties();
        properties.setSsoUrl(SSO_URL);
        properties.setPortalUrl(PORTAL_URL);
        properties.setTimeout(Duration.ofSeconds(2));

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        SaintSessionProperties sessionProps = new SaintSessionProperties();
        sessionProps.setTtl(Duration.ofMinutes(30));
        sessionProps.setEncryptionKey("");
        sessionStore = new SaintSessionStore(
                sessionProps, Clock.fixed(T0, ZoneOffset.UTC), new SecureRandom());

        service = new SaintSsoService(properties, restClient, sessionStore);
    }

    @Test
    void happyPathParsesIdentityAndForwardsPortalCookies() {
        HttpHeaders phase1Headers = new HttpHeaders();
        phase1Headers.setContentType(TEXT_HTML_UTF8);
        phase1Headers.add(HttpHeaders.SET_COOKIE,
                "MYSAPSSO2=portal-session-abc; Path=/; HttpOnly");
        phase1Headers.add(HttpHeaders.SET_COOKIE,
                "JSESSIONID=jsess-xyz; Path=/irj");

        mockServer.expect(requestTo(phase1Uri("sToken-one-shot", "20231234")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE,
                        "sToken=sToken-one-shot; sIdno=20231234"))
                .andRespond(withSuccess(loadFixture("saint/phase1-success.html"),
                        TEXT_HTML_UTF8).headers(phase1Headers));

        mockServer.expect(requestTo(PORTAL_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE,
                        "MYSAPSSO2=portal-session-abc; JSESSIONID=jsess-xyz"))
                .andRespond(withSuccess(loadFixture("saint/portal-success.html"),
                        TEXT_HTML_UTF8));

        UsaintAuthResult result = service.authenticate("sToken-one-shot", "20231234");

        assertThat(result.studentId()).isEqualTo("20999999");
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.major()).isEqualTo("컴퓨터학부");
        assertThat(result.enrollmentStatus()).isEqualTo("학사과정 재학");
        assertThat(sessionStore.cookies("20999999"))
                .as("phase 1 portal cookies should be persisted under the portal-confirmed "
                        + "studentId (sIdno can disagree with the authoritative HTML id)")
                .hasValueSatisfying(cookies -> assertThat(cookies.rawCookieHeader())
                        .isEqualTo("MYSAPSSO2=portal-session-abc; JSESSIONID=jsess-xyz"));
        mockServer.verify();
    }

    @Test
    void phase1SuccessMarkerMissingFailsAuth() {
        mockServer.expect(requestTo(phase1Uri("bad", "20231234")))
                .andRespond(withSuccess(loadFixture("saint/phase1-failure.html"),
                        TEXT_HTML_UTF8));

        assertThatThrownBy(() -> service.authenticate("bad", "20231234"))
                .isInstanceOf(SaintAuthFailedException.class)
                .hasMessageContaining("success marker");
        assertThat(sessionStore.size())
                .as("no cookies should be persisted when phase 1 fails")
                .isZero();
    }

    @Test
    void phase1ServerErrorFailsAuth() {
        mockServer.expect(requestTo(phase1Uri("token", "20231234")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.authenticate("token", "20231234"))
                .isInstanceOf(SaintAuthFailedException.class)
                .hasMessageContaining("http 500");
    }

    @Test
    void phase1WithoutSetCookieFailsAuth() {
        mockServer.expect(requestTo(phase1Uri("token", "20231234")))
                .andRespond(withSuccess(loadFixture("saint/phase1-success.html"),
                        TEXT_HTML_UTF8));

        assertThatThrownBy(() -> service.authenticate("token", "20231234"))
                .isInstanceOf(SaintAuthFailedException.class)
                .hasMessageContaining("Set-Cookie");
    }

    @Test
    void phase2PortalParseFailsOnMissingCells() {
        HttpHeaders phase1Headers = new HttpHeaders();
        phase1Headers.setContentType(TEXT_HTML_UTF8);
        phase1Headers.add(HttpHeaders.SET_COOKIE, "MYSAPSSO2=cookie; Path=/");

        mockServer.expect(requestTo(phase1Uri("token", "20231234")))
                .andRespond(withSuccess(loadFixture("saint/phase1-success.html"),
                        TEXT_HTML_UTF8).headers(phase1Headers));
        mockServer.expect(requestTo(PORTAL_URL))
                .andRespond(withSuccess(loadFixture("saint/portal-missing-cells.html"),
                        TEXT_HTML_UTF8));

        assertThatThrownBy(() -> service.authenticate("token", "20231234"))
                .isInstanceOf(SaintPortalUnavailableException.class)
                .hasMessageContaining("missing identity rows");
        assertThat(sessionStore.size())
                .as("no cookies should be persisted when portal parse fails")
                .isZero();
    }

    @Test
    void phase2PortalParseFailsWhenGreetingNameIsMissing() {
        HttpHeaders phase1Headers = new HttpHeaders();
        phase1Headers.setContentType(TEXT_HTML_UTF8);
        phase1Headers.add(HttpHeaders.SET_COOKIE, "MYSAPSSO2=cookie; Path=/");

        mockServer.expect(requestTo(phase1Uri("token", "20231234")))
                .andRespond(withSuccess(loadFixture("saint/phase1-success.html"),
                        TEXT_HTML_UTF8).headers(phase1Headers));
        mockServer.expect(requestTo(PORTAL_URL))
                .andRespond(withSuccess(loadFixture("saint/portal-missing-name.html"),
                        TEXT_HTML_UTF8));

        assertThatThrownBy(() -> service.authenticate("token", "20231234"))
                .isInstanceOf(SaintPortalUnavailableException.class)
                .hasMessageContaining("missing name element");
        assertThat(sessionStore.size())
                .as("no cookies should be persisted when the name greeting is missing")
                .isZero();
    }

    @Test
    void phase2ServerErrorMapsToPortalUnavailable() {
        HttpHeaders phase1Headers = new HttpHeaders();
        phase1Headers.setContentType(TEXT_HTML_UTF8);
        phase1Headers.add(HttpHeaders.SET_COOKIE, "MYSAPSSO2=cookie; Path=/");

        mockServer.expect(requestTo(phase1Uri("token", "20231234")))
                .andRespond(withSuccess(loadFixture("saint/phase1-success.html"),
                        TEXT_HTML_UTF8).headers(phase1Headers));
        mockServer.expect(requestTo(PORTAL_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.authenticate("token", "20231234"))
                .isInstanceOf(SaintPortalUnavailableException.class)
                .hasMessageContaining("http 502");
    }

    @Test
    void blankInputIsRejectedBeforeAnyUpstreamCall() {
        assertThatThrownBy(() -> service.authenticate("  ", "20231234"))
                .isInstanceOf(SaintAuthFailedException.class)
                .hasMessageContaining("sToken");
        assertThatThrownBy(() -> service.authenticate("token", null))
                .isInstanceOf(SaintAuthFailedException.class)
                .hasMessageContaining("sIdno");
        mockServer.verify();
    }

    private static String phase1Uri(String sToken, String sIdno) {
        return SSO_URL
                + "?sToken=" + URLEncoder.encode(sToken, StandardCharsets.UTF_8)
                + "&sIdno=" + URLEncoder.encode(sIdno, StandardCharsets.UTF_8);
    }

    private static String loadFixture(String classpath) {
        try {
            return new String(new ClassPathResource(classpath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
