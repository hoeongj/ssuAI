"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { searchLibraryBooks } from "@/lib/api/library";
import { FIVE_MINUTES_MS } from "@/lib/query";

export function useLibraryBookSearch() {
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState("");
  const [page, setPage] = useState(0);

  const result = useQuery({
    queryKey: ["library", "books", submittedQuery, page],
    queryFn: () => searchLibraryBooks(submittedQuery, page),
    enabled: submittedQuery.length > 0,
    staleTime: FIVE_MINUTES_MS,
  });

  function search(q: string) {
    const trimmed = q.trim();
    setSubmittedQuery(trimmed);
    setPage(0);
  }

  return { query, setQuery, search, page, setPage, submittedQuery, ...result };
}
