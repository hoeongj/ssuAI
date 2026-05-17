import { fetchJson } from "./client";
import type { AssignmentsResponse } from "./types";

export function getLmsAssignments(accessToken: string) {
  return fetchJson<AssignmentsResponse>("/api/lms/assignments", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
}
