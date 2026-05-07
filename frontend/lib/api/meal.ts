import { fetchJson } from "./client";
import type { MealResponse, WeeklyMealResponse } from "./types";

export function getTodayMeal() {
  return fetchJson<MealResponse>("/api/meals/today");
}

export function getWeeklyMeals(startDate?: string) {
  const params = new URLSearchParams();

  if (startDate) {
    params.set("startDate", startDate);
  }

  const query = params.toString();
  return fetchJson<WeeklyMealResponse>(`/api/meals/weekly${query ? `?${query}` : ""}`);
}
