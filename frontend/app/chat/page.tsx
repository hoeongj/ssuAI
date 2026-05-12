import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { ChatPanel } from "@/components/chat/ChatPanel";
import { buttonVariants } from "@/components/ui/button";

export default function ChatPage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col gap-5 px-4 py-6 sm:px-6 lg:px-8">
      <header className="flex items-center justify-between gap-4 border-b border-border pb-5">
        <div className="min-w-0">
          <p className="text-sm font-medium text-muted-foreground">Soongsil University</p>
          <h1 className="mt-2 truncate text-3xl font-semibold tracking-normal text-foreground">ssuAI Chat</h1>
        </div>
        <Link href="/" className={buttonVariants({ variant: "outline" })}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          대시보드
        </Link>
      </header>
      <ChatPanel />
    </main>
  );
}
