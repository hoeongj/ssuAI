package com.ssuai.domain.saint.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes the {@code SAPEVENTQUEUE} form parameter SAP WebDynpro clients
 * post when they react to a user gesture (button press, focus change,
 * dropdown selection). The wire format is a fixed-token state machine that
 * SAP NetWeaver's WebDynpro runtime parses on the server side.
 *
 * <p>Token layout (Task 16 spec §3.4):
 *
 * <pre>
 *   ~E001   between events (one POST can carry many)
 *   ~E002   open one meta group
 *   ~E003   close one meta group
 *   ~E004   key=value separator inside a meta group
 *   ~E005   param=param separator inside a meta group
 * </pre>
 *
 * <p>A meta-group {@code key} or {@code value} that itself contains a
 * structural character (e.g. JSON's {@code "}, {@code :}, {@code @},
 * {@code {}}, {@code }}) is escaped to the SAP {@code ~XXXX} hex form
 * before being placed into the queue. See {@link #escape(String)}.
 *
 * <p>This encoder only models the subset needed by Task 16 — a single
 * "previous-year button press" gesture on the timetable page. SAP's full
 * WebDynpro event vocabulary is much larger; we intentionally do not
 * generalize past the captured event shape (yagni — every additional
 * event type is a new server contract we'd need to spike).
 */
public final class WebDynproSapEventEncoder {

    private static final String EVENT_SEPARATOR = "~E001";
    private static final String META_OPEN = "~E002";
    private static final String META_CLOSE = "~E003";
    private static final String KV_SEPARATOR = "~E004";
    private static final String PARAM_SEPARATOR = "~E005";

    private WebDynproSapEventEncoder() {
    }

    /**
     * Convenience for the only gesture Task 16 PR 16b emits: press a
     * button identified by its WebDynpro {@code Id} (e.g. {@code WDA7}
     * for "previous year"). Mirrors the captured cURL exactly:
     *
     * <pre>
     *   Button_Press~E002Id~E004WDA7~E003~E002ResponseData~E004delta~E005ClientAction~E004submit~E003~E002~E003
     *   ~E001
     *   Form_Request~E002Id~E004sap.client.SsrClient.form~E005Async~E004false~E005FocusInfo~E004@{"sFocussedId":"WDA7"}~E005Hash~E004~E005DomChanged~E004false~E005IsDirty~E004false~E003~E002ResponseData~E004delta~E003~E002~E003
     * </pre>
     *
     * <p>The trailing empty {@code ~E002~E003} pair after each event's
     * non-empty meta group is what SAP's server parser expects as the
     * "no further meta groups" marker; emitting it is not optional.
     */
    public static String encodeButtonPress(String elementId) {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("elementId is required");
        }
        SapEvent buttonPress = SapEvent.builder("Button_Press")
                .meta(meta -> meta.put("Id", elementId))
                .meta(meta -> {
                    meta.put("ResponseData", "delta");
                    meta.put("ClientAction", "submit");
                })
                .build();
        SapEvent formRequest = SapEvent.builder("Form_Request")
                .meta(meta -> {
                    meta.put("Id", "sap.client.SsrClient.form");
                    meta.put("Async", "false");
                    // SAP serializes FocusInfo as the literal text @{...JSON...}
                    // and the encoder escapes the @, {, }, ", : characters into
                    // ~0040 / ~007B / ~007D / ~0022 / ~003A respectively.
                    meta.put("FocusInfo", "@{\"sFocussedId\":\"" + elementId + "\"}");
                    meta.put("Hash", "");
                    meta.put("DomChanged", "false");
                    meta.put("IsDirty", "false");
                })
                .meta(meta -> meta.put("ResponseData", "delta"))
                .build();
        return encode(List.of(buttonPress, formRequest));
    }

    public static String encode(List<SapEvent> events) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("events is required");
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) {
                out.append(EVENT_SEPARATOR);
            }
            appendEvent(out, events.get(i));
        }
        return out.toString();
    }

    private static void appendEvent(StringBuilder out, SapEvent event) {
        out.append(event.name());
        for (Map<String, String> group : event.metaGroups()) {
            out.append(META_OPEN);
            int written = 0;
            for (Map.Entry<String, String> kv : group.entrySet()) {
                if (written > 0) {
                    out.append(PARAM_SEPARATOR);
                }
                out.append(escape(kv.getKey()))
                        .append(KV_SEPARATOR)
                        .append(escape(kv.getValue()));
                written++;
            }
            out.append(META_CLOSE);
        }
        // SAP's parser expects an empty trailing meta group as the
        // end-of-event marker after the last non-empty group.
        out.append(META_OPEN).append(META_CLOSE);
    }

    /**
     * Escape structural characters into SAP's {@code ~XXXX} hex form so
     * they can't collide with the event-queue tokens or with the
     * {@code @{...}} JSON-literal sentinel SAP uses for nested values.
     *
     * <p>The captured cURL escapes {@code "} {@code :} {@code @} {@code {}
     * {@code }} (used inside {@code FocusInfo}). We use the same minimal
     * set; widening the table preemptively risks breaking valid input
     * (e.g. a literal {@code ~} in a value would need its own escape).
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("~0022");
                case ':' -> out.append("~003A");
                case '@' -> out.append("~0040");
                case '{' -> out.append("~007B");
                case '}' -> out.append("~007D");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * One SAP WebDynpro event = a name plus an ordered list of meta
     * groups (each group is an ordered map of key-value pairs).
     */
    public record SapEvent(String name, List<Map<String, String>> metaGroups) {

        public SapEvent {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("event name is required");
            }
            metaGroups = List.copyOf(metaGroups);
        }

        public static Builder builder(String name) {
            return new Builder(name);
        }

        public static final class Builder {
            private final String name;
            private final java.util.List<Map<String, String>> groups = new java.util.ArrayList<>();

            private Builder(String name) {
                this.name = name;
            }

            public Builder meta(java.util.function.Consumer<LinkedHashMap<String, String>> populate) {
                LinkedHashMap<String, String> group = new LinkedHashMap<>();
                populate.accept(group);
                groups.add(group);
                return this;
            }

            public SapEvent build() {
                return new SapEvent(name, groups);
            }
        }
    }
}
