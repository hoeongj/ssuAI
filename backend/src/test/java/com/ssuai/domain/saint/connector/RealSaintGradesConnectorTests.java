package com.ssuai.domain.saint.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.saint.config.SaintGradesProperties;
import com.ssuai.domain.saint.dto.GradesResponse;
import com.ssuai.global.exception.SaintSessionExpiredException;

class RealSaintGradesConnectorTests {

    private MockWebServer server;
    private RealSaintGradesConnector connector;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        SaintGradesProperties properties = new SaintGradesProperties();
        properties.setGradesUrl(server.url("/zcmb3w0017").toString());
        properties.setTimeout(Duration.ofSeconds(5));
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        connector = new RealSaintGradesConnector(properties, httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void firstGetParsesHistorySummariesAndIteratesPrevButtonForEachPriorTerm() throws Exception {
        String firstFixture = loadFixture("grades-success.html");
        String prevFixture = loadFixture("grades-prev-success.html");

        // First GET — current page (6 history rows, empty detail).
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .addHeader("Set-Cookie", "SAP_SESSIONID_SSP_100=GR-SESS; Path=/")
                .setBody(firstFixture));
        // 5 prev POSTs follow (history.size() - 1 = 5).
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/xml; charset=utf-8")
                    .setBody(prevFixture));
        }

        GradesResponse response = connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.history()).hasSize(6);
        assertThat(response.academicRecord().gpa()).isEqualTo(3.50d);
        assertThat(response.certificate().gpa()).isEqualTo(3.50d);
        // Five prev hops populated five history.get(1..5) keys; the
        // current-term (history[0]) detail stayed empty.
        assertThat(response.detailsByTerm()).hasSize(5);
        assertThat(server.getRequestCount()).isEqualTo(6);

        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getHeader("Cookie")).contains("MYSAPSSO2=abc");

        RecordedRequest firstPost = server.takeRequest();
        assertThat(firstPost.getMethod()).isEqualTo("POST");
        assertThat(firstPost.getHeader("Content-Type"))
                .startsWith("application/x-www-form-urlencoded");
        String body = firstPost.getBody().readUtf8();
        assertThat(body).contains("sap-wd-secure-id=");
        assertThat(body).contains("SAPEVENTQUEUE=");
        // mergeSetCookies must carry the Set-Cookie from the first GET
        // into the subsequent POSTs so the SAP session affinity holds.
        assertThat(firstPost.getHeader("Cookie"))
                .contains("MYSAPSSO2=abc")
                .contains("SAP_SESSIONID_SSP_100=GR-SESS");
    }

    @Test
    void emptyHistoryShortCircuitsBeforeAnyPrevPost() throws Exception {
        // A response with NO 학기별 표 rows still has the tbody anchor
        // (the auth gate stays clear) but iterate has nothing to walk.
        String htmlOnly = "<TABLE><tbody id=\"WD65-contentTBody\"><tr rt=\"2\"></tr></tbody></TABLE>";
        String wrapped = "<updates><full-update windowid=\"sapwd_main_window\">"
                + "<content-update id=\"sapwd_main_window_root_\">"
                + "<![CDATA[" + htmlOnly + "<input id=\"sap-wd-secure-id\" value=\"CSRF\"/>]]>"
                + "</content-update></full-update></updates>";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(wrapped));

        GradesResponse response = connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.history()).isEmpty();
        assertThat(response.detailsByTerm()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void loginPageInsteadOfGradesTriggersSaintSessionExpired() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body><form action=\"/logon\">login required</form></body></html>"));

        assertThatThrownBy(() -> connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=expired")))
                .isInstanceOf(SaintSessionExpiredException.class);
    }

    private static String loadFixture(String name) throws IOException {
        return Files.readString(
                Path.of("src", "test", "resources", "saint", name),
                StandardCharsets.UTF_8);
    }
}
