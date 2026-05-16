"use client";

import { useEffect, useRef, useState } from "react";

import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useLibrarySession } from "@/hooks/useLibrarySession";

interface LibraryLoginModalProps {
  onClose: () => void;
}

export function LibraryLoginModal({ onClose }: LibraryLoginModalProps) {
  const [token, setToken] = useState("");
  const { state, errorMessage, submitToken } = useLibrarySession();
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  // Close on Escape
  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!token.trim()) return;
    const ok = await submitToken(token);
    if (ok) {
      setTimeout(onClose, 800);
    }
  }

  const isSubmitting = state === "submitting";

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="library-login-title"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="w-full max-w-md rounded-lg border border-border bg-card p-6 shadow-lg">
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <h2 id="library-login-title" className="text-base font-semibold text-foreground">
              도서관 연동
            </h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              실시간 좌석 현황을 조회하려면 Pyxis-Auth-Token이 필요합니다.
            </p>
          </div>
          <button
            type="button"
            aria-label="닫기"
            className="rounded p-1 hover:bg-accent"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <ol className="mb-4 space-y-1.5 text-sm text-muted-foreground">
          <li>
            <span className="font-medium text-foreground">1.</span>{" "}
            <a
              href="https://oasis.ssu.ac.kr"
              target="_blank"
              rel="noopener noreferrer"
              className="underline underline-offset-2"
            >
              oasis.ssu.ac.kr
            </a>
            에 로그인합니다.
          </li>
          <li>
            <span className="font-medium text-foreground">2.</span>{" "}
            좌석 예약 페이지로 이동합니다.
          </li>
          <li>
            <span className="font-medium text-foreground">3.</span>{" "}
            <kbd className="rounded border border-border bg-muted px-1 py-0.5 font-mono text-xs">
              F12
            </kbd>{" "}
            → Network 탭 → <code className="font-mono text-xs">pyxis-api</code> 요청 클릭
          </li>
          <li>
            <span className="font-medium text-foreground">4.</span>{" "}
            Request Headers에서{" "}
            <code className="font-mono text-xs">Pyxis-Auth-Token</code> 값 복사
          </li>
          <li>
            <span className="font-medium text-foreground">5.</span>{" "}
            아래에 붙여넣기 후 확인 (약 2시간 유효)
          </li>
        </ol>

        <form onSubmit={handleSubmit} className="space-y-3">
          <textarea
            ref={textareaRef}
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="Pyxis-Auth-Token 값 붙여넣기"
            rows={3}
            className="w-full resize-none rounded-md border border-input bg-background px-3 py-2 font-mono text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            disabled={isSubmitting || state === "success"}
          />

          {errorMessage ? (
            <p className="text-sm text-destructive">{errorMessage}</p>
          ) : null}

          {state === "success" ? (
            <p className="text-sm text-emerald-600">연동 완료! 좌석 현황을 불러오는 중…</p>
          ) : null}

          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              취소
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={!token.trim() || isSubmitting || state === "success"}
            >
              {isSubmitting ? "저장 중…" : "확인"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
