import { ApiError } from "../../lib/api/types";

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
