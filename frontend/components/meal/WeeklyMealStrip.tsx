"use client";

import { useMemo, useState } from "react";
import { CalendarDays, Utensils } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import type { MealResponse } from "@/lib/api/types";
import { cn, formatShortKoreanDate, getSeoulDateString, mealTypeLabel } from "@/lib/utils";

const mealTypeOrder = ["BREAKFAST", "LUNCH", "DINNER"];

interface WeeklyMealStripProps {
  days: MealResponse[];
  emptyTitle: string;
  emptyDescription: string;
}

function sortMeals(day: MealResponse) {
  return [...day.meals].sort(
    (left, right) => mealTypeOrder.indexOf(left.type) - mealTypeOrder.indexOf(right.type),
  );
}

export function WeeklyMealStrip({ days, emptyTitle, emptyDescription }: WeeklyMealStripProps) {
  const today = getSeoulDateString();
  const defaultDate = useMemo(
    () => days.find((day) => day.date === today)?.date ?? days[0]?.date ?? "",
    [days, today],
  );
  const [selectedDate, setSelectedDate] = useState(defaultDate);
  const selectedDay = days.find((day) => day.date === (selectedDate || defaultDate));

  if (days.length === 0) {
    return (
      <EmptyState
        icon={<CalendarDays className="h-6 w-6" aria-hidden="true" />}
        title={emptyTitle}
        description={emptyDescription}
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-5">
        {days.map((day) => {
          const active = day.date === selectedDay?.date;
          return (
            <Button
              key={day.date}
              type="button"
              variant={active ? "default" : "outline"}
              className={cn(
                "h-auto min-h-24 flex-col items-start justify-start px-3 py-3 text-left",
                active ? "shadow-sm" : "bg-card",
              )}
              onClick={() => setSelectedDate(day.date)}
              onFocus={() => setSelectedDate(day.date)}
              onMouseEnter={() => setSelectedDate(day.date)}
            >
              <span className="text-sm font-semibold">{formatShortKoreanDate(day.date)}</span>
              <span className="flex flex-wrap gap-1 pt-2">
                {sortMeals(day).length > 0 ? (
                  sortMeals(day).map((meal) => (
                    <span
                      key={`${day.date}-${meal.restaurant}-${meal.type}-${meal.corner}`}
                      className={cn(
                        "rounded-sm border px-1.5 py-0.5 text-xs",
                        active
                          ? "border-primary-foreground/35 text-primary-foreground"
                          : "border-border text-muted-foreground",
                      )}
                    >
                      {mealTypeLabel(meal.type)}
                    </span>
                  ))
                ) : (
                  <span className={active ? "text-primary-foreground/80" : "text-muted-foreground"}>
                    메뉴 없음
                  </span>
                )}
              </span>
            </Button>
          );
        })}
      </div>

      {selectedDay && selectedDay.meals.length > 0 ? (
        <div className="rounded-md border border-border p-4">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            <h4 className="text-sm font-semibold text-foreground">
              {formatShortKoreanDate(selectedDay.date)}
            </h4>
            {selectedDay.closures.map((closure) => (
              <Badge key={`${closure.restaurant}-${closure.reason}`} variant="outline">
                휴무: {closure.restaurant} · {closure.reason}
              </Badge>
            ))}
          </div>
          <div className="space-y-4">
            {sortMeals(selectedDay).map((meal) => (
              <section key={`${meal.restaurant}-${meal.type}-${meal.corner}`} className="space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge variant="secondary">{mealTypeLabel(meal.type)}</Badge>
                  <span className="text-sm font-medium text-foreground">{meal.restaurant}</span>
                  <span className="text-xs text-muted-foreground">{meal.corner}</span>
                </div>
                <p className="text-sm leading-6 text-foreground">
                  {meal.menu.length > 0 ? meal.menu.join(", ") : "메뉴 준비 중"}
                </p>
              </section>
            ))}
          </div>
        </div>
      ) : (
        <EmptyState
          icon={<Utensils className="h-6 w-6" aria-hidden="true" />}
          title="선택한 날짜의 메뉴가 없습니다"
          description="다른 날짜를 선택해보세요."
        />
      )}
    </div>
  );
}
