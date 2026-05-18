import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import McpLibraryAuthPage from "./page";
import { ApiError } from "@/lib/api/types";

vi.mock("next/navigation", () => ({
  useSearchParams: vi.fn(),
}));

vi.mock("@/lib/api/library", () => ({
  completeMcpLibraryAuth: vi.fn(),
}));

vi.mock("@/lib/crypto", () => ({
  encryptLibraryPassword: vi.fn().mockResolvedValue("encrypted-pw"),
}));

import { useSearchParams } from "next/navigation";
import { completeMcpLibraryAuth } from "@/lib/api/library";

const mockUseSearchParams = vi.mocked(useSearchParams);
const mockComplete = vi.mocked(completeMcpLibraryAuth);

function makeParams(state: string | null) {
  return { get: (key: string) => (key === "state" ? state : null) } as ReturnType<typeof useSearchParams>;
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseSearchParams.mockReturnValue(makeParams("valid-state-token"));
  mockComplete.mockResolvedValue(null);
});

describe("McpLibraryAuthPage", () => {
  it("shows the login form when state is present", () => {
    render(<McpLibraryAuthPage />);

    expect(screen.getByLabelText("학번")).toBeInTheDocument();
    expect(screen.getByLabelText("비밀번호 (유세인트 비밀번호)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "도서관 로그인" })).toBeInTheDocument();
  });

  it("shows an error and disables form when state is missing", () => {
    mockUseSearchParams.mockReturnValue(makeParams(null));

    render(<McpLibraryAuthPage />);

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("학번")).toBeDisabled();
    expect(screen.getByLabelText("비밀번호 (유세인트 비밀번호)")).toBeDisabled();
  });

  it("submit button is disabled while fields are empty", () => {
    render(<McpLibraryAuthPage />);

    expect(screen.getByRole("button", { name: "도서관 로그인" })).toBeDisabled();
  });

  it("calls completeMcpLibraryAuth with encrypted password on submit", async () => {
    const user = userEvent.setup();
    render(<McpLibraryAuthPage />);

    await user.type(screen.getByLabelText("학번"), "20221528");
    await user.type(screen.getByLabelText("비밀번호 (유세인트 비밀번호)"), "mypassword");
    await user.click(screen.getByRole("button", { name: "도서관 로그인" }));

    await waitFor(() => {
      expect(mockComplete).toHaveBeenCalledWith({
        state: "valid-state-token",
        loginId: "20221528",
        password: "encrypted-pw",
      });
    });
  });

  it("shows success message after successful login", async () => {
    const user = userEvent.setup();
    render(<McpLibraryAuthPage />);

    await user.type(screen.getByLabelText("학번"), "20221528");
    await user.type(screen.getByLabelText("비밀번호 (유세인트 비밀번호)"), "mypassword");
    await user.click(screen.getByRole("button", { name: "도서관 로그인" }));

    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("로그인이 완료되었습니다.");
    });
    expect(screen.queryByRole("button", { name: "도서관 로그인" })).not.toBeInTheDocument();
  });

  it("shows auth failure error message when credentials are wrong", async () => {
    mockComplete.mockRejectedValue(new ApiError("AUTH_FAILED", "auth failed", "", 401));
    const user = userEvent.setup();
    render(<McpLibraryAuthPage />);

    await user.type(screen.getByLabelText("학번"), "20221528");
    await user.type(screen.getByLabelText("비밀번호 (유세인트 비밀번호)"), "wrong");
    await user.click(screen.getByRole("button", { name: "도서관 로그인" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "로그인에 실패했습니다. 학번과 비밀번호를 확인해 주세요.",
      );
    });
  });

  it("shows invalid state error when server rejects state", async () => {
    mockComplete.mockRejectedValue(new ApiError("INVALID_STATE", "expired", "", 400));
    const user = userEvent.setup();
    render(<McpLibraryAuthPage />);

    await user.type(screen.getByLabelText("학번"), "20221528");
    await user.type(screen.getByLabelText("비밀번호 (유세인트 비밀번호)"), "pw");
    await user.click(screen.getByRole("button", { name: "도서관 로그인" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("인증 요청이 만료되었거나");
    });
  });

  it("state token is not rendered into the page HTML", () => {
    render(<McpLibraryAuthPage />);

    // state is read from URL and kept in component state; it must not be
    // echoed into any visible element or hidden field in the DOM.
    expect(document.body.innerHTML).not.toContain("valid-state-token");
  });

  it("raw password is never sent to the API — only the encrypted version", async () => {
    const user = userEvent.setup();
    render(<McpLibraryAuthPage />);

    await user.type(screen.getByLabelText("학번"), "20221528");
    await user.type(screen.getByLabelText("비밀번호 (유세인트 비밀번호)"), "secret123");
    await user.click(screen.getByRole("button", { name: "도서관 로그인" }));

    await waitFor(() => {
      const [call] = mockComplete.mock.calls;
      expect(call[0].password).toBe("encrypted-pw");
      expect(call[0].password).not.toBe("secret123");
    });
  });
});
