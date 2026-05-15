"use client";

import Link from "next/link";

import { useSaintAuth } from "@/hooks/useSaintAuth";

/**
 * Header slot for the dashboard. Renders one of three states:
 *   - loading: empty placeholder (avoids a CTA flicker on first paint
 *     while the refresh-cookie probe is in flight)
 *   - signed in: "안녕하세요, OOO 학생" greeting
 *   - anonymous: a "유세인트로 로그인" link to /auth/login
 */
export function UserGreeting() {
  const { user, isAuthenticated, isLoading } = useSaintAuth();

  if (isLoading) {
    return <span className="text-sm text-muted-foreground" aria-hidden="true">&nbsp;</span>;
  }

  if (isAuthenticated && user) {
    return (
      <p
        className="max-w-[8rem] truncate text-sm font-medium text-foreground sm:max-w-[12rem]"
        title={`안녕하세요, ${user.name} 학생`}
      >
        <span className="hidden sm:inline">안녕하세요, </span>
        <span className="font-semibold">{user.name}</span>
        <span className="hidden sm:inline"> 학생</span>
      </p>
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
