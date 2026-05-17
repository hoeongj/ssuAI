"use client";

import { useQuery } from "@tanstack/react-query";

import { getLibraryLoans } from "@/lib/api/library";
import { FIVE_MINUTES_MS } from "@/lib/query";

export function useLibraryLoans() {
  return useQuery({
    queryKey: ["library", "loans"],
    queryFn: () => getLibraryLoans(),
    staleTime: FIVE_MINUTES_MS,
  });
}
