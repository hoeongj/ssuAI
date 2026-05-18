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

        // GET returns SAP WDA bootstrap, then initial Form_Request POST
        // returns the current page (6 history rows, empty detail).
        server.enqueue(bootstrapOk("CSRF-BOOT")
                .addHeader("Set-Cookie", "SAP_SESSIONID_SSP_100=GR-SESS; Path=/")
        );
        server.enqueue(xmlOk(firstFixture));
        // 5 prev POSTs follow (history.size() - 1 = 5).
        for (int i = 0; i < 5; i++) {
            server.enqueue(xmlOk(prevFixture));
        }

        GradesResponse response = connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.history()).hasSize(6);
        assertThat(response.academicRecord().gpa()).isEqualTo(3.50d);
        assertThat(response.certificate().gpa()).isEqualTo(3.50d);
        // Five prev hops populated five history.get(1..5) keys; the
        // current-term (history[0]) detail stayed empty.
        assertThat(response.detailsByTerm()).hasSize(5);
        assertThat(server.getRequestCount()).isEqualTo(7);

        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("GET");
        assertThat(first.getHeader("Cookie")).contains("MYSAPSSO2=abc");

        RecordedRequest initPost = server.takeRequest();
        assertThat(initPost.getMethod()).isEqualTo("POST");
        assertThat(initPost.getHeader("Content-Type"))
                .startsWith("application/x-www-form-urlencoded");
        String body = initPost.getBody().readUtf8();
        assertThat(body).contains("sap-wd-secure-id=CSRF-BOOT");
        assertThat(body).contains("SAPEVENTQUEUE=");
        // mergeSetCookies must carry the Set-Cookie from the first GET
        // into the subsequent POSTs so the SAP session affinity holds.
        assertThat(initPost.getHeader("Cookie"))
                .contains("MYSAPSSO2=abc")
                .contains("SAP_SESSIONID_SSP_100=GR-SESS");

        RecordedRequest firstPrevPost = server.takeRequest();
        assertThat(firstPrevPost.getBody().readUtf8())
                .contains("sap-wd-secure-id=92AC1288589D3E4A398E724EED71D17A");
    }

    @Test
    void firstGetWithRenderedGradesDoesNotSendInitialPost() throws Exception {
        String firstFixture = loadFixture("grades-success.html");
        String prevFixture = loadFixture("grades-prev-success.html");

        server.enqueue(htmlOk(firstFixture));
        for (int i = 0; i < 5; i++) {
            server.enqueue(xmlOk(prevFixture));
        }

        GradesResponse response = connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.history()).hasSize(6);
        assertThat(response.academicRecord().gpa()).isEqualTo(3.50d);
        // GET rendered the history and current page, so only prev POSTs are sent.
        assertThat(server.getRequestCount()).isEqualTo(6);

        RecordedRequest first = server.takeRequest();
        assertThat(first.getMethod()).isEqualTo("GET");
        RecordedRequest firstPrevPost = server.takeRequest();
        assertThat(firstPrevPost.getMethod()).isEqualTo("POST");
        assertThat(firstPrevPost.getBody().readUtf8())
                .contains("sap-wd-secure-id=92AC1288589D3E4A398E724EED71D17A");
    }

    @Test
    void emptyHistoryShortCircuitsBeforeAnyPrevPost() throws Exception {
        // A response with NO 학기별 표 rows still has the tbody anchor
        // (the auth gate stays clear) but iterate has nothing to walk.
        String htmlOnly = "<TABLE><tbody id=\"WD65-contentTBody\"><tr rt=\"2\"></tr></tbody></TABLE>";
        server.enqueue(bootstrapOk("CSRF-BOOT"));
        server.enqueue(xmlOk(wrap(htmlOnly
                + "<input id=\"sap-wd-secure-id\" name=\"sap-wd-secure-id\" value=\"CSRF\"/>")));

        GradesResponse response = connector.fetchGrades("20221528",
                new PortalCookies("MYSAPSSO2=abc"));

        assertThat(response.history()).isEmpty();
        assertThat(response.detailsByTerm()).isEmpty();
        assertThat(server.getRequestCount()).isEqualTo(2);
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

    private static MockResponse bootstrapOk(String secureId) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body><form id=\"sap.client.SsrClient.form\">"
                        + "<input type=\"hidden\" name=\"sap-wd-secure-id\" value=\"" + secureId + "\"/>"
                        + "</form></body></html>");
    }

    private static MockResponse htmlOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(body);
    }

    private static MockResponse xmlOk(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(body);
    }

    private static String wrap(String html) {
        return "<updates><full-update windowid=\"sapwd_main_window\">"
                + "<content-update id=\"sapwd_main_window_root_\">"
                + "<![CDATA[" + html + "]]>"
                + "</content-update></full-update></updates>";
    }
}
