import { fetchJson } from "./client";
import type { LibraryFloorCode, LibrarySeatStatusResponse } from "./types";

export function getLibrarySeatStatus(floor: LibraryFloorCode) {
  return fetchJson<LibrarySeatStatusResponse>(`/api/library/seats?floor=${floor}`);
}
