import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LibrarySeatCard } from "@/components/library/LibrarySeatCard";
import { getLibrarySeatStatus } from "@/lib/api/library";
import { ApiError, type LibrarySeatStatusResponse } from "@/lib/api/types";
import { renderWithProviders } from "@/test-utils/render-with-providers";

vi.mock("@/lib/api/library", () => ({
  getLibrarySeatStatus: vi.fn(),
}));

const floorFour: LibrarySeatStatusResponse = {
  floor: 4,
  floorLabel: "4층",
  totalSeats: 36,
  availableSeats: 12,
  reservedSeats: 18,
  outOfServiceSeats: 6,
  fetchedAt: "2026-05-15T07:30:14Z",
  zones: [
    { label: "창가", total: 8, available: 3, seatIds: ["412", "415", "418"] },
    { label: "중앙", total: 28, available: 9, seatIds: [] },
  ],
};

const floorTwo: LibrarySeatStatusResponse = {
  floor: 2,
  floorLabel: "2층",
  totalSeats: 60,
  availableSeats: 22,
  reservedSeats: 32,
  outOfServiceSeats: 6,
  fetchedAt: "2026-05-15T07:30:14Z",
  zones: [
    { label: "창가", total: 20, available: 9, seatIds: ["201", "203"] },
    { label: "중앙", total: 40, available: 13, seatIds: [] },
  ],
};

beforeEach(() => {
  vi.mocked(getLibrarySeatStatus).mockReset();
});

describe("LibrarySeatCard", () => {
  it("renders availability + zones for the default floor", async () => {
    vi.mocked(getLibrarySeatStatus).mockResolvedValue(floorFour);

    renderWithProviders(<LibrarySeatCard />);

    const progressBar = await screen.findByRole("progressbar", {
      name: /4층.*사용률/,
    });
    expect(progressBar).toBeInTheDocument();
    expect(screen.getByText(/\/ 36석 이용 가능/)).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("창가")).toBeInTheDocument();
    expect(screen.getByText(/빈 자리: 412, 415, 418/)).toBeInTheDocument();
    expect(screen.getByText("예약 18")).toBeInTheDocument();
    expect(screen.getByText("사용 불가 6")).toBeInTheDocument();
  });

  it("switches floor when a different tab is selected", async () => {
    const user = userEvent.setup();
    vi.mocked(getLibrarySeatStatus).mockImplementation(async (floor) =>
      floor === 2 ? floorTwo : floorFour,
    );

    renderWithProviders(<LibrarySeatCard />);

    await screen.findByText(/\/ 36석 이용 가능/);

    await user.click(screen.getByRole("tab", { name: "2층" }));

    await waitFor(() => {
      expect(getLibrarySeatStatus).toHaveBeenCalledWith(2);
    });
    expect(await screen.findByText(/\/ 60석 이용 가능/)).toBeInTheDocument();
    const progressBar = screen.getByRole("progressbar", { name: /2층.*사용률/ });
    expect(progressBar).toBeInTheDocument();
    expect(within(progressBar.parentElement!).queryByText("22")).toBeInTheDocument();
  });

  it("shows ErrorState with retry when the request fails", async () => {
    vi.mocked(getLibrarySeatStatus).mockRejectedValue(
      new ApiError("CONNECTOR_TIMEOUT", "응답 지연", "trace-x", 504),
    );

    renderWithProviders(<LibrarySeatCard />);

    expect(
      await screen.findByText("응답이 너무 오래 걸렸습니다. 잠시 후 다시 시도해주세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /다시 시도/ })).toBeInTheDocument();
  });
});
