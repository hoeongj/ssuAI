import { ApiError, type ApiErrorBody, type ApiResponse } from "./types";

const rawBaseUrl = process.env.NEXT_PUBLIC_SSUAI_API_BASE;

if (!rawBaseUrl) {
  throw new Error("NEXT_PUBLIC_SSUAI_API_BASE is required. Set it in frontend/.env.local.");
}

const apiBaseUrl = rawBaseUrl.replace(/\/$/, "");

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    isRecord(value) &&
    typeof value.code === "string" &&
    typeof value.message === "string"
  );
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (!isRecord(value)) {
    return false;
  }

  const hasData = "data" in value;
  const hasError = "error" in value;
  const hasTraceId = typeof value.traceId === "string";
  const error = value.error;

  return hasData && hasError && hasTraceId && (error === null || isApiErrorBody(error));
}

async function parseEnvelope(response: Response) {
  try {
    const body = (await response.json()) as unknown;
    return isApiResponse(body) ? body : null;
  } catch {
    return null;
  }
}

export async function fetchJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!path.startsWith("/api/")) {
    throw new Error(`API path must start with /api/: ${path}`);
  }

  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...init.headers,
    },
  });
  const envelope = await parseEnvelope(response);

  if (!response.ok) {
    if (envelope?.error) {
      throw new ApiError(
        envelope.error.code,
        envelope.error.message,
        envelope.traceId,
        response.status,
      );
    }

    throw new ApiError(
      `HTTP_${response.status}`,
      response.statusText || "HTTP request failed",
      "",
      response.status,
    );
  }

  if (envelope?.error) {
    throw new ApiError(
      envelope.error.code,
      envelope.error.message,
      envelope.traceId,
      response.status,
    );
  }

  if (!envelope) {
    throw new ApiError("INVALID_ENVELOPE", "Backend returned an invalid response envelope.", "", response.status);
  }

  return envelope.data as T;
}
