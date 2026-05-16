package com.ssuai.domain.saint.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.saint.dto.ScheduleEntry;

class SaintScheduleParserTests {

    private static final Path FIXTURE = Path.of(
            "src", "test", "resources", "saint", "timetable-success.html");

    @Test
    void parsesPinnedFixtureIntoExpectedEntries() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        List<ScheduleEntry> entries = SaintScheduleParser.parse(html);

        // 1교시: every cell empty (skipped). 3교시: 4 lectures (월/화/수/목).
        // 5교시: 3 lectures (월/화/수). Total = 7.
        assertThat(entries).hasSize(7);

        ScheduleEntry mondayThird = entries.stream()
                .filter(e -> e.dayOfWeek() == 1 && e.period() == 3)
                .findFirst()
                .orElseThrow();
        assertThat(mondayThird.dayLabel()).isEqualTo("월");
        assertThat(mondayThird.course()).isEqualTo("자료구조");
        assertThat(mondayThird.professor()).isEqualTo("김교수");
        assertThat(mondayThird.timeRange()).isEqualTo("10:30-11:45");
        assertThat(mondayThird.room()).isEqualTo("정보과학관 30100 (강의실A)");

        ScheduleEntry tuesdayFifth = entries.stream()
                .filter(e -> e.dayOfWeek() == 2 && e.period() == 5)
                .findFirst()
                .orElseThrow();
        assertThat(tuesdayFifth.course()).isEqualTo("채플");
        assertThat(tuesdayFifth.timeRange()).isEqualTo("13:30-14:20");
        assertThat(tuesdayFifth.room()).isEqualTo("한경직기념관 10000");
    }

    @Test
    void allThirdPeriodEntriesPresent() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        List<ScheduleEntry> entries = SaintScheduleParser.parse(html);

        long thirdPeriod = entries.stream().filter(e -> e.period() == 3).count();
        assertThat(thirdPeriod).isEqualTo(4);
        // 자료구조 in cc=1 (월) + cc=3 (수); 알고리즘 in cc=2 (화) + cc=4 (목).
        assertThat(entries.stream()
                .filter(e -> e.period() == 3 && "자료구조".equals(e.course()))
                .map(ScheduleEntry::dayOfWeek))
                .containsExactlyInAnyOrder(1, 3);
        assertThat(entries.stream()
                .filter(e -> e.period() == 3 && "알고리즘".equals(e.course()))
                .map(ScheduleEntry::dayOfWeek))
                .containsExactlyInAnyOrder(2, 4);
    }

    @Test
    void skipsRowsWhereEveryCellIsEmpty() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        List<ScheduleEntry> entries = SaintScheduleParser.parse(html);

        // No 1교시 entries because every cc cell in that row is lsSTEmptyRow.
        assertThat(entries.stream().anyMatch(e -> e.period() == 1)).isFalse();
    }

    @Test
    void blankAndNullHtmlReturnEmpty() {
        assertThat(SaintScheduleParser.parse(null)).isEmpty();
        assertThat(SaintScheduleParser.parse("")).isEmpty();
        assertThat(SaintScheduleParser.parse("   ")).isEmpty();
    }

    @Test
    void htmlWithoutTimetableTableReturnsEmpty() {
        // Page that's structurally a SAP wrapper but missing the
        // contentTBody container — e.g. a redirect-to-login page.
        String html = "<html><body><div>세션이 만료되었습니다.</div></body></html>";

        assertThat(SaintScheduleParser.parse(html)).isEmpty();
    }
}
