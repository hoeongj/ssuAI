"use client";

import { useQuery } from "@tanstack/react-query";

import { getLibrarySeatStatus } from "@/lib/api/library";
import type { LibraryFloorCode } from "@/lib/api/types";
import { THIRTY_SECONDS_MS } from "@/lib/query";

export function useLibrarySeatStatus(floor: LibraryFloorCode) {
  return useQuery({
    queryKey: ["library", "seats", floor],
    queryFn: () => getLibrarySeatStatus(floor),
    staleTime: THIRTY_SECONDS_MS,
    refetchOnWindowFocus: true,
  });
}
