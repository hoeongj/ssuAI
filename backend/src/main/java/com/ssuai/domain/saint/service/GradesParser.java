package com.ssuai.domain.saint.service;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.ssuai.domain.saint.dto.CourseGrade;
import com.ssuai.domain.saint.dto.GpaSummary;
import com.ssuai.domain.saint.dto.TermGpa;

/**
 * Parses ZCMB3W0017 (학생 성적조회) HTML into the three views the
 * grades fetcher needs (Task 16 spec §3.5).
 *
 * <p>Two SAP NetWeaver tables drive the output:
 *
 * <ul>
 *   <li>The 학기별 성적 history (상단) — first
 *       {@code tbody[id$=-contentTBody]} in document order, 13 data
 *       columns (cc=1..13) plus the selection toggle at cc=0.
 *   <li>The 학기별 세부 성적 (하단) — second
 *       {@code tbody[id$=-contentTBody]}, 7 data columns (cc=1..7). On
 *       the first GET this table is empty; data rows appear only after
 *       a 이전학기 button-press POST hops back to a populated term.
 * </ul>
 *
 * <p>Cumulative summaries are not a table — they're a
 * {@code <label for>}/{@code <input id>} grid. The label text carries
 * the field name ("학적부신청학점") and the input's {@code value} carries
 * the figure. The parser matches by prefix ("학적부" vs "증명") + suffix
 * ("신청학점" / "취득학점" / …) so the {@code WDxxxx} ids may change
 * across renders without breaking us.
 */
public final class GradesParser {

    private static final String TBODY_SELECTOR = "tbody[id$=-contentTBody]";
    private static final String DATA_ROW_SELECTOR = "tr[rt=1]";
    private static final String TEXT_VIEW_SELECTOR = "span.lsTextView--wrap, span.lsTextView--nowrap";

    private static final String ACADEMIC_PREFIX = "학적부";
    private static final String CERTIFICATE_PREFIX = "증명";

    private static final String SUFFIX_REQUESTED = "신청학점";
    private static final String SUFFIX_EARNED = "취득학점";
    private static final String SUFFIX_GPA_SUM = "평점계";
    private static final String SUFFIX_GPA = "평점평균";
    private static final String SUFFIX_ARITHMETIC = "산술평균";
    private static final String SUFFIX_PASS_FAIL = "P/F학점";

    private GradesParser() {
    }

    public static List<TermGpa> parseTermHistory(String html) {
        Document document = parseSafely(html);
        if (document == null) {
            return List.of();
        }
        Element table = nthTbody(document, 0);
        if (table == null) {
            return List.of();
        }
        List<TermGpa> out = new ArrayList<>();
        for (Element row : table.select(DATA_ROW_SELECTOR)) {
            TermGpa parsed = parseTermRow(row);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    public static GpaSummary parseAcademicSummary(String html) {
        return parseSummary(html, ACADEMIC_PREFIX);
    }

    public static GpaSummary parseCertificateSummary(String html) {
        return parseSummary(html, CERTIFICATE_PREFIX);
    }

    public static List<CourseGrade> parseDetailRows(String html) {
        Document document = parseSafely(html);
        if (document == null) {
            return List.of();
        }
        Element table = nthTbody(document, 1);
        if (table == null) {
            return List.of();
        }
        List<CourseGrade> out = new ArrayList<>();
        for (Element row : table.select(DATA_ROW_SELECTOR)) {
            CourseGrade parsed = parseDetailRow(row);
            if (parsed != null) {
                out.add(parsed);
            }
        }
        return out;
    }

    private static GpaSummary parseSummary(String html, String prefix) {
        Document document = parseSafely(html);
        if (document == null) {
            return zeroSummary();
        }
        double requested = lookupSummaryValue(document, prefix + SUFFIX_REQUESTED);
        double earned = lookupSummaryValue(document, prefix + SUFFIX_EARNED);
        double gpaSum = lookupSummaryValue(document, prefix + SUFFIX_GPA_SUM);
        double gpa = lookupSummaryValue(document, prefix + SUFFIX_GPA);
        double arithmetic = lookupSummaryValue(document, prefix + SUFFIX_ARITHMETIC);
        double passFail = lookupSummaryValue(document, prefix + SUFFIX_PASS_FAIL);
        return new GpaSummary(requested, earned, gpaSum, gpa, arithmetic, passFail);
    }

    private static double lookupSummaryValue(Document document, String labelText) {
        for (Element label : document.select("label")) {
            if (label.text().equals(labelText)) {
                String targetId = label.attr("for");
                if (targetId.isEmpty()) {
                    continue;
                }
                Element input = document.getElementById(targetId);
                if (input == null) {
                    continue;
                }
                String value = input.attr("value");
                if (value == null || value.isBlank()) {
                    return 0.0d;
                }
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ignored) {
                    return 0.0d;
                }
            }
        }
        return 0.0d;
    }

    private static TermGpa parseTermRow(Element row) {
        Integer year = parseInt(cellText(row, 1));
        String term = cellText(row, 2);
        if (year == null || term == null || term.isBlank()) {
            return null;
        }
        return new TermGpa(
                year,
                term,
                parseDouble(cellText(row, 3)),
                parseDouble(cellText(row, 4)),
                parseDouble(cellText(row, 5)),
                parseDouble(cellText(row, 6)),
                parseDouble(cellText(row, 7)),
                parseDouble(cellText(row, 8)),
                stringOrEmpty(cellText(row, 9)),
                stringOrEmpty(cellText(row, 10)),
                isMarked(row, 11),
                isMarked(row, 12),
                isMarked(row, 13));
    }

    private static CourseGrade parseDetailRow(Element row) {
        String courseName = cellText(row, 3);
        if (courseName == null || courseName.isBlank()) {
            return null;
        }
        return new CourseGrade(
                stringOrEmpty(cellText(row, 1)),
                stringOrEmpty(cellText(row, 2)),
                courseName,
                stringOrEmpty(cellText(row, 4)),
                parseDouble(cellText(row, 5)),
                stringOrEmpty(cellText(row, 6)),
                stringOrEmpty(cellText(row, 7)));
    }

    private static String cellText(Element row, int cc) {
        Element cell = row.selectFirst("td[role=gridcell][cc=" + cc + "]");
        if (cell == null) {
            return null;
        }
        Element text = cell.selectFirst(TEXT_VIEW_SELECTOR);
        if (text != null) {
            String value = text.text().trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private static boolean isMarked(Element row, int cc) {
        Element cell = row.selectFirst("td[role=gridcell][cc=" + cc + "]");
        if (cell == null) {
            return false;
        }
        if (cell.selectFirst("div.lsSTEmptyRow") != null) {
            return false;
        }
        Element text = cell.selectFirst(TEXT_VIEW_SELECTOR);
        if (text == null) {
            return false;
        }
        String value = text.text().trim();
        return !value.isEmpty();
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Element nthTbody(Document document, int index) {
        Elements tbodies = document.select(TBODY_SELECTOR);
        if (tbodies.size() <= index) {
            return null;
        }
        return tbodies.get(index);
    }

    private static Document parseSafely(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return Jsoup.parse(html);
    }

    private static GpaSummary zeroSummary() {
        return new GpaSummary(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
    }
}
