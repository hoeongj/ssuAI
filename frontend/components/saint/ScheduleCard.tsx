"use client";

import { CalendarDays, LogIn } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useSaintAuth } from "@/hooks/useSaintAuth";
import { useSaintSchedule } from "@/hooks/useSaintSchedule";
import { getSsoInitUrl } from "@/lib/api/auth";
import type { ScheduleEntry } from "@/lib/api/types";

const DAY_LABELS = ["", "월", "화", "수", "목", "금", "토"];

function groupByDay(entries: ScheduleEntry[]): Map<number, ScheduleEntry[]> {
  const map = new Map<number, ScheduleEntry[]>();
  for (const e of entries) {
    const arr = map.get(e.dayOfWeek) ?? [];
    arr.push(e);
    map.set(e.dayOfWeek, arr);
  }
  return map;
}

function ScheduleSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

export function ScheduleCard() {
  const { accessToken, isAuthenticated, isLoading: authLoading } = useSaintAuth();
  const { data, error, isLoading } = useSaintSchedule(accessToken);
  const errorState = getErrorStateDetails(error);

  const currentTerm = data?.terms.find(
    (t) => t.year === data.currentYear && t.term === data.currentTerm,
  );

  const byDay = currentTerm ? groupByDay(currentTerm.entries) : new Map();

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>내 시간표</CardTitle>
        <CardDescription>
          {data ? `${data.currentYear}년 ${data.currentTerm}학기` : "u-SAINT 시간표"}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {authLoading && <ScheduleSkeleton />}

        {!authLoading && !isAuthenticated && (
          <div className="flex flex-col items-start gap-3 rounded-md border border-border bg-muted/40 p-4">
            <p className="text-sm text-muted-foreground">
              시간표는 u-SAINT 로그인이 필요합니다.
            </p>
            <Button size="sm" onClick={() => (window.location.href = getSsoInitUrl())}>
              <LogIn className="h-4 w-4" aria-hidden="true" />
              SmartID 로그인
            </Button>
          </div>
        )}

        {isAuthenticated && isLoading && <ScheduleSkeleton />}

        {errorState && (
          <ErrorState code={errorState.code} message={errorState.message} traceId={errorState.traceId} />
        )}

        {currentTerm && !errorState && (
          byDay.size === 0 ? (
            <EmptyState
              icon={<CalendarDays className="h-6 w-6" aria-hidden="true" />}
              title="이번 학기 강의가 없습니다"
              description="현재 학기 등록 강의를 찾을 수 없어요."
            />
          ) : (
            <ul className="space-y-3">
              {[1, 2, 3, 4, 5].map((day) => {
                const entries = byDay.get(day);
                if (!entries) return null;
                return (
                  <li key={day}>
                    <p className="mb-1 text-xs font-semibold text-muted-foreground">
                      {DAY_LABELS[day]}요일
                    </p>
                    <ul className="space-y-1">
                      {entries
                        .sort((a: ScheduleEntry, b: ScheduleEntry) => a.period - b.period)
                        .map((e: ScheduleEntry) => (
                          <li
                            key={`${e.dayOfWeek}-${e.period}`}
                            className="flex items-baseline justify-between rounded-md border border-border px-3 py-2"
                          >
                            <div>
                              <span className="text-sm font-medium text-foreground">{e.course}</span>
                              <span className="ml-2 text-xs text-muted-foreground">{e.room}</span>
                            </div>
                            <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                              {e.timeRange}
                            </span>
                          </li>
                        ))}
                    </ul>
                  </li>
                );
              })}
            </ul>
          )
        )}
      </CardContent>
    </Card>
  );
}
