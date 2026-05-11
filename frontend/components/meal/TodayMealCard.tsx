"use client";

import { Utensils } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useTodayMeal } from "@/hooks/useTodayMeal";
import type { MealItem } from "@/lib/api/types";
import { formatKoreanDate, mealTypeLabel } from "@/lib/utils";

function groupByRestaurant(meals: MealItem[]) {
  return meals.reduce<Record<string, MealItem[]>>((groups, meal) => {
    (groups[meal.restaurant] ??= []).push(meal);
    return groups;
  }, {});
}

function TodayMealSkeleton() {
  return (
    <div className="space-y-4">
      <Skeleton className="h-5 w-36" />
      <div className="space-y-3">
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    </div>
  );
}

export function TodayMealCard() {
  const { data, error, isLoading, refetch } = useTodayMeal();
  const errorState = getErrorStateDetails(error);
  const groupedMeals = data ? groupByRestaurant(data.meals) : {};
  const hasMeals = data ? data.meals.length > 0 : false;
  const hasClosures = data ? data.closures.length > 0 : false;

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>오늘의 학식</CardTitle>
        <CardDescription>
          {data ? formatKoreanDate(data.date) : "학생식당 메뉴"}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? <TodayMealSkeleton /> : null}

        {errorState ? (
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
            onRetry={() => void refetch()}
          />
        ) : null}

        {data && !hasMeals && !hasClosures ? (
          <EmptyState
            icon={<Utensils className="h-6 w-6" aria-hidden="true" />}
            title="등록된 메뉴가 없습니다"
            description="오늘 공개된 학식 정보가 아직 없습니다."
          />
        ) : null}

        {data && (hasMeals || hasClosures) ? (
          <div className="space-y-4">
            {hasClosures ? (
              <div className="flex flex-wrap gap-2">
                {data.closures.map((closure) => (
                  <Badge key={`${closure.restaurant}-${closure.reason}`} variant="outline">
                    휴무: {closure.restaurant} · {closure.reason}
                  </Badge>
                ))}
              </div>
            ) : null}

            <div className="space-y-4">
              {Object.entries(groupedMeals).map(([restaurant, meals]) => (
                <section key={restaurant} className="rounded-md border border-border p-4">
                  <h4 className="text-sm font-semibold text-foreground">{restaurant}</h4>
                  <div className="mt-3 space-y-3">
                    {meals.map((meal) => (
                      <div
                        key={`${meal.restaurant}-${meal.type}-${meal.corner}`}
                        className="grid gap-2 sm:grid-cols-[5rem_1fr]"
                      >
                        <div className="flex items-start gap-2">
                          <Badge variant="secondary">{mealTypeLabel(meal.type)}</Badge>
                          <span className="text-xs text-muted-foreground sm:hidden">{meal.corner}</span>
                        </div>
                        <div className="min-w-0">
                          <p className="hidden text-xs font-medium text-muted-foreground sm:block">
                            {meal.corner}
                          </p>
                          <p className="mt-1 text-sm leading-6 text-foreground">
                            {meal.menu.length > 0 ? meal.menu.join(", ") : "메뉴 준비 중"}
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
