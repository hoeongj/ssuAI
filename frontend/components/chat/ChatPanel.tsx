"use client";

import { Send, Sparkles } from "lucide-react";
import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";

import { MessageBubble, type ChatMessageRole } from "@/components/chat/MessageBubble";
import { ErrorState, getErrorStateDetails, type ErrorStateDetails } from "@/components/shared/ErrorState";
import { Button } from "@/components/ui/button";
import { sendChatMessage } from "@/lib/api/chat";
import { cn } from "@/lib/utils";

const MAX_MESSAGE_LENGTH = 1000;

const SAMPLE_PROMPTS = [
  "오늘 학식 뭐야?",
  "도서관 4층에 자리 있어?",
  "도서관에 파이썬 책 있어?",
  "이번 주 기숙사 식단 알려줘",
];

interface ChatMessage {
  id: string;
  role: ChatMessageRole;
  content: string;
}

function nextMessageId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function ChatPanel() {
  const [conversationId, setConversationId] = useState<string>();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [errorState, setErrorState] = useState<ErrorStateDetails | null>(null);
  const [lastFailedMessage, setLastFailedMessage] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isSending, errorState]);

  async function sendMessage(messageText: string, appendUserMessage = true) {
    const trimmed = messageText.trim();
    if (!trimmed || isSending) {
      return;
    }

    setErrorState(null);
    setLastFailedMessage(trimmed);
    if (appendUserMessage) {
      setMessages((current) => [
        ...current,
        { id: nextMessageId(), role: "user", content: trimmed },
      ]);
    }
    setIsSending(true);

    try {
      const response = await sendChatMessage({ conversationId, message: trimmed });
      setConversationId(response.conversationId);
      setMessages((current) => [
        ...current,
        { id: nextMessageId(), role: "assistant", content: response.reply },
      ]);
      setLastFailedMessage(null);
    } catch (error) {
      setErrorState(
        getErrorStateDetails(error) ?? {
          code: "UNKNOWN_ERROR",
          message: "",
          traceId: "",
        },
      );
    } finally {
      setIsSending(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const messageText = input;
    setInput("");
    void sendMessage(messageText);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      const messageText = input;
      setInput("");
      void sendMessage(messageText);
    }
  }

  return (
    <section className="flex min-h-[34rem] flex-1 flex-col rounded-md border border-border bg-card">
      <header className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
        <div className="flex min-w-0 items-center gap-2">
          <Sparkles className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
          <h2 className="truncate text-base font-semibold text-foreground">ssuAI Chat</h2>
        </div>
        {conversationId ? (
          <span className="shrink-0 rounded-md bg-muted px-2 py-1 text-xs font-medium text-muted-foreground">
            {conversationId}
          </span>
        ) : null}
      </header>

      <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 py-5">
        {messages.length === 0 ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
            <div className="flex max-w-xl flex-wrap justify-center gap-2">
              {SAMPLE_PROMPTS.map((prompt) => (
                <Button
                  key={prompt}
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => void sendMessage(prompt)}
                  disabled={isSending}
                  className="h-auto min-h-8 whitespace-normal text-left leading-5"
                >
                  {prompt}
                </Button>
              ))}
            </div>
          </div>
        ) : null}

        {messages.map((message) => (
          <MessageBubble key={message.id} role={message.role} content={message.content} />
        ))}

        {isSending ? <MessageBubble role="assistant" content="" isLoading /> : null}
        <div ref={bottomRef} />
      </div>

      {errorState ? (
        <div className="border-t border-border px-4 py-3">
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
            onRetry={lastFailedMessage ? () => void sendMessage(lastFailedMessage, false) : undefined}
          />
        </div>
      ) : null}

      <form onSubmit={handleSubmit} className="border-t border-border p-4">
        <div className="flex gap-2">
          <textarea
            value={input}
            onChange={(event) => setInput(event.target.value)}
            onKeyDown={handleKeyDown}
            maxLength={MAX_MESSAGE_LENGTH}
            disabled={isSending}
            rows={2}
            placeholder="메시지를 입력하세요"
            aria-label="채팅 메시지"
            className={cn(
              "min-h-12 flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm leading-6",
              "placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              "disabled:cursor-not-allowed disabled:opacity-50",
            )}
          />
          <Button
            type="submit"
            size="icon"
            className="h-12 w-12 shrink-0"
            disabled={!input.trim() || isSending}
            aria-label="메시지 보내기"
          >
            <Send className="h-5 w-5" aria-hidden="true" />
          </Button>
        </div>
        <div className="mt-2 text-right text-xs text-muted-foreground">
          {input.length}/{MAX_MESSAGE_LENGTH}
        </div>
      </form>
    </section>
  );
}
