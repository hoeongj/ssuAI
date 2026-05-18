import { fetchJson } from "./client";
import type { ChatRequest, ChatResponse } from "./types";

export function sendChatMessage(request: ChatRequest, accessToken?: string | null) {
  const headers = accessToken
    ? { Authorization: `Bearer ${accessToken}` }
    : undefined;

  return fetchJson<ChatResponse>("/api/chat", {
    method: "POST",
    headers,
    body: JSON.stringify(request),
  });
}
