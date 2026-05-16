package com.ssuai.domain.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.ssuai.domain.saint.dto.GradesResponse;
import com.ssuai.domain.saint.mcp.SaintToolContext;
import com.ssuai.domain.saint.service.SaintGradesService;

/**
 * MCP tool that returns the authenticated student's u-SAINT cumulative
 * grades (Task 16 PR 16c + MCP-tools follow-up).
 *
 * <p>Same auth shape as {@link SaintScheduleMcpTool}: no caller-supplied
 * student id, identity read from {@link SaintToolContext} bound by the
 * chat path. External MCP clients cannot call this tool.
 *
 * <p>Grade content never crosses into the LLM token stream — the chat
 * path runs {@code LlmChatService.compactAndCap("get_my_grades", …)}
 * which reduces the response to {@code {count, link}} before any LLM
 * sees it (Task 16 spec §6 #6, pinned by PR #130 unit tests). The full
 * payload returned by this tool is reachable only through the controller
 * path {@code GET /api/saint/grades} (rendered by the frontend, never
 * shipped to a third-party LLM provider).
 */
@Component
public class SaintGradesMcpTool {

    private final SaintGradesService gradesService;

    public SaintGradesMcpTool(SaintGradesService gradesService) {
        this.gradesService = gradesService;
    }

    @Tool(
            name = "get_my_grades",
            description = "로그인된 학생 본인의 u-SAINT 누적 성적을 가져옵니다. "
                    + "응답에는 학기별 GPA 이력, 학적부/증명 누적 통계, 학기별 과목 세부가 포함됩니다. "
                    + "학번/이름 같은 인자를 받지 않습니다 — 인증된 chat 세션에서만 호출 가능합니다. "
                    + "chat 답변은 본문이 아닌 '성적 페이지에서 N과목 확인 가능합니다' 형식의 인용으로 제한됩니다."
    )
    public GradesResponse getMyGrades() {
        String studentId = SaintToolContext.currentStudentId();
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalStateException(
                    "이 도구는 인증된 chat 세션에서만 호출 가능합니다. "
                            + "외부 MCP 클라이언트는 본인 학번 인자를 전달할 수 없습니다.");
        }
        return gradesService.fetchGrades(studentId);
    }
}
