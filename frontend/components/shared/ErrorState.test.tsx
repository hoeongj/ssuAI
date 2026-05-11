import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ErrorState } from "./ErrorState";

describe("ErrorState", () => {
  it.each([
    ["CONNECTOR_TIMEOUT", "응답이 너무 오래 걸렸습니다. 잠시 후 다시 시도해주세요."],
    ["CONNECTOR_UNAVAILABLE", "외부 서비스가 일시적으로 응답하지 않습니다."],
    ["VALIDATION_FAILED", "입력값을 확인해주세요."],
    ["INVALID_ENVELOPE", "백엔드 응답 형식이 올바르지 않습니다."],
    ["NETWORK_ERROR", "백엔드에 연결할 수 없습니다. 서버 실행 상태를 확인해주세요."],
    ["CONFIG_ERROR", "프론트엔드 환경 설정이 누락되었습니다."],
    ["UNKNOWN_ERROR", "알 수 없는 오류가 발생했습니다."],
  ])("renders the user-facing message for %s", (code, expectedMessage) => {
    render(<ErrorState code={code} message="" traceId="trace-1" />);

    expect(screen.getByText(expectedMessage)).toBeInTheDocument();
    expect(screen.getByText(code)).toBeInTheDocument();
    expect(screen.getByText("traceId: trace-1")).toBeInTheDocument();
  });

  it("calls onRetry from the retry button when retry is allowed", async () => {
    const onRetry = vi.fn();

    render(<ErrorState code="NETWORK_ERROR" message="" traceId="" onRetry={onRetry} />);
    screen.getByRole("button", { name: "다시 시도" }).click();

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("does not render retry for validation errors", () => {
    render(<ErrorState code="VALIDATION_FAILED" message="" traceId="" onRetry={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });
});
