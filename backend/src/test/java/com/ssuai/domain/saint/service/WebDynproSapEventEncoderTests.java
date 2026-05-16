package com.ssuai.domain.saint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebDynproSapEventEncoderTests {

    /**
     * Expected wire shape for "press the previous-year button" (WDA7) as
     * captured from a logged-in browser session against the ZCMW2102
     * timetable page (Task 16 spec §3.4). If SAP changes the gesture
     * vocabulary or the trailing empty meta group, this assertion catches
     * it as a unit failure before we hit ecc.ssu.ac.kr.
     */
    private static final String EXPECTED_WDA7_QUEUE =
            "Button_Press"
                    + "~E002Id~E004WDA7~E003"
                    + "~E002ResponseData~E004delta~E005ClientAction~E004submit~E003"
                    + "~E002~E003"
                    + "~E001"
                    + "Form_Request"
                    + "~E002Id~E004sap.client.SsrClient.form"
                    + "~E005Async~E004false"
                    + "~E005FocusInfo~E004~0040~007B~0022sFocussedId~0022~003A~0022WDA7~0022~007D"
                    + "~E005Hash~E004"
                    + "~E005DomChanged~E004false"
                    + "~E005IsDirty~E004false~E003"
                    + "~E002ResponseData~E004delta~E003"
                    + "~E002~E003";

    @Test
    void encodeButtonPressMatchesCapturedWda7Queue() {
        String queue = WebDynproSapEventEncoder.encodeButtonPress("WDA7");

        assertThat(queue).isEqualTo(EXPECTED_WDA7_QUEUE);
    }

    @Test
    void encodeButtonPressWithDifferentIdRoundTripsThroughFocusInfo() {
        String queue = WebDynproSapEventEncoder.encodeButtonPress("WDA8");

        // Different button id flows into both the Button_Press Id meta and
        // the Form_Request FocusInfo JSON literal.
        assertThat(queue).contains("Button_Press~E002Id~E004WDA8~E003");
        assertThat(queue).contains("~0022sFocussedId~0022~003A~0022WDA8~0022");
    }

    @Test
    void encodeButtonPressRejectsBlankElementId() {
        assertThatThrownBy(() -> WebDynproSapEventEncoder.encodeButtonPress(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebDynproSapEventEncoder.encodeButtonPress(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebDynproSapEventEncoder.encodeButtonPress("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapeReplacesStructuralCharactersWithSapHexForms() {
        assertThat(WebDynproSapEventEncoder.escape("\"")).isEqualTo("~0022");
        assertThat(WebDynproSapEventEncoder.escape(":")).isEqualTo("~003A");
        assertThat(WebDynproSapEventEncoder.escape("@")).isEqualTo("~0040");
        assertThat(WebDynproSapEventEncoder.escape("{")).isEqualTo("~007B");
        assertThat(WebDynproSapEventEncoder.escape("}")).isEqualTo("~007D");
    }

    @Test
    void escapeLeavesOrdinaryAsciiAlone() {
        assertThat(WebDynproSapEventEncoder.escape("WDA7")).isEqualTo("WDA7");
        assertThat(WebDynproSapEventEncoder.escape("hello world"))
                .isEqualTo("hello world");
    }

    @Test
    void escapeHandlesNullAndEmpty() {
        assertThat(WebDynproSapEventEncoder.escape(null)).isEmpty();
        assertThat(WebDynproSapEventEncoder.escape("")).isEmpty();
    }

    @Test
    void escapeAppliesToEveryOccurrenceWithinAString() {
        assertThat(WebDynproSapEventEncoder.escape("@{\"k\":\"v\"}"))
                .isEqualTo("~0040~007B~0022k~0022~003A~0022v~0022~007D");
    }
}
