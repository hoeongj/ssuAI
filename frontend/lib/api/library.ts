import { fetchJson } from "./client";
import type { LibraryFloorCode, LibrarySeatStatusResponse } from "./types";

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
