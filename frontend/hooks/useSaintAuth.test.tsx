import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SaintAuthProvider, useSaintAuth } from "./useSaintAuth";
import { callLogout, fetchMe, refreshAccessToken } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/types";

vi.mock("@/lib/api/auth", () => ({
  refreshAccessToken: vi.fn(),
  fetchMe: vi.fn(),
  callLogout: vi.fn(),
}));

function AuthHarness() {
  const { user, isAuthenticated, isLoading, refresh, logout } = useSaintAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="name">{user?.name ?? ""}</span>
      <button type="button" onClick={() => void refresh()}>
        refresh
      </button>
      <button type="button" onClick={() => void logout()}>
        logout
      </button>
    </div>
  );
}

beforeEach(() => {
  vi.mocked(refreshAccessToken).mockReset();
  vi.mocked(fetchMe).mockReset();
  vi.mocked(callLogout).mockReset();
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("SaintAuthProvider", () => {
  it("hydrates a session on mount when the refresh cookie is valid", async () => {
    vi.mocked(refreshAccessToken).mockResolvedValue({
      accessToken: "access.jwt",
      accessTtlSeconds: 900,
    });
    vi.mocked(fetchMe).mockResolvedValue({
      studentId: "20231234",
      name: "홍길동",
      major: "컴퓨터학부",
      enrollmentStatus: "재학",
    });

    render(
      <SaintAuthProvider>
        <AuthHarness />
      </SaintAuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("auth").textContent).toBe("true");
    });
    expect(screen.getByTestId("name").textContent).toBe("홍길동");
    expect(refreshAccessToken).toHaveBeenCalledTimes(1);
    expect(fetchMe).toHaveBeenCalledWith("access.jwt");
  });

  it("stays anonymous when refresh returns 401", async () => {
    vi.mocked(refreshAccessToken).mockRejectedValue(
      new ApiError("UNAUTHORIZED", "unauthorized", "trace-1", 401),
    );

    render(
      <SaintAuthProvider>
        <AuthHarness />
      </SaintAuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(screen.getByTestId("auth").textContent).toBe("false");
    expect(screen.getByTestId("name").textContent).toBe("");
    expect(fetchMe).not.toHaveBeenCalled();
  });

  it("logout calls callLogout and clears the in-memory user", async () => {
    vi.mocked(refreshAccessToken).mockResolvedValue({
      accessToken: "access.jwt",
      accessTtlSeconds: 900,
    });
    vi.mocked(fetchMe).mockResolvedValue({
      studentId: "20231234",
      name: "홍길동",
      major: null,
      enrollmentStatus: null,
    });
    vi.mocked(callLogout).mockResolvedValue();

    render(
      <SaintAuthProvider>
        <AuthHarness />
      </SaintAuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("auth").textContent).toBe("true");
    });

    await act(async () => {
      screen.getByRole("button", { name: "logout" }).click();
    });

    expect(callLogout).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("auth").textContent).toBe("false");
    expect(screen.getByTestId("name").textContent).toBe("");
  });

  it("logout still clears state when the server call fails", async () => {
    vi.mocked(refreshAccessToken).mockResolvedValue({
      accessToken: "access.jwt",
      accessTtlSeconds: 900,
    });
    vi.mocked(fetchMe).mockResolvedValue({
      studentId: "20231234",
      name: "홍길동",
      major: null,
      enrollmentStatus: null,
    });
    vi.mocked(callLogout).mockRejectedValue(
      new ApiError("HTTP_500", "boom", "trace-1", 500),
    );
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});

    render(
      <SaintAuthProvider>
        <AuthHarness />
      </SaintAuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("auth").textContent).toBe("true");
    });

    await act(async () => {
      screen.getByRole("button", { name: "logout" }).click();
    });

    expect(callLogout).toHaveBeenCalledTimes(1);
    expect(warn).toHaveBeenCalled();
    expect(screen.getByTestId("auth").textContent).toBe("false");
    expect(screen.getByTestId("name").textContent).toBe("");
  });

  it("refresh() returns false when the refresh call throws", async () => {
    vi.mocked(refreshAccessToken).mockRejectedValue(
      new ApiError("UNAUTHORIZED", "unauthorized", "trace-1", 401),
    );

    let returned: boolean | undefined;
    function Caller() {
      const { refresh } = useSaintAuth();
      return (
        <button
          type="button"
          onClick={async () => {
            returned = await refresh();
          }}
        >
          go
        </button>
      );
    }

    render(
      <SaintAuthProvider>
        <Caller />
      </SaintAuthProvider>,
    );

    // wait for the initial mount probe to settle
    await waitFor(() => {
      expect(refreshAccessToken).toHaveBeenCalledTimes(1);
    });

    await act(async () => {
      screen.getByRole("button", { name: "go" }).click();
    });

    expect(returned).toBe(false);
  });
});
