package com.ssuai.domain.saint.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Pulls the embedded page HTML out of the WebDynpro XML envelope that SAP
 * returns when a client posts a delta event (e.g. a button press on the
 * timetable nav).
 *
 * <p>Envelope shape (Task 16 spec §3.4):
 *
 * <pre>
 *   &lt;updates&gt;
 *     &lt;full-update windowid="sapwd_main_window"&gt;
 *       &lt;content-update id="sapwd_main_window_root_"&gt;
 *         &lt;![CDATA[ ... full re-rendered page HTML, including a fresh
 *                    sap-wd-secure-id hidden input ... ]]&gt;
 *       &lt;/content-update&gt;
 *     &lt;/full-update&gt;
 *   &lt;/updates&gt;
 * </pre>
 *
 * <p>We use a string-level CDATA scan rather than a full XML parser
 * because the CDATA payload is arbitrary HTML — including {@code <script>}
 * blocks and stray angle brackets — which would force a lenient parser
 * to be configured carefully just to spit the same bytes back out. The
 * scan also tolerates upstream renaming the wrapper element (e.g.
 * {@code <delta-update>} instead of {@code <full-update>}) as long as the
 * CDATA payload boundary stays intact.
 *
 * <p>{@link #extractSecureId(String)} is the second half of the
 * connector's CSRF rotation: each SAP response carries a fresh
 * {@code sap-wd-secure-id} that the next POST must echo. The selector
 * matches the same hidden input the captured cURL contained
 * (spec §3.1 step 2).
 */
public final class WebDynproResponseUnwrapper {

    private static final String CDATA_OPEN = "<![CDATA[";
    private static final String CDATA_CLOSE = "]]>";
    private static final String SECURE_ID_SELECTOR = "input[name=sap-wd-secure-id]";
    // Matches SAP XML parameter-update / input-like tags carrying secure-id,
    // tolerating extra attributes between name and value (either order).
    private static final Pattern SECURE_ID_XML = Pattern.compile(
            "<[^>]*(?:name=[\"']sap-wd-secure-id[\"'][^>]*value=[\"']([^\"']+)[\"']" +
            "|value=[\"']([^\"']+)[\"'][^>]*name=[\"']sap-wd-secure-id[\"'])[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private WebDynproResponseUnwrapper() {
    }

    /**
     * Extract the HTML payload from the first CDATA section in a
     * WebDynpro delta envelope. Returns the inner string verbatim
     * (no entity decoding — CDATA's whole point is to skip XML escaping).
     *
     * @throws IllegalArgumentException if the input is blank or contains
     *                                  no CDATA section
     */
    public static String extractHtml(String wrappedXml) {
        if (wrappedXml == null || wrappedXml.isBlank()) {
            throw new IllegalArgumentException("wrappedXml is required");
        }
        int open = wrappedXml.indexOf(CDATA_OPEN);
        if (open < 0) {
            throw new IllegalArgumentException("wrappedXml has no CDATA section");
        }
        int payloadStart = open + CDATA_OPEN.length();
        int close = wrappedXml.indexOf(CDATA_CLOSE, payloadStart);
        if (close < 0) {
            throw new IllegalArgumentException("wrappedXml CDATA section is not closed");
        }
        return wrappedXml.substring(payloadStart, close);
    }

    /**
     * Reach into the page HTML and pull the value of the
     * {@code sap-wd-secure-id} hidden input. Returns empty when the input
     * is absent (e.g. read-only page) — the caller decides whether that
     * is fatal for the next POST.
     */
    public static Optional<String> extractSecureId(String html) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(html, "", Parser.htmlParser());
        Element input = document.selectFirst(SECURE_ID_SELECTOR);
        if (input == null) {
            return Optional.empty();
        }
        String value = input.attr("value");
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Like {@link #extractSecureId} but also handles SAP WDA XML responses
     * where the secure-id appears as a {@code parameter-update} XML attribute
     * rather than an HTML hidden input. Used when the first GET might return
     * an XML wrapper (grades) instead of plain HTML (schedule).
     */
    public static Optional<String> extractSecureIdFromAny(String content) {
        Optional<String> fromHtml = extractSecureId(content);
        if (fromHtml.isPresent()) {
            return fromHtml;
        }
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SECURE_ID_XML.matcher(content);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            if (val != null && !val.isBlank()) {
                return Optional.of(val);
            }
        }
        return Optional.empty();
    }
}
