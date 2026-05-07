"use client";

import { Building2 } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { WeeklyMealStrip } from "@/components/meal/WeeklyMealStrip";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useDormWeeklyMeal } from "@/hooks/useDormWeeklyMeal";
import { formatShortKoreanDate } from "@/lib/utils";

function DormWeeklySkeleton() {
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

export function DormWeeklyCard() {
  const { data, error, isLoading, refetch } = useDormWeeklyMeal();
  const errorState = getErrorStateDetails(error);
  const range = data ? `${formatShortKoreanDate(data.startDate)} - ${formatShortKoreanDate(data.endDate)}` : "이번 주";

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>기숙사 주간 식단</CardTitle>
        <CardDescription>{range}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? <DormWeeklySkeleton /> : null}
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
            emptyTitle="기숙사 식단이 없습니다"
            emptyDescription="이번 주 공개된 기숙사 메뉴가 아직 없습니다."
          />
        ) : null}
        {data && data.days.length === 0 ? (
          <EmptyState
            icon={<Building2 className="h-6 w-6" aria-hidden="true" />}
            title="기숙사 식단이 없습니다"
            description="이번 주 공개된 기숙사 메뉴가 아직 없습니다."
          />
        ) : null}
      </CardContent>
    </Card>
  );
}
