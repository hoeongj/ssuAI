"use client";

import { useQuery } from "@tanstack/react-query";

import { getWeeklyMeals } from "@/lib/api/meal";

const FIVE_MINUTES = 5 * 60 * 1000;

export function useWeeklyMeals(startDate?: string) {
  return useQuery({
    queryKey: ["meal", "weekly", startDate ?? "current-week"],
    queryFn: () => getWeeklyMeals(startDate),
    staleTime: FIVE_MINUTES,
  });
}
