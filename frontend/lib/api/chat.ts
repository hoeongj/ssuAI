import { fetchJson } from "./client";
import type { ChatRequest, ChatResponse } from "./types";

export function sendChatMessage(request: ChatRequest) {
  return fetchJson<ChatResponse>("/api/chat", {
    method: "POST",
    body: JSON.stringify(request),
  });
}
