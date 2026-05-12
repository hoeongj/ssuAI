import { AlertCircle, RefreshCw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export { getErrorStateDetails, type ErrorStateDetails } from "./error-state.utils";

interface ErrorStateProps {
  code: string;
  message: string;
  traceId: string;
  onRetry?: () => void;
  className?: string;
}

function userMessage(code: string, message: string) {
  switch (code) {
    case "CONNECTOR_TIMEOUT":
      return "응답이 너무 오래 걸렸습니다. 잠시 후 다시 시도해주세요.";
    case "CONNECTOR_UNAVAILABLE":
      return "외부 서비스가 일시적으로 응답하지 않습니다.";
    case "CHAT_UNAVAILABLE":
      return "지금은 AI 응답이 잠시 어려워요. 조금 있다가 다시 물어봐 주세요.";
    case "VALIDATION_FAILED":
      return "입력값을 확인해주세요.";
    case "INVALID_ENVELOPE":
      return "백엔드 응답 형식이 올바르지 않습니다.";
    case "NETWORK_ERROR":
      return "백엔드에 연결할 수 없습니다. 서버 실행 상태를 확인해주세요.";
    case "CONFIG_ERROR":
      return message || "프론트엔드 환경 설정이 누락되었습니다.";
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
