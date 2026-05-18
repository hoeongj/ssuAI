"use client";

import { Suspense, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";

import { completeMcpLibraryAuth } from "@/lib/api/library";
import { encryptLibraryPassword } from "@/lib/crypto";
import { ApiError } from "@/lib/api/types";

type PageState = "idle" | "submitting" | "success" | "auth_failed" | "invalid_state" | "server_error";

function errorMessageForCode(code: string): string {
  switch (code) {
    case "INVALID_STATE":
      return "인증 요청이 만료되었거나 유효하지 않습니다. MCP 클라이언트에서 start_auth를 다시 호출해 주세요.";
    case "AUTH_FAILED":
      return "로그인에 실패했습니다. 학번과 비밀번호를 확인해 주세요.";
    default:
      return "로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
}

function McpLibraryAuthContent() {
  const params = useSearchParams();
  const state = params.get("state");

  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [pageState, setPageState] = useState<PageState>(state ? "idle" : "invalid_state");
  const [errorMessage, setErrorMessage] = useState<string | null>(
    state ? null : "인증 요청이 만료되었거나 잘못되었습니다. MCP 클라이언트에서 start_auth를 다시 호출해 주세요.",
  );
  const loginIdRef = useRef<HTMLInputElement>(null);

  const disabled = pageState === "submitting" || pageState === "success" || pageState === "invalid_state";

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!state || !loginId.trim() || !password) return;
    setPageState("submitting");
    setErrorMessage(null);
    try {
      const encryptedPassword = await encryptLibraryPassword(password);
      await completeMcpLibraryAuth({ state, loginId: loginId.trim(), password: encryptedPassword });
      setPageState("success");
    } catch (err) {
      const code = err instanceof ApiError ? err.code : "SERVER_ERROR";
      setErrorMessage(errorMessageForCode(code));
      setPageState(code === "AUTH_FAILED" ? "auth_failed" : "server_error");
    }
  }

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-sm flex-col justify-center gap-6 px-4 py-12 sm:px-6">
      <header>
        <p className="text-sm font-medium text-muted-foreground">ssuAI MCP 도서관 인증</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
          도서관 로그인
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          학번과 비밀번호를 입력하면 MCP 클라이언트에서 도서관 대출 현황을 조회할 수 있습니다.
          비밀번호는 도서관 로그인에만 사용되며 ssuAI 서버에 저장되지 않습니다.
        </p>
      </header>

      {pageState === "success" ? (
        <div
          role="status"
          className="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-5 text-sm text-emerald-800 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200"
        >
          <p className="font-medium">로그인이 완료되었습니다.</p>
          <p className="mt-1 text-muted-foreground">
            MCP 클라이언트로 돌아가 방금 요청을 다시 실행하세요.
          </p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" aria-label="도서관 MCP 로그인 폼">
          <div>
            <label
              htmlFor="mcp-library-login-id"
              className="mb-1 block text-xs font-medium text-muted-foreground"
            >
              학번
            </label>
            <input
              id="mcp-library-login-id"
              ref={loginIdRef}
              type="text"
              inputMode="numeric"
              autoComplete="username"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              placeholder="20221528"
              disabled={disabled}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
            />
          </div>

          <div>
            <label
              htmlFor="mcp-library-password"
              className="mb-1 block text-xs font-medium text-muted-foreground"
            >
              비밀번호 (유세인트 비밀번호)
            </label>
            <input
              id="mcp-library-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={disabled}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
            />
          </div>

          {errorMessage && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage}
            </p>
          )}

          <button
            type="submit"
            disabled={disabled || !loginId.trim() || !password}
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:pointer-events-none disabled:opacity-50"
          >
            {pageState === "submitting" ? "로그인 중…" : "도서관 로그인"}
          </button>
        </form>
      )}
    </main>
  );
}

export default function McpLibraryAuthPage() {
  return (
    <Suspense fallback={<p className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">로딩 중…</p>}>
      <McpLibraryAuthContent />
    </Suspense>
  );
}
