"use client";

import { useQuery } from "@tanstack/react-query";

import { searchFacilities } from "@/lib/api/facility";
import { normalizeSearchQuery } from "@/lib/utils";

const FIVE_MINUTES = 5 * 60 * 1000;

export function useFacilitySearch(query: string) {
  const normalizedQuery = normalizeSearchQuery(query);

  return useQuery({
    queryKey: ["facility", "search", normalizedQuery],
    queryFn: () => searchFacilities(normalizedQuery),
    enabled: normalizedQuery.length > 0,
    staleTime: FIVE_MINUTES,
  });
}
