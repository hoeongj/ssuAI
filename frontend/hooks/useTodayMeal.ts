"use client";

import { useQuery } from "@tanstack/react-query";

import { getTodayMeal } from "@/lib/api/meal";
import { millisecondsUntilNextSeoulMidnight } from "@/lib/utils";

export function useTodayMeal() {
  return useQuery({
    queryKey: ["meal", "today"],
    queryFn: getTodayMeal,
    staleTime: millisecondsUntilNextSeoulMidnight(),
  });
}
