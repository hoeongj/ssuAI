import { AlertCircle, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface ErrorStateProps {
  code: string;
  message: string;
  traceId: string;
  onRetry?: () => void;
  className?: string;
}

export interface ErrorStateDetails {
  code: string;
  message: string;
  traceId: string;
}

export function getErrorStateDetails(error: unknown): ErrorStateDetails | null {
  if (!error) {
    return null;
  }

  if (error instanceof ApiError) {
    return {
      code: error.code,
      message: error.message,
      traceId: error.traceId,
    };
  }

  if (error instanceof Error) {
    return {
      code: "NETWORK_ERROR",
      message: error.message,
      traceId: "",
    };
  }

  return {
    code: "UNKNOWN_ERROR",
    message: "",
    traceId: "",
  };
}

function userMessage(code: string, message: string) {
  switch (code) {
    case "CONNECTOR_TIMEOUT":
      return "응답이 너무 오래 걸렸습니다. 잠시 후 다시 시도해주세요.";
    case "CONNECTOR_UNAVAILABLE":
      return "외부 서비스가 일시적으로 응답하지 않습니다.";
    case "VALIDATION_FAILED":
      return "입력값을 확인해주세요.";
    case "NETWORK_ERROR":
      return "백엔드에 연결할 수 없습니다. 서버 실행 상태를 확인해주세요.";
    default:
      return message || "알 수 없는 오류가 발생했습니다.";
  }
}

export function ErrorState({ code, message, traceId, onRetry, className }: ErrorStateProps) {
  const canRetry = code !== "VALIDATION_FAILED" && onRetry;

  return (
    <div
      className={cn(
        "flex flex-col gap-4 rounded-md border border-destructive/30 bg-destructive/5 p-4",
        className,
      )}
    >
      <div className="flex gap-3">
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-destructive" aria-hidden="true" />
        <div className="min-w-0 space-y-1">
          <p className="text-sm font-medium text-foreground">{userMessage(code, message)}</p>
          <p className="text-xs text-muted-foreground">{code}</p>
        </div>
      </div>
      {canRetry ? (
        <Button type="button" variant="outline" size="sm" className="w-fit" onClick={onRetry}>
          <RefreshCw className="h-4 w-4" aria-hidden="true" />
          다시 시도
        </Button>
      ) : null}
      {traceId ? <p className="text-xs font-mono text-muted-foreground">traceId: {traceId}</p> : null}
    </div>
  );
}
