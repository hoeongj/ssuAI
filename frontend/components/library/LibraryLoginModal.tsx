"use client";

import { useEffect, useRef, useState } from "react";

import { ExternalLink, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useLibraryAuth } from "@/contexts/LibraryAuthContext";
import { useLibrarySession } from "@/hooks/useLibrarySession";

interface LibraryLoginModalProps {
  onClose: () => void;
}

export function LibraryLoginModal({ onClose }: LibraryLoginModalProps) {
  const [token, setToken] = useState("");
  const { state, errorMessage, submitToken } = useLibrarySession();
  const { setConnected } = useLibraryAuth();
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

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
      setConnected(true);
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
              좌석 현황·대출 내역 조회에 필요합니다. (약 2시간 유효)
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

        <div className="mb-4 space-y-2.5">
          <a
            href="https://oasis.ssu.ac.kr"
            target="_blank"
            rel="noopener noreferrer"
            className="flex w-full items-center justify-center gap-2 rounded-md border border-input bg-background px-3 py-2 text-sm font-medium hover:bg-accent"
          >
            <ExternalLink className="h-4 w-4" aria-hidden="true" />
            oasis.ssu.ac.kr 열고 로그인하기
          </a>
          <div className="rounded-md bg-muted/50 px-3 py-2.5 text-xs text-muted-foreground">
            <p className="font-medium text-foreground">로그인 후 토큰 복사:</p>
            <ol className="mt-1 space-y-0.5 leading-relaxed">
              <li>① 좌석 예약 페이지로 이동</li>
              <li>② F12 → Network 탭 → pyxis-api 요청 클릭</li>
              <li>③ Request Headers에서 <code className="font-mono">Pyxis-Auth-Token</code> 복사</li>
            </ol>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="space-y-3">
          <textarea
            ref={textareaRef}
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="복사한 Pyxis-Auth-Token 붙여넣기"
            rows={3}
            className="w-full resize-none rounded-md border border-input bg-background px-3 py-2 font-mono text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            disabled={isSubmitting || state === "success"}
          />

          {errorMessage ? (
            <p className="text-sm text-destructive">{errorMessage}</p>
          ) : null}

          {state === "success" ? (
            <p className="text-sm text-emerald-600">연동 완료! 데이터를 불러오는 중…</p>
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
