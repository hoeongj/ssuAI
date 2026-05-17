"use client";

import { Search } from "lucide-react";
import { FormEvent } from "react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState, getErrorStateDetails } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useLibraryBookSearch } from "@/hooks/useLibraryBookSearch";
import type { BookStatus } from "@/lib/api/types";

function statusLabel(status: BookStatus) {
  if (status === "AVAILABLE") return { text: "대출가능", variant: "default" as const };
  if (status === "CHECKED_OUT") return { text: "대출중", variant: "secondary" as const };
  return { text: "알수없음", variant: "outline" as const };
}

function BookSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <div key={i} className="rounded-md border border-border p-3">
          <Skeleton className="mb-1 h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      ))}
    </div>
  );
}

export function LibraryBookSearchCard() {
  const { query, setQuery, search, submittedQuery, data, error, isLoading } =
    useLibraryBookSearch();
  const errorState = getErrorStateDetails(error);

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    search(query);
  }

  return (
    <Card className="h-full">
      <CardHeader>
        <CardTitle>도서 검색</CardTitle>
        <CardDescription>중앙도서관 소장 도서 검색</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <form onSubmit={handleSubmit} className="flex gap-2">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="제목, 저자 검색"
            aria-label="도서 검색어"
          />
          <Button type="submit" size="sm" disabled={!query.trim()}>
            <Search className="h-4 w-4" aria-hidden="true" />
          </Button>
        </form>

        {isLoading && <BookSkeleton />}

        {errorState && (
          <ErrorState code={errorState.code} message={errorState.message} traceId={errorState.traceId} />
        )}

        {data && !errorState && (
          <>
            <p className="text-xs text-muted-foreground">
              총 {data.total}건 중 {data.items.length}건 표시
            </p>
            {data.items.length === 0 ? (
              <EmptyState
                icon={<Search className="h-6 w-6" aria-hidden="true" />}
                title="검색 결과가 없습니다"
                description={`"${submittedQuery}" 에 대한 결과가 없어요.`}
              />
            ) : (
              <ul className="space-y-2">
                {data.items.map((book) => {
                  const { text, variant } = statusLabel(book.status);
                  return (
                    <li key={book.id} className="rounded-md border border-border p-3">
                      <div className="flex items-start justify-between gap-2">
                        <p className="text-sm font-medium leading-snug text-foreground">
                          {book.title}
                        </p>
                        <Badge variant={variant} className="shrink-0 text-xs">
                          {text}
                        </Badge>
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">
                        {book.author}
                        {book.location ? ` · ${book.location}` : ""}
                        {book.callNumber ? ` · ${book.callNumber}` : ""}
                      </p>
                    </li>
                  );
                })}
              </ul>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
