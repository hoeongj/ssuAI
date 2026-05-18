import { fetchJson } from "./client";
import type { LibraryBookSearchResponse, LibraryFloorCode, LibraryLoansResponse, LibrarySeatStatusResponse } from "./types";

export interface McpLibraryCallbackRequest {
  state: string;
  loginId: string;
  password: string;
}

export function completeMcpLibraryAuth(req: McpLibraryCallbackRequest) {
  return fetchJson<null>("/api/mcp/auth/library/callback", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export function getLibrarySeatStatus(floor: LibraryFloorCode) {
  return fetchJson<LibrarySeatStatusResponse>(`/api/library/seats?floor=${floor}`, {
    credentials: "include",
  });
}

export function captureLibrarySession(token: string) {
  return fetchJson<null>("/api/library/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ token }),
  });
}

export function loginLibrary(loginId: string, password: string) {
  return fetchJson<null>("/api/library/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ loginId, password }),
  });
}

export function searchLibraryBooks(query: string, page = 0, size = 10) {
  const params = new URLSearchParams({ query, page: String(page), size: String(size) });
  return fetchJson<LibraryBookSearchResponse>(`/api/library/books?${params.toString()}`);
}

export function getLibraryLoans() {
  return fetchJson<LibraryLoansResponse>("/api/library/loans", { credentials: "include" });
}

export function logoutLibrary() {
  return fetchJson<null>("/api/library/session", {
    method: "DELETE",
    credentials: "include",
  });
}
