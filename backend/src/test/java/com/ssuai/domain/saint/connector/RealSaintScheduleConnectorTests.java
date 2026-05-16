package com.ssuai.domain.saint.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.saint.config.SaintScheduleProperties;
import com.ssuai.domain.saint.dto.ScheduleResponse;
import com.ssuai.global.exception.SaintSessionExpiredException;

class RealSaintScheduleConnectorTests {

    private static final Clock CLOCK_2026_05_16 = Clock.fixed(
            Instant.parse("2026-05-16T03:00:00Z"), ZoneOffset.UTC);

    private MockWebServer server;
    private RealSaintScheduleConnector connector;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        SaintScheduleProperties properties = new SaintScheduleProperties();
        properties.setTimetableUrl(server.url("/zcmw2102").toString());
        properties.setTimeout(Duration.ofSeconds(5));
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connector = new RealSaintScheduleConnector(properties, CLOCK_2026_05_16, httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void singleTermStudentDoesOneGetAndZeroPosts() throws Exception {
        String fixture = loadFixture();
        server.enqueue(htmlOk(fixture));

        ScheduleResponse response = connector.fetchSchedule("20261234",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.enrollmentYear()).isEqualTo(2026);
        assertThat(response.currentYear()).isEqualTo(2026);
        assertThat(response.terms()).hasSize(1);
        assertThat(response.terms().get(0).entries()).hasSize(7);
        assertThat(server.getRequestCount()).isEqualTo(1);

        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getHeader("Cookie")).contains("MYSAPSSO2=abc");
    }

    @Test
    void multiYearIterateSendsButtonPressPostsAndMergesCookies() throws Exception {
        String fixtureWithSecureId = withSecureId(loadFixture(), "FRESH-CSRF-1");
        String wrapperWithNextCsrf = wrap(withSecureId(loadFixture(), "FRESH-CSRF-2"));
        String wrapperFinal = wrap(loadFixture());

        // First GET sets an extra ecc session cookie via Set-Cookie.
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .addHeader("Set-Cookie", "SAP_SESSIONID_SSP_100=ABCD; Path=/")
                .setBody(fixtureWithSecureId));
        // POST 1 → still has a fresh secure-id
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(wrapperWithNextCsrf));
        // POST 2 → final response (no secure-id is fine: iterate completes naturally)
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(wrapperFinal));

        ScheduleResponse response = connector.fetchSchedule("20241234",
                new PortalCookies("MYSAPSSO2=abc"));

        // 2026 (current) + 2025 + 2024 = 3 terms
        assertThat(response.terms()).hasSize(3);
        assertThat(response.terms().get(0).year()).isEqualTo(2026);
        assertThat(response.terms().get(2).year()).isEqualTo(2024);
        assertThat(server.getRequestCount()).isEqualTo(3);

        server.takeRequest(); // skip the initial GET
        RecordedRequest firstPost = server.takeRequest();
        assertThat(firstPost.getMethod()).isEqualTo("POST");
        assertThat(firstPost.getHeader("Content-Type"))
                .startsWith("application/x-www-form-urlencoded");
        String firstPostBody = firstPost.getBody().readUtf8();
        assertThat(firstPostBody).contains("sap-wd-secure-id=FRESH-CSRF-1");
        assertThat(firstPostBody).contains("SAPEVENTQUEUE=");
        // mergeSetCookies must carry the upstream Set-Cookie through to the POST.
        assertThat(firstPost.getHeader("Cookie"))
                .contains("MYSAPSSO2=abc")
                .contains("SAP_SESSIONID_SSP_100=ABCD");

        RecordedRequest secondPost = server.takeRequest();
        assertThat(secondPost.getMethod()).isEqualTo("POST");
        assertThat(secondPost.getBody().readUtf8()).contains("sap-wd-secure-id=FRESH-CSRF-2");
    }

    @Test
    void iterateStopsWhenSecureIdGoesMissingMidWalk() throws Exception {
        // GET returns the timetable but with NO secure-id input — iterate
        // refuses to POST without a CSRF token and returns only the current term.
        server.enqueue(htmlOk(loadFixture()));

        ScheduleResponse response = connector.fetchSchedule("20221234",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.terms()).hasSize(1);
        assertThat(response.terms().get(0).year()).isEqualTo(2026);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void loginPageInsteadOfTimetableTriggersSaintSessionExpired() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body><form action=\"/logon\">login required</form></body></html>"));

        assertThatThrownBy(() -> connector.fetchSchedule("20221234",
                new PortalCookies("MYSAPSSO2=expired")))
                .isInstanceOf(SaintSessionExpiredException.class);
    }

    @Test
    void mergeSetCookiesAddsNewNamesAndOverwritesExistingOnConflict() {
        String merged = RealSaintScheduleConnector.mergeSetCookies(
                "MYSAPSSO2=keep; saplb_*=old-value",
                List.of("SAP_SESSIONID_SSP_100=NEW; Path=/", "saplb_*=fresh; HttpOnly"));

        assertThat(merged).contains("MYSAPSSO2=keep");
        assertThat(merged).contains("SAP_SESSIONID_SSP_100=NEW");
        // saplb_* was set by both — Set-Cookie wins (LinkedHashMap put overwrites value).
        assertThat(merged).contains("saplb_*=fresh");
        assertThat(merged).doesNotContain("saplb_*=old-value");
    }

    @Test
    void mergeSetCookiesHandlesNullAndEmptyInputs() {
        assertThat(RealSaintScheduleConnector.mergeSetCookies("", null)).isEmpty();
        assertThat(RealSaintScheduleConnector.mergeSetCookies(null, List.of()))
                .isEmpty();
        assertThat(RealSaintScheduleConnector.mergeSetCookies("a=1", null))
                .isEqualTo("a=1");
    }

    private static String loadFixture() throws IOException {
        return Files.readString(
                Path.of("src", "test", "resources", "saint", "timetable-success.html"),
                StandardCharsets.UTF_8);
    }

    private static String withSecureId(String html, String value) {
        // Inject a hidden secure-id input next to the table so the unwrapper
        // can find it. Any location inside the document is fine.
        String injection = "<input type=\"hidden\" name=\"sap-wd-secure-id\" value=\""
                + value + "\"/>";
        return html.replace("<body>", "<body>" + injection);
    }

    private static String wrap(String html) {
        return "<updates><full-update windowid=\"sapwd_main_window\">"
                + "<content-update id=\"sapwd_main_window_root_\">"
                + "<![CDATA[" + html + "]]>"
                + "</content-update></full-update></updates>";
    }

    private static MockResponse htmlOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(body);
    }
}
