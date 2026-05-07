"use client";

import { CalendarDays } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { WeeklyMealStrip } from "@/components/meal/WeeklyMealStrip";
import { useWeeklyMeals } from "@/hooks/useWeeklyMeals";
import { ApiError } from "@/lib/api/types";
import { formatShortKoreanDate } from "@/lib/utils";

function WeeklySkeleton() {
  return (
    <div className="space-y-4">
      <div className="grid gap-2 sm:grid-cols-5">
        {Array.from({ length: 5 }).map((_, index) => (
          <Skeleton key={index} className="h-24 w-full" />
        ))}
      </div>
      <Skeleton className="h-36 w-full" />
    </div>
  );
}

export function WeeklyMealCard() {
  const { data, error, isLoading, refetch } = useWeeklyMeals();
  const apiError = error instanceof ApiError ? error : null;
  const range = data ? `${formatShortKoreanDate(data.startDate)} - ${formatShortKoreanDate(data.endDate)}` : "이번 주";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>학식 주간 식단</CardTitle>
        <CardDescription>{range}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? <WeeklySkeleton /> : null}
        {apiError ? (
          <ErrorState
            code={apiError.code}
            message={apiError.message}
            traceId={apiError.traceId}
            onRetry={() => void refetch()}
          />
        ) : null}
        {data && data.days.length > 0 ? (
          <WeeklyMealStrip
            days={data.days}
            emptyTitle="주간 식단이 없습니다"
            emptyDescription="이번 주 공개된 학식 메뉴가 아직 없습니다."
          />
        ) : null}
        {data && data.days.length === 0 ? (
          <EmptyState
            icon={<CalendarDays className="h-6 w-6" aria-hidden="true" />}
            title="주간 식단이 없습니다"
            description="이번 주 공개된 학식 메뉴가 아직 없습니다."
          />
        ) : null}
      </CardContent>
    </Card>
  );
}
