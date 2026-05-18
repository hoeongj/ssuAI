"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { useSaintAuth } from "@/hooks/useSaintAuth";

const ERROR_MESSAGES: Record<string, string> = {
  auth_failed: "유세인트 로그인에 실패했어요. 다시 시도해 주세요.",
  portal_unavailable:
    "유세인트 포털이 응답하지 않아요. 잠시 후 다시 시도해 주세요.",
  lms_auth_failed: "LMS 로그인에 실패했어요. 다시 시도해 주세요.",
  lms_unknown: "LMS 로그인 처리 중 알 수 없는 오류가 발생했어요. 다시 시도해 주세요.",
  unknown: "알 수 없는 오류가 발생했어요. 다시 시도해 주세요.",
};

function AuthReturnContent() {
  const params = useSearchParams();
  const router = useRouter();
  const { refresh } = useSaintAuth();
  const ok = params.get("ok") === "1";
  const lmsOk = params.get("lms_ok") === "1";
  const errorCode = params.get("error");
  const [refreshSettled, setRefreshSettled] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);

  useEffect(() => {
    if (!ok && !lmsOk) return;
    let cancelled = false;
    refresh().then((success) => {
      if (cancelled) return;
      setRefreshSettled(true);
      if (success) {
        router.replace("/");
      } else {
        setRefreshFailed(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [ok, lmsOk, refresh, router]);

  // Pending only on the success path while the refresh round-trip is in
  // flight. The error path renders immediately; no effect involved.
  const pending = (ok || lmsOk) && !refreshSettled;

  if (pending) {
    return <p className="text-sm text-muted-foreground">로그인 처리 중…</p>;
  }

  if (refreshFailed) {
    return (
      <>
        <h1 className="text-2xl font-semibold">세션을 만들지 못했어요</h1>
        <p className="text-sm text-muted-foreground">
          SSO 는 통과했지만 ssuAI 세션 갱신에 실패했습니다. 잠시 후 다시
          시도해 주세요.
        </p>
        <Link href="/auth/login" className="text-sm underline">
          다시 로그인하기
        </Link>
      </>
    );
  }

  if (ok || lmsOk) {
    // success path — already redirected via router.replace("/"); component is
    // about to unmount, render a quiet placeholder.
    return null;
  }

  const message = errorCode
    ? (ERROR_MESSAGES[errorCode] ?? ERROR_MESSAGES.unknown)
    : ERROR_MESSAGES.unknown;

  return (
    <>
      <h1 className="text-2xl font-semibold">로그인 실패</h1>
      <p className="text-sm text-muted-foreground">{message}</p>
      <Link href="/auth/login" className="text-sm underline">
        다시 시도
      </Link>
    </>
  );
}

export default function AuthReturnPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center gap-4 px-4 py-12 sm:px-6">
      <Suspense fallback={<p className="text-sm text-muted-foreground">로딩 중…</p>}>
        <AuthReturnContent />
      </Suspense>
    </main>
  );
}
