import { fetchJson } from "./client";
import type { GradesResponse, ScheduleResponse } from "./types";

function authHeader(token: string) {
  return { Authorization: `Bearer ${token}` };
}

export function getSaintSchedule(accessToken: string) {
  return fetchJson<ScheduleResponse>("/api/saint/schedule", {
    headers: authHeader(accessToken),
  });
}

export function getSaintGrades(accessToken: string) {
  return fetchJson<GradesResponse>("/api/saint/grades", {
    headers: authHeader(accessToken),
  });
}
