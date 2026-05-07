import { fetchJson } from "./client";
import type { WeeklyMealResponse } from "./types";

export function getDormThisWeekMeal() {
  return fetchJson<WeeklyMealResponse>("/api/dorm/meals/this-week");
}
