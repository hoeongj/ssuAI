"use client";

import { ClipboardList, LogIn } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useLmsAssignments } from "@/hooks/useLmsAssignments";
import { useSaintAuth } from "@/hooks/useSaintAuth";
import { getSsoInitUrl } from "@/lib/api/auth";

function AssignmentsSkeleton() {
  return (
    <div className="space-y-2">
      {[1, 2, 3].map((i) => (
        <div key={i} className="rounded-md border border-border p-3">
          <Skeleton className="mb-1 h-4 w-2/3" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      ))}
    </div>
  );
}

function typeLabel(type: string) {
  if (type === "quiz") return "퀴즈";
  if (type === "assignment") return "과제";
  return type;
}

function formatDue(dueDate: string | null) {
  if (!dueDate) return null;
  const d = new Date(dueDate);
  if (isNaN(d.getTime())) return dueDate;
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function AssignmentsCard() {
  const { accessToken, isAuthenticated, isLoading: authLoading } = useSaintAuth();
  const { data, error, isLoading, refetch } = useLmsAssignments(accessToken);
  const errorState = getErrorStateDetails(error);

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>LMS 과제</CardTitle>
        <CardDescription>미제출 과제 및 퀴즈</CardDescription>
      </CardHeader>
      <CardContent>
        {authLoading && <AssignmentsSkeleton />}

        {!authLoading && !isAuthenticated && (
          <div className="flex flex-col items-start gap-3 rounded-md border border-border bg-muted/40 p-4">
            <p className="text-sm text-muted-foreground">
              LMS 과제는 로그인이 필요합니다.
            </p>
            <Button size="sm" onClick={() => (window.location.href = getSsoInitUrl())}>
              <LogIn className="h-4 w-4" aria-hidden="true" />
              SmartID 로그인
            </Button>
          </div>
        )}

        {isAuthenticated && isLoading && <AssignmentsSkeleton />}

        {errorState && (
          <ErrorState
            code={errorState.code}
            message={errorState.message}
            traceId={errorState.traceId}
            onRetry={() => void refetch()}
          />
        )}

        {data && !errorState && (
          data.items.length === 0 ? (
            <EmptyState
              icon={<ClipboardList className="h-6 w-6" aria-hidden="true" />}
              title="미제출 과제가 없습니다"
              description="현재 제출해야 할 과제나 퀴즈가 없어요."
            />
          ) : (
            <ul className="space-y-2">
              {data.items.map((item, i) => {
                const due = formatDue(item.dueDate);
                return (
                  <li key={i} className="rounded-md border border-border p-3">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-foreground">
                          {item.title}
                        </p>
                        <p className="text-xs text-muted-foreground">{item.courseName}</p>
                      </div>
                      <Badge variant="outline" className="shrink-0 text-xs">
                        {typeLabel(item.type)}
                      </Badge>
                    </div>
                    {due && (
                      <p className="mt-1 text-xs text-muted-foreground">마감: {due}</p>
                    )}
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
