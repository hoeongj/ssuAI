"use client";

import { CalendarDays } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { WeeklyMealSkeleton } from "@/components/meal/WeeklyMealSkeleton";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { WeeklyMealStrip } from "@/components/meal/WeeklyMealStrip";
import { useWeeklyMeals } from "@/hooks/useWeeklyMeals";
import { formatShortKoreanDate } from "@/lib/utils";

export function WeeklyMealCard() {
  const { data, error, isLoading, refetch } = useWeeklyMeals();
  const errorState = getErrorStateDetails(error);
  const range = data ? `${formatShortKoreanDate(data.startDate)} - ${formatShortKoreanDate(data.endDate)}` : "이번 주";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>학식 주간 식단</CardTitle>
        <CardDescription>{range}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? <WeeklyMealSkeleton /> : null}
        {errorState ? (
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
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
