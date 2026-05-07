"use client";

import { useQuery } from "@tanstack/react-query";

import { getDormThisWeekMeal } from "@/lib/api/dorm";

const FIVE_MINUTES = 5 * 60 * 1000;

export function useDormWeeklyMeal() {
  return useQuery({
    queryKey: ["dorm", "weekly"],
    queryFn: getDormThisWeekMeal,
    staleTime: FIVE_MINUTES,
  });
}
