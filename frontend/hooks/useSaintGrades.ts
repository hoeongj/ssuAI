"use client";

import { useQuery } from "@tanstack/react-query";

import { getSaintGrades } from "@/lib/api/saint";
import { FIVE_MINUTES_MS } from "@/lib/query";

export function useSaintGrades(accessToken: string | null) {
  return useQuery({
    queryKey: ["saint", "grades", accessToken],
    queryFn: () => getSaintGrades(accessToken!),
    enabled: !!accessToken,
    staleTime: FIVE_MINUTES_MS,
  });
}
