"use client";

import { useEffect, useRef, useState } from "react";

import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useLibraryAuth } from "@/contexts/LibraryAuthContext";
import { loginLibrary } from "@/lib/api/library";
import { encryptLibraryPassword } from "@/lib/crypto";
import { useQueryClient } from "@tanstack/react-query";

interface LibraryLoginModalProps {
  onClose: () => void;
}

export function LibraryLoginModal({ onClose }: LibraryLoginModalProps) {
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const { setConnected } = useLibraryAuth();
  const queryClient = useQueryClient();
  const loginIdRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loginIdRef.current?.focus();
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
    if (!loginId.trim() || !password) return;
    setSubmitting(true);
    setError(null);
    try {
      await loginLibrary(loginId.trim(), await encryptLibraryPassword(password));
      setSuccess(true);
      setConnected(true);
      await queryClient.invalidateQueries({ queryKey: ["library", "loans"] });
      await queryClient.invalidateQueries({ queryKey: ["library", "seats"] });
      setTimeout(onClose, 800);
    } catch {
      setError("로그인에 실패했습니다. 학번과 비밀번호를 확인해주세요.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="library-login-title"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-sm rounded-lg border border-border bg-card p-6 shadow-lg">
        <div className="mb-5 flex items-start justify-between gap-3">
          <div>
            <h2 id="library-login-title" className="text-base font-semibold text-foreground">
              도서관 연동
            </h2>
            <p className="mt-0.5 text-sm text-muted-foreground">
              대출 현황 조회를 위해 학교 계정으로 로그인합니다.
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

        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label htmlFor="library-login-id" className="mb-1 block text-xs font-medium text-muted-foreground">
              학번
            </label>
            <input
              id="library-login-id"
              ref={loginIdRef}
              type="text"
              inputMode="numeric"
              autoComplete="username"
              value={loginId}
              onChange={(e) => setLoginId(e.target.value)}
              placeholder="20221528"
              disabled={submitting || success}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
            />
          </div>

          <div>
            <label htmlFor="library-password" className="mb-1 block text-xs font-medium text-muted-foreground">
              비밀번호 (유세인트 비밀번호)
            </label>
            <input
              id="library-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={submitting || success}
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
            />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
          {success && <p className="text-sm text-emerald-600">연동 완료!</p>}

          <p className="text-xs text-muted-foreground">
            비밀번호는 도서관 로그인에만 사용되며 ssuAI 서버에 저장되지 않습니다.
          </p>

          <div className="flex justify-end gap-2 pt-1">
            <Button type="button" variant="outline" size="sm" onClick={onClose}>
              취소
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={!loginId.trim() || !password || submitting || success}
            >
              {submitting ? "로그인 중…" : "연동"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
