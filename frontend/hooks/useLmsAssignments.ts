"use client";

import { useQuery } from "@tanstack/react-query";

import { getLmsAssignments } from "@/lib/api/lms";
import { FIVE_MINUTES_MS } from "@/lib/query";

export function useLmsAssignments(accessToken: string | null) {
  return useQuery({
    queryKey: ["lms", "assignments", accessToken],
    queryFn: () => getLmsAssignments(accessToken!),
    enabled: !!accessToken,
    staleTime: FIVE_MINUTES_MS,
  });
}
