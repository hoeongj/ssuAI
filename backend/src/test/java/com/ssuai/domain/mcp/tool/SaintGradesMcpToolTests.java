package com.ssuai.domain.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.saint.dto.GpaSummary;
import com.ssuai.domain.saint.dto.GradesResponse;
import com.ssuai.domain.saint.mcp.SaintToolContext;
import com.ssuai.domain.saint.service.SaintGradesService;

class SaintGradesMcpToolTests {

    private final SaintGradesService gradesService = mock(SaintGradesService.class);
    private final SaintGradesMcpTool tool = new SaintGradesMcpTool(gradesService);

    @Test
    void refusesWhenSaintToolContextIsNotBound() {
        assertThatThrownBy(tool::getMyGrades)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("인증된 chat 세션에서만");

        verify(gradesService, never()).fetchGrades(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void usesBoundStudentIdInsteadOfAcceptingItAsAToolParameter() {
        GpaSummary zero = new GpaSummary(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        GradesResponse stub = new GradesResponse(List.of(), zero, zero, Map.of());
        when(gradesService.fetchGrades("20221528")).thenReturn(stub);

        try (SaintToolContext.Scope ignored = SaintToolContext.withStudentId("20221528")) {
            GradesResponse result = tool.getMyGrades();

            assertThat(result).isSameAs(stub);
        }
        verify(gradesService).fetchGrades("20221528");
    }
}
