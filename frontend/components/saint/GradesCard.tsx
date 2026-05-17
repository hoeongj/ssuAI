"use client";

import { GraduationCap, LogIn } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useSaintAuth } from "@/hooks/useSaintAuth";
import { useSaintGrades } from "@/hooks/useSaintGrades";
import { getSsoInitUrl } from "@/lib/api/auth";

function GradesSkeleton() {
  return (
    <div className="space-y-2">
      <Skeleton className="h-16 w-full" />
      {[1, 2, 3].map((i) => (
        <Skeleton key={i} className="h-8 w-full" />
      ))}
    </div>
  );
}

export function GradesCard() {
  const { accessToken, isAuthenticated, isLoading: authLoading } = useSaintAuth();
  const { data, error, isLoading } = useSaintGrades(accessToken);
  const errorState = getErrorStateDetails(error);

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>내 성적</CardTitle>
        <CardDescription>u-SAINT 누적 성적</CardDescription>
      </CardHeader>
      <CardContent>
        {authLoading && <GradesSkeleton />}

        {!authLoading && !isAuthenticated && (
          <div className="flex flex-col items-start gap-3 rounded-md border border-border bg-muted/40 p-4">
            <p className="text-sm text-muted-foreground">
              성적은 u-SAINT 로그인이 필요합니다.
            </p>
            <Button size="sm" onClick={() => (window.location.href = getSsoInitUrl())}>
              <LogIn className="h-4 w-4" aria-hidden="true" />
              SmartID 로그인
            </Button>
          </div>
        )}

        {isAuthenticated && isLoading && <GradesSkeleton />}

        {errorState && (
          <ErrorState code={errorState.code} message={errorState.message} traceId={errorState.traceId} />
        )}

        {data && !errorState && (
          <>
            <div className="mb-4 rounded-md border border-border bg-muted/40 p-4">
              <p className="text-xs text-muted-foreground">누적 평점 (학업)</p>
              <p className="mt-1 text-3xl font-bold tabular-nums text-foreground">
                {data.academicRecord.gpa.toFixed(2)}
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">
                취득학점 {data.academicRecord.earnedCredits} · 신청학점{" "}
                {data.academicRecord.requestedCredits}
              </p>
            </div>

            {data.history.length === 0 ? (
              <EmptyState
                icon={<GraduationCap className="h-6 w-6" aria-hidden="true" />}
                title="성적 내역이 없습니다"
                description="학기별 성적 정보를 가져올 수 없어요."
              />
            ) : (
              <ul className="space-y-1">
                {data.history.map((row, i) => (
                  <li
                    key={i}
                    className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
                  >
                    <span className="text-muted-foreground">
                      {row.year}년 {row.term}학기
                    </span>
                    <div className="flex items-center gap-3 tabular-nums">
                      <span className="text-xs text-muted-foreground">
                        {row.earnedCredits}학점
                      </span>
                      <span className="font-medium text-foreground">{row.gpa.toFixed(2)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
