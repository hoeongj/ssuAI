import { fetchJson } from "./client";
import type { CampusFacilityListResponse } from "./types";

export function searchFacilities(query: string) {
  const params = new URLSearchParams();
  const normalizedQuery = query.trim();

  if (normalizedQuery) {
    params.set("query", normalizedQuery);
  }

  const search = params.toString();
  return fetchJson<CampusFacilityListResponse>(
    `/api/campus/facilities${search ? `?${search}` : ""}`,
  );
}
