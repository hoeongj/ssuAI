package com.ssuai.domain.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.saint.dto.ScheduleResponse;
import com.ssuai.domain.saint.dto.TermSchedule;
import com.ssuai.domain.saint.mcp.SaintToolContext;
import com.ssuai.domain.saint.service.SaintScheduleService;

class SaintScheduleMcpToolTests {

    private final SaintScheduleService scheduleService = mock(SaintScheduleService.class);
    private final SaintScheduleMcpTool tool = new SaintScheduleMcpTool(scheduleService);

    @Test
    void refusesWhenSaintToolContextIsNotBound() {
        assertThatThrownBy(tool::getMySchedule)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증된 chat 세션에서만");

        verify(scheduleService, never()).fetchSchedule(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refusesWhenBoundStudentIdIsBlank() {
        try (SaintToolContext.Scope ignored = SaintToolContext.withStudentId("   ")) {
            assertThatThrownBy(tool::getMySchedule)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("인증된 chat 세션에서만");
        }

        verify(scheduleService, never()).fetchSchedule(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesBoundStudentIdInsteadOfAcceptingItAsAToolParameter() {
        ScheduleResponse stub = new ScheduleResponse(2022, 2025, 2, List.of(
                new TermSchedule(2025, 2, List.of())));
        when(scheduleService.fetchSchedule("20221528")).thenReturn(stub);

        try (SaintToolContext.Scope ignored = SaintToolContext.withStudentId("20221528")) {
            ScheduleResponse result = tool.getMySchedule();

            assertThat(result).isSameAs(stub);
        }
        verify(scheduleService).fetchSchedule("20221528");
    }
}
