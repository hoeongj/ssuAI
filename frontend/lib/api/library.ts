import { fetchJson } from "./client";
import type { LibraryBookSearchResponse, LibraryFloorCode, LibraryLoansResponse, LibrarySeatStatusResponse } from "./types";

export function getLibrarySeatStatus(floor: LibraryFloorCode) {
  return fetchJson<LibrarySeatStatusResponse>(`/api/library/seats?floor=${floor}`);
}

export function captureLibrarySession(token: string) {
  return fetchJson<null>("/api/library/session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token }),
  });
}

export function searchLibraryBooks(query: string, page = 0, size = 10) {
  const params = new URLSearchParams({ query, page: String(page), size: String(size) });
  return fetchJson<LibraryBookSearchResponse>(`/api/library/books?${params.toString()}`);
}

export function getLibraryLoans() {
  return fetchJson<LibraryLoansResponse>("/api/library/loans", { credentials: "include" });
}
