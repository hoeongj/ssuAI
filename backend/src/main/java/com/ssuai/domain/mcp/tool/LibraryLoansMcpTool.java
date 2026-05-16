package com.ssuai.domain.mcp.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.ssuai.domain.library.dto.LibraryLoansResponse;
import com.ssuai.domain.library.mcp.LibraryToolContext;
import com.ssuai.domain.library.service.LibraryLoansService;

@Component
public class LibraryLoansMcpTool {

    private final LibraryLoansService loansService;

    public LibraryLoansMcpTool(LibraryLoansService loansService) {
        this.loansService = loansService;
    }

    @Tool(
            name = "get_my_library_loans",
            description = "로그인된 사용자의 중앙도서관 대출 현황을 가져옵니다. "
                    + "대출 도서 목록, 반납 기한, 연장 가능 여부가 포함됩니다. "
                    + "인자를 받지 않습니다 — 도서관 세션이 연동된 chat 세션에서만 호출 가능합니다."
    )
    public LibraryLoansResponse getMyLibraryLoans() {
        String sessionKey = LibraryToolContext.currentSessionKey();
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new IllegalStateException(
                    "이 도구는 도서관 세션이 연동된 chat 세션에서만 호출 가능합니다.");
        }
        return loansService.getLoansForSession(sessionKey);
    }
}
