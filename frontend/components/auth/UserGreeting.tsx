"use client";

import Link from "next/link";

import { useSaintAuth } from "@/hooks/useSaintAuth";

export function UserGreeting() {
  const { user, isAuthenticated, isLoading, logout } = useSaintAuth();

  if (isLoading) {
    return <span className="text-sm text-muted-foreground" aria-hidden="true">&nbsp;</span>;
  }

  if (isAuthenticated && user) {
    return (
      <div className="flex items-center gap-2">
        <p
          className="max-w-[8rem] truncate text-sm font-medium text-foreground sm:max-w-[12rem]"
          title={`안녕하세요, ${user.name} 학생`}
        >
          <span className="hidden sm:inline">안녕하세요, </span>
          <span className="font-semibold">{user.name}</span>
          <span className="hidden sm:inline"> 학생</span>
        </p>
        <button
          type="button"
          className="text-xs text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
          onClick={() => void logout()}
        >
          로그아웃
        </button>
      </div>
    );
  }

  return (
    <Link
      href="/auth/login"
      className="text-sm font-medium text-primary underline-offset-4 hover:underline"
    >
      <span className="hidden sm:inline">유세인트로 로그인</span>
      <span className="sm:hidden">로그인</span>
    </Link>
  );
}
