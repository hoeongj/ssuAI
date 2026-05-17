"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

import { LibraryAuthProvider } from "@/contexts/LibraryAuthContext";
import { SaintAuthProvider } from "@/hooks/useSaintAuth";
import { ApiError } from "@/lib/api/types";

function shouldRetry(failureCount: number, error: Error) {
  if (failureCount >= 1) {
    return false;
  }

  if (error instanceof ApiError && error.code === "VALIDATION_FAILED") {
    return false;
  }

  return true;
}

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: shouldRetry,
            refetchOnWindowFocus: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <SaintAuthProvider>
        <LibraryAuthProvider>{children}</LibraryAuthProvider>
      </SaintAuthProvider>
    </QueryClientProvider>
  );
}
