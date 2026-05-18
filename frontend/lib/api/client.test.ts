import { afterEach, describe, expect, it, vi } from "vitest";

async function importClient(baseUrl: string | null = "http://localhost:8080") {
  vi.resetModules();

  if (baseUrl === null) {
    delete process.env.NEXT_PUBLIC_SSUAI_API_BASE;
  } else {
    process.env.NEXT_PUBLIC_SSUAI_API_BASE = baseUrl;
  }

  return import("./client");
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
  delete process.env.NEXT_PUBLIC_SSUAI_API_BASE;
});

describe("fetchJson", () => {
  it("returns data from a 200 response with a valid envelope", async () => {
    const { fetchJson } = await importClient();
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json({
        data: { ok: true },
        error: null,
        traceId: "trace-1",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchJson<{ ok: boolean }>("/api/meals/today")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/meals/today",
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    );
    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Accept")).toBe("application/json");
    expect(headers.has("Content-Type")).toBe(false);
  });

  it("sets Content-Type only when a request body is present", async () => {
    const { fetchJson } = await importClient();
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json({
        data: { ok: true },
        error: null,
        traceId: "trace-1",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchJson<{ ok: boolean }>("/api/meals/today", {
      method: "POST",
      body: JSON.stringify({ ok: true }),
    });

    const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers;
    expect(headers.get("Accept")).toBe("application/json");
    expect(headers.get("Content-Type")).toBe("application/json");
  });

  it("throws ApiError when a 200 envelope contains an error", async () => {
    const { fetchJson } = await importClient();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        Response.json({
          data: null,
          error: { code: "CONNECTOR_UNAVAILABLE", message: "Unavailable" },
          traceId: "trace-2",
        }),
      ),
    );

    await expect(fetchJson("/api/meals/today")).rejects.toMatchObject({
      code: "CONNECTOR_UNAVAILABLE",
      message: "Unavailable",
      traceId: "trace-2",
      httpStatus: 200,
    });
  });

  it("throws ApiError from the envelope on a 5xx response", async () => {
    const { fetchJson } = await importClient();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        Response.json(
          {
            data: null,
            error: { code: "CONNECTOR_TIMEOUT", message: "Timed out" },
            traceId: "trace-3",
          },
          { status: 504 },
        ),
      ),
    );

    await expect(fetchJson("/api/meals/today")).rejects.toMatchObject({
      code: "CONNECTOR_TIMEOUT",
      message: "Timed out",
      traceId: "trace-3",
      httpStatus: 504,
    });
  });

  it("throws HTTP status ApiError when a failed response is not a JSON envelope", async () => {
    const { fetchJson } = await importClient();
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Server exploded", { status: 500, statusText: "Server Error" })),
    );

    await expect(fetchJson("/api/meals/today")).rejects.toMatchObject({
      code: "HTTP_500",
      message: "Server Error",
      traceId: "",
      httpStatus: 500,
    });
  });

  it("propagates network failures as the original error (non-ApiError)", async () => {
    const { fetchJson } = await importClient();
    const networkError = new TypeError("Failed to fetch");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(networkError));

    await expect(fetchJson("/api/meals/today")).rejects.toBe(networkError);
  });

  it("uses same-origin /api when NEXT_PUBLIC_SSUAI_API_BASE is missing in the browser", async () => {
    const { fetchJson } = await importClient(null);
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json({
        data: { ok: true },
        error: null,
        traceId: "trace-1",
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchJson<{ ok: boolean }>("/api/meals/today")).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/meals/today",
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    );
  });
});
