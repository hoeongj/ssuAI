"use client";

import { useQuery } from "@tanstack/react-query";

import { getTodayMeal } from "@/lib/api/meal";
import * as utils from "@/lib/utils";

export function useTodayMeal() {
  return useQuery({
    queryKey: ["meal", "today"],
    queryFn: getTodayMeal,
    staleTime: utils.millisecondsUntilNextSeoulMidnight(),
    refetchInterval: () => utils.millisecondsUntilNextSeoulMidnight(),
  });
}
