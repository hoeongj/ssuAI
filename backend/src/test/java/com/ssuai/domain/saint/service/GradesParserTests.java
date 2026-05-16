package com.ssuai.domain.saint.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.saint.dto.CourseGrade;
import com.ssuai.domain.saint.dto.GpaSummary;
import com.ssuai.domain.saint.dto.TermGpa;

class GradesParserTests {

    @Test
    void termHistoryPicksUpAllRowsWithKoreanSemesterLabels() throws IOException {
        String html = unwrap(loadFixture("grades-success.html"));

        List<TermGpa> history = GradesParser.parseTermHistory(html);

        assertThat(history).hasSize(6);
        assertThat(history.get(0).year()).isEqualTo(2025);
        assertThat(history.get(0).term()).isEqualTo("겨울학기");
        assertThat(history.get(0).gpa()).isEqualTo(0.0d);
        assertThat(history.get(0).passFailCredits()).isEqualTo(3.0d);
        assertThat(history.get(1).term()).isEqualTo("2학기");
        assertThat(history.get(1).gpa()).isEqualTo(3.50d);
        assertThat(history.get(5).year()).isEqualTo(2022);
        assertThat(history.get(5).term()).isEqualTo("1학기");
    }

    @Test
    void academicSummaryReadsLabelInputPairs() throws IOException {
        String html = unwrap(loadFixture("grades-success.html"));

        GpaSummary summary = GradesParser.parseAcademicSummary(html);

        assertThat(summary.requestedCredits()).isEqualTo(75.0d);
        assertThat(summary.earnedCredits()).isEqualTo(75.0d);
        assertThat(summary.gpaSum()).isEqualTo(262.50d);
        assertThat(summary.gpa()).isEqualTo(3.50d);
        assertThat(summary.arithmeticAverage()).isEqualTo(85.00d);
        assertThat(summary.passFailCredits()).isEqualTo(12.0d);
    }

    @Test
    void certificateSummaryIsSeparateFromAcademicRecord() throws IOException {
        String html = unwrap(loadFixture("grades-success.html"));

        GpaSummary summary = GradesParser.parseCertificateSummary(html);

        assertThat(summary.requestedCredits()).isEqualTo(72.0d);
        assertThat(summary.earnedCredits()).isEqualTo(72.0d);
        assertThat(summary.gpaSum()).isEqualTo(252.00d);
    }

    @Test
    void detailTableIsEmptyOnFirstGetByDesign() throws IOException {
        String html = unwrap(loadFixture("grades-success.html"));

        List<CourseGrade> rows = GradesParser.parseDetailRows(html);

        assertThat(rows).isEmpty();
    }

    @Test
    void detailTablePopulatesAfterPrevButtonPress() throws IOException {
        String html = unwrap(loadFixture("grades-prev-success.html"));

        List<CourseGrade> rows = GradesParser.parseDetailRows(html);

        assertThat(rows).hasSize(2);
        CourseGrade letter = rows.get(0);
        assertThat(letter.score()).isEqualTo("95");
        assertThat(letter.grade()).isEqualTo("A0");
        assertThat(letter.courseName()).isEqualTo("과목A");
        assertThat(letter.courseCode()).isEqualTo("21500001");
        assertThat(letter.credits()).isEqualTo(3.0d);
        assertThat(letter.professor()).isEqualTo("김교수");
        assertThat(letter.remark()).isEmpty();

        CourseGrade passFail = rows.get(1);
        assertThat(passFail.score()).isEqualTo("P");
        assertThat(passFail.grade()).isEqualTo("P");
        assertThat(passFail.credits()).isEqualTo(0.5d);
    }

    @Test
    void blankOrNullHtmlReturnsEmptyResultsAndZeroSummary() {
        assertThat(GradesParser.parseTermHistory(null)).isEmpty();
        assertThat(GradesParser.parseTermHistory("")).isEmpty();
        assertThat(GradesParser.parseDetailRows("   ")).isEmpty();
        GpaSummary zero = GradesParser.parseAcademicSummary("");
        assertThat(zero.requestedCredits()).isEqualTo(0.0d);
        assertThat(zero.gpa()).isEqualTo(0.0d);
    }

    private static String loadFixture(String name) throws IOException {
        return Files.readString(
                Path.of("src", "test", "resources", "saint", name),
                StandardCharsets.UTF_8);
    }

    private static String unwrap(String fullResponse) {
        // Both fixtures live as `<updates><full-update><content-update>[CDATA HTML]`
        // wrappers — the connector strips that envelope before handing
        // the body to the parser, so the unit tests do the same.
        return WebDynproResponseUnwrapper.extractHtml(fullResponse);
    }
}
