import Link from "next/link";

import { SaintLoginButton } from "@/components/auth/SaintLoginButton";

export const metadata = {
  title: "유세인트로 로그인 · ssuAI",
};

export default function AuthLoginPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center gap-8 px-4 py-12 sm:px-6">
      <header>
        <p className="text-sm font-medium text-muted-foreground">ssuAI 로그인</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-normal text-foreground">
          유세인트로 로그인
        </h1>
        <p className="mt-3 text-sm text-muted-foreground">
          학교 SmartID 페이지에서 본인 확인 후 ssuAI 로 돌아옵니다. ssuAI 는
          학생 비밀번호를 절대 저장하지 않아요.
        </p>
      </header>

      <SaintLoginButton className="w-full" />

      <p className="text-xs text-muted-foreground">
        로그인하지 않아도 <Link href="/" className="underline">대시보드</Link> 와
        <Link href="/chat" className="ml-1 underline">챗봇</Link> 의 공개 기능 (학식,
        기숙사, 시설, 도서관 좌석·도서) 은 그대로 쓸 수 있어요.
      </p>
    </main>
  );
}
