import { describe, expect, it } from "vitest";

import { ApiError } from "../../lib/api/types";

import { getErrorStateDetails } from "./error-state.utils";

describe("getErrorStateDetails", () => {
  it("returns null for nullish error", () => {
    expect(getErrorStateDetails(null)).toBeNull();
    expect(getErrorStateDetails(undefined)).toBeNull();
  });

  it("maps ApiError into code/message/traceId", () => {
    const apiError = new ApiError("CONNECTOR_TIMEOUT", "Timed out", "trace-1", 504);
    expect(getErrorStateDetails(apiError)).toEqual({
      code: "CONNECTOR_TIMEOUT",
      message: "Timed out",
      traceId: "trace-1",
    });
  });

  it("maps a plain Error (network failure) to NETWORK_ERROR", () => {
    const networkError = new TypeError("Failed to fetch");
    expect(getErrorStateDetails(networkError)).toEqual({
      code: "NETWORK_ERROR",
      message: "Failed to fetch",
      traceId: "",
    });
  });

  it("maps an unknown thrown value to UNKNOWN_ERROR", () => {
    expect(getErrorStateDetails("oops")).toEqual({
      code: "UNKNOWN_ERROR",
      message: "",
      traceId: "",
    });
  });
});
