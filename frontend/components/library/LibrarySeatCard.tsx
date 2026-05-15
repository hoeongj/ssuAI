"use client";

import { BookOpen } from "lucide-react";
import { useState } from "react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useLibrarySeatStatus } from "@/hooks/useLibrarySeatStatus";
import type { LibraryFloorCode } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const FLOOR_OPTIONS: ReadonlyArray<{ code: LibraryFloorCode; label: string }> = [
  { code: -1, label: "B1" },
  { code: 1, label: "1층" },
  { code: 2, label: "2층" },
  { code: 3, label: "3층" },
  { code: 4, label: "4층" },
  { code: 5, label: "5층" },
  { code: 6, label: "6층" },
];

const DEFAULT_FLOOR: LibraryFloorCode = 4;

function LibrarySeatSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-7 w-32" />
      <Skeleton className="h-3 w-full" />
      <div className="space-y-3">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-12 w-full" />
      </div>
    </div>
  );
}

export function LibrarySeatCard() {
  const [floor, setFloor] = useState<LibraryFloorCode>(DEFAULT_FLOOR);
  const { data, error, isLoading, isFetching, refetch } = useLibrarySeatStatus(floor);
  const errorState = getErrorStateDetails(error);
  const usagePercent =
    data && data.totalSeats > 0
      ? Math.round(((data.totalSeats - data.availableSeats) / data.totalSeats) * 100)
      : 0;

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>도서관 좌석</CardTitle>
        <CardDescription>중앙도서관 층별 현재 잔여 좌석 (30초 자동 갱신)</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="도서관 층 선택">
          {FLOOR_OPTIONS.map((option) => {
            const isActive = option.code === floor;
            return (
              <Button
                key={option.code}
                type="button"
                variant={isActive ? "default" : "outline"}
                size="sm"
                role="tab"
                aria-selected={isActive}
                onClick={() => setFloor(option.code)}
              >
                {option.label}
              </Button>
            );
          })}
        </div>

        {isLoading ? <LibrarySeatSkeleton /> : null}

        {errorState ? (
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
            onRetry={() => void refetch()}
          />
        ) : null}

        {data && !errorState ? (
          <div className="space-y-4">
            <div>
              <div className="flex items-baseline justify-between gap-3">
                <p className="text-sm text-muted-foreground">{data.floorLabel}</p>
                <p className="text-sm tabular-nums text-muted-foreground">
                  <span className="font-medium text-foreground">{data.availableSeats}</span>
                  <span> / {data.totalSeats}석 이용 가능</span>
                </p>
              </div>
              <div
                className="mt-2 h-2 w-full overflow-hidden rounded-full bg-muted"
                role="progressbar"
                aria-valuenow={usagePercent}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label={`${data.floorLabel} 좌석 사용률 ${usagePercent}%`}
              >
                <div
                  className={cn(
                    "h-full transition-all",
                    usagePercent >= 90
                      ? "bg-destructive"
                      : usagePercent >= 70
                        ? "bg-amber-500"
                        : "bg-emerald-500",
                  )}
                  style={{ width: `${usagePercent}%` }}
                />
              </div>
              <div className="mt-2 flex flex-wrap gap-2 text-xs">
                <Badge variant="secondary">예약 {data.reservedSeats}</Badge>
                {data.outOfServiceSeats > 0 ? (
                  <Badge variant="outline">사용 불가 {data.outOfServiceSeats}</Badge>
                ) : null}
                {isFetching && !isLoading ? (
                  <span className="text-muted-foreground">갱신 중…</span>
                ) : null}
              </div>
            </div>

            {data.zones.length > 0 ? (
              <ul className="space-y-2" aria-label="구역별 좌석 현황">
                {data.zones.map((zone) => (
                  <li
                    key={zone.label}
                    className="rounded-md border border-border bg-card p-3"
                  >
                    <div className="flex items-baseline justify-between gap-3">
                      <p className="text-sm font-medium text-foreground">{zone.label}</p>
                      <p className="text-xs tabular-nums text-muted-foreground">
                        <span className="font-medium text-foreground">{zone.available}</span>
                        <span> / {zone.total}석</span>
                      </p>
                    </div>
                    {zone.seatIds.length > 0 ? (
                      <p className="mt-1 truncate text-xs text-muted-foreground">
                        빈 자리: {zone.seatIds.join(", ")}
                      </p>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                icon={<BookOpen className="h-6 w-6" aria-hidden="true" />}
                title="구역 정보가 없습니다"
                description="이 층은 구역별 분류가 제공되지 않습니다."
              />
            )}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
