package com.ssuai.domain.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.ssuai.domain.saint.dto.ScheduleResponse;
import com.ssuai.domain.saint.mcp.SaintToolContext;
import com.ssuai.domain.saint.service.SaintScheduleService;

/**
 * MCP tool that returns the authenticated student's u-SAINT timetable
 * across every enrolled year (Task 16 PR 16b + MCP-tools follow-up).
 *
 * <p>Unlike the public tools ({@code get_today_meal}, {@code get_library_seat_status}…),
 * this tool takes <strong>no caller-supplied student id</strong>. Allowing a
 * client-supplied id would let an external MCP client (Claude Desktop, an
 * arbitrary MCP-aware agent) spoof any student. Instead the chat path
 * binds the authenticated student id to {@link SaintToolContext} before
 * dispatching the tool callback; this method reads it back. An external
 * MCP client that calls this tool without going through chat will see
 * the context empty and receive an explicit failure.
 *
 * <p>The chat path is also free to bypass MCP transport entirely and
 * call {@link SaintScheduleService} directly with the authenticated
 * student id — both routes funnel through the same service, and the
 * spec §6 #6 compact policy lives in {@code LlmChatService.compactAndCap}
 * regardless of which route runs.
 */
@Component
public class SaintScheduleMcpTool {

    private final SaintScheduleService scheduleService;

    public SaintScheduleMcpTool(SaintScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Tool(
            name = "get_my_schedule",
            description = "로그인된 학생 본인의 u-SAINT 시간표를 입학년도부터 현재 학기까지 전부 가져옵니다. "
                    + "응답에는 학기별 강의 목록 (요일·교시·과목명·강의실) 이 포함됩니다. "
                    + "학번/이름 같은 인자를 받지 않습니다 — 인증된 chat 세션에서만 호출 가능합니다."
    )
    public ScheduleResponse getMySchedule() {
        String studentId = SaintToolContext.currentStudentId();
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalStateException(
                    "이 도구는 인증된 chat 세션에서만 호출 가능합니다. "
                            + "외부 MCP 클라이언트는 본인 학번 인자를 전달할 수 없습니다.");
        }
        return scheduleService.fetchSchedule(studentId);
    }
}
