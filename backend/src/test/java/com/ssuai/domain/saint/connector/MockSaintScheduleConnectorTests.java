package com.ssuai.domain.saint.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.saint.dto.ScheduleResponse;

class MockSaintScheduleConnectorTests {

    private static final Clock CLOCK_2026_05_16 = Clock.fixed(
            Instant.parse("2026-05-16T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void iteratesFromEnrollmentYearToCurrentYearInDescendingOrder() {
        MockSaintScheduleConnector connector = new MockSaintScheduleConnector(CLOCK_2026_05_16);

        ScheduleResponse response = connector.fetchSchedule("20221234",
                new PortalCookies("MYSAPSSO2=mock"));

        // 2026 → 2022 inclusive = 5 terms, ordered current-first.
        assertThat(response.enrollmentYear()).isEqualTo(2022);
        assertThat(response.currentYear()).isEqualTo(2026);
        assertThat(response.currentTerm()).isEqualTo(1);
        assertThat(response.terms()).hasSize(5);
        assertThat(response.terms().get(0).year()).isEqualTo(2026);
        assertThat(response.terms().get(4).year()).isEqualTo(2022);
    }

    @Test
    void freshlyEnrolledStudentGetsOneTerm() {
        MockSaintScheduleConnector connector = new MockSaintScheduleConnector(CLOCK_2026_05_16);

        ScheduleResponse response = connector.fetchSchedule("20261234",
                new PortalCookies("MYSAPSSO2=mock"));

        assertThat(response.terms()).hasSize(1);
        assertThat(response.terms().get(0).year()).isEqualTo(2026);
    }

    @Test
    void mockEntriesPopulateEveryTerm() {
        MockSaintScheduleConnector connector = new MockSaintScheduleConnector(CLOCK_2026_05_16);

        ScheduleResponse response = connector.fetchSchedule("20221234",
                new PortalCookies("MYSAPSSO2=mock"));

        assertThat(response.terms()).allSatisfy(term ->
                assertThat(term.entries()).isNotEmpty());
    }
}
