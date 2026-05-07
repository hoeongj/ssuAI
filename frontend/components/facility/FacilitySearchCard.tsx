"use client";

import { Search, SearchX } from "lucide-react";
import { useEffect, useState } from "react";

import { FacilityResultItem } from "@/components/facility/FacilityResultItem";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useFacilitySearch } from "@/hooks/useFacilitySearch";
import { normalizeSearchQuery } from "@/lib/utils";

function FacilitySkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

export function FacilitySearchCard() {
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const normalizedQuery = normalizeSearchQuery(debouncedQuery);
  const { data, error, isFetching, refetch } = useFacilitySearch(debouncedQuery);
  const errorState = getErrorStateDetails(error);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query);
    }, 300);

    return () => window.clearTimeout(timer);
  }, [query]);

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>시설 검색</CardTitle>
        <CardDescription>식당, 카페, 매점, 출력소</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
          <Input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="검색어를 입력하세요"
            className="pl-9"
            aria-label="시설 검색어"
          />
        </div>

        {!normalizedQuery ? (
          <EmptyState
            icon={<Search className="h-6 w-6" aria-hidden="true" />}
            title="검색어를 입력하세요"
            description="시설 이름, 별칭, 위치로 검색할 수 있습니다."
          />
        ) : null}

        {normalizedQuery && isFetching && !data ? <FacilitySkeleton /> : null}

        {errorState ? (
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
            onRetry={() => void refetch()}
          />
        ) : null}

        {normalizedQuery && data && data.facilities.length === 0 ? (
          <EmptyState
            icon={<SearchX className="h-6 w-6" aria-hidden="true" />}
            title="검색 결과가 없습니다"
            description="다른 검색어를 입력해보세요."
          />
        ) : null}

        {normalizedQuery && data && data.facilities.length > 0 ? (
          <div className="space-y-3">
            {data.facilities.map((facility) => (
              <FacilityResultItem key={facility.id} facility={facility} />
            ))}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
