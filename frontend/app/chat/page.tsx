import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { ChatPanel } from "@/components/chat/ChatPanel";
import { buttonVariants } from "@/components/ui/button";

export default function ChatPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-4 px-4 py-6 sm:gap-5 sm:px-6 lg:px-8">
      <header className="flex items-center justify-between gap-3 border-b border-border pb-5 sm:gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium text-muted-foreground sm:text-sm">Soongsil University</p>
          <h1 className="mt-1 truncate text-2xl font-semibold tracking-normal text-foreground sm:mt-2 sm:text-3xl">
            ssuAI Chat
          </h1>
        </div>
        <Link
          href="/"
          className={buttonVariants({ variant: "outline", size: "sm" })}
          aria-label="대시보드"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          <span className="hidden sm:inline">대시보드</span>
        </Link>
      </header>
      <ChatPanel />
    </main>
  );
}
