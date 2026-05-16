package com.ssuai.domain.saint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class WebDynproResponseUnwrapperTests {

    @Test
    void extractHtmlPullsCdataContentVerbatim() {
        String envelope = """
                <updates>
                  <full-update windowid="sapwd_main_window">
                    <content-update id="sapwd_main_window_root_">
                      <![CDATA[<html><body><div>hello</div></body></html>]]>
                    </content-update>
                  </full-update>
                </updates>
                """;

        assertThat(WebDynproResponseUnwrapper.extractHtml(envelope))
                .isEqualTo("<html><body><div>hello</div></body></html>");
    }

    @Test
    void extractHtmlReturnsOnlyFirstCdataSection() {
        String envelope = "<updates>"
                + "<full-update><content-update><![CDATA[<div>A</div>]]></content-update></full-update>"
                + "<full-update><content-update><![CDATA[<div>B</div>]]></content-update></full-update>"
                + "</updates>";

        assertThat(WebDynproResponseUnwrapper.extractHtml(envelope))
                .isEqualTo("<div>A</div>");
    }

    @Test
    void extractHtmlPreservesScriptAndAngleBracketsInsideCdata() {
        // CDATA's whole point: nested <script>, &amp;, and unbalanced
        // angle brackets in the HTML payload must not be touched.
        String html = "<script>if (a<b) { x = '&'; }</script><table><tr><td>cell</td></tr>";
        String envelope = "<updates><full-update><content-update><![CDATA[" + html
                + "]]></content-update></full-update></updates>";

        assertThat(WebDynproResponseUnwrapper.extractHtml(envelope)).isEqualTo(html);
    }

    @Test
    void extractHtmlRejectsBlankInput() {
        assertThatThrownBy(() -> WebDynproResponseUnwrapper.extractHtml(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebDynproResponseUnwrapper.extractHtml(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebDynproResponseUnwrapper.extractHtml("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extractHtmlRejectsEnvelopeWithoutCdata() {
        assertThatThrownBy(() -> WebDynproResponseUnwrapper.extractHtml(
                "<updates><full-update><content-update>plain</content-update></full-update></updates>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CDATA");
    }

    @Test
    void extractHtmlRejectsEnvelopeWithUnclosedCdata() {
        assertThatThrownBy(() -> WebDynproResponseUnwrapper.extractHtml(
                "<updates><![CDATA[<div>oops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not closed");
    }

    @Test
    void extractSecureIdReadsHiddenInputValue() {
        String html = """
                <html><body>
                <form>
                  <input type="hidden" name="sap-wd-secure-id" value="871202BF3389E157FBEB10D187CEB48A"/>
                </form>
                </body></html>
                """;

        assertThat(WebDynproResponseUnwrapper.extractSecureId(html))
                .contains("871202BF3389E157FBEB10D187CEB48A");
    }

    @Test
    void extractSecureIdReturnsEmptyWhenInputAbsent() {
        String html = "<html><body><div>no form</div></body></html>";

        assertThat(WebDynproResponseUnwrapper.extractSecureId(html)).isEmpty();
    }

    @Test
    void extractSecureIdReturnsEmptyWhenInputValueIsBlank() {
        String html = "<input name=\"sap-wd-secure-id\" value=\"\"/>";

        assertThat(WebDynproResponseUnwrapper.extractSecureId(html)).isEmpty();
    }

    @Test
    void extractSecureIdHandlesNullAndBlank() {
        assertThat(WebDynproResponseUnwrapper.extractSecureId(null)).isEmpty();
        assertThat(WebDynproResponseUnwrapper.extractSecureId("")).isEmpty();
        assertThat(WebDynproResponseUnwrapper.extractSecureId("   ")).isEmpty();
    }

    @Test
    void unwrapperHelpersComposeForRoundTrip() {
        String html = "<form><input name=\"sap-wd-secure-id\" value=\"FRESH-CSRF-XYZ\"/></form>";
        String envelope = "<updates><full-update><content-update><![CDATA[" + html
                + "]]></content-update></full-update></updates>";

        String extracted = WebDynproResponseUnwrapper.extractHtml(envelope);
        Optional<String> secureId = WebDynproResponseUnwrapper.extractSecureId(extracted);

        assertThat(secureId).contains("FRESH-CSRF-XYZ");
    }
}
