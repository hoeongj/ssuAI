import { Loader2 } from "lucide-react";

import { cn } from "@/lib/utils";

export type ChatMessageRole = "user" | "assistant";

interface MessageBubbleProps {
  role: ChatMessageRole;
  content: string;
  isLoading?: boolean;
}

export function MessageBubble({ role, content, isLoading = false }: MessageBubbleProps) {
  const isUser = role === "user";

  return (
    <div className={cn("flex w-full", isUser ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[min(42rem,86%)] break-words rounded-md px-4 py-3 text-sm leading-6",
          isUser
            ? "bg-primary text-primary-foreground"
            : "border border-border bg-muted text-foreground",
        )}
      >
        {isLoading ? (
          <span className="flex items-center gap-2">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            답변 준비 중...
          </span>
        ) : (
          <p className="whitespace-pre-wrap">{content}</p>
        )}
      </div>
    </div>
  );
}
