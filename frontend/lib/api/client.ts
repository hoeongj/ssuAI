import { ApiError, type ApiErrorBody, type ApiResponse } from "./types";

export function getApiBaseUrl() {
  if (typeof window !== "undefined") {
    return "";
  }

  const rawBaseUrl = process.env.NEXT_PUBLIC_SSUAI_API_BASE?.trim();
  if (!rawBaseUrl) {
    return "";
  }
  return rawBaseUrl.replace(/\/$/, "");
}

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

  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  if (init.body !== undefined && init.body !== null && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    ...init,
    headers,
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
