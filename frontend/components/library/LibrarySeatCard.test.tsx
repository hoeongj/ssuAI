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

const floorTwo: LibrarySeatStatusResponse = {
  floor: 2,
  floorLabel: "2층",
  totalSeats: 344,
  availableSeats: 230,
  reservedSeats: 112,
  outOfServiceSeats: 2,
  fetchedAt: "2026-05-15T07:30:14Z",
  zones: [
    { label: "숭실스퀘어ON(2F)", total: 112, available: 87, seatIds: ["201", "205", "210"] },
    { label: "오픈열람실(2F)", total: 232, available: 143, seatIds: [] },
  ],
};

const floorFive: LibrarySeatStatusResponse = {
  floor: 5,
  floorLabel: "5층",
  totalSeats: 104,
  availableSeats: 70,
  reservedSeats: 32,
  outOfServiceSeats: 2,
  fetchedAt: "2026-05-15T07:30:14Z",
  zones: [
    { label: "숭실멀티라운지(5F)", total: 98, available: 65, seatIds: [] },
    { label: "리클라이너(5F)", total: 6, available: 5, seatIds: [] },
  ],
};

beforeEach(() => {
  vi.mocked(getLibrarySeatStatus).mockReset();
});

describe("LibrarySeatCard", () => {
  it("renders availability + zones for the default floor", async () => {
    vi.mocked(getLibrarySeatStatus).mockResolvedValue(floorTwo);

    renderWithProviders(<LibrarySeatCard />);

    const progressBar = await screen.findByRole("progressbar", {
      name: /2층.*사용률/,
    });
    expect(progressBar).toBeInTheDocument();
    expect(screen.getByText(/\/ 344석 이용 가능/)).toBeInTheDocument();
    expect(screen.getByText("230")).toBeInTheDocument();
    expect(screen.getByText("숭실스퀘어ON(2F)")).toBeInTheDocument();
    expect(screen.getByText(/빈 자리: 201, 205, 210/)).toBeInTheDocument();
    expect(screen.getByText("예약 112")).toBeInTheDocument();
    expect(screen.getByText("사용 불가 2")).toBeInTheDocument();
  });

  it("switches floor when a different tab is selected", async () => {
    const user = userEvent.setup();
    vi.mocked(getLibrarySeatStatus).mockImplementation(async (floor) =>
      floor === 5 ? floorFive : floorTwo,
    );

    renderWithProviders(<LibrarySeatCard />);

    await screen.findByText(/\/ 344석 이용 가능/);

    await user.click(screen.getByRole("tab", { name: "5층" }));

    await waitFor(() => {
      expect(getLibrarySeatStatus).toHaveBeenCalledWith(5);
    });
    expect(await screen.findByText(/\/ 104석 이용 가능/)).toBeInTheDocument();
    const progressBar = screen.getByRole("progressbar", { name: /5층.*사용률/ });
    expect(progressBar).toBeInTheDocument();
    expect(within(progressBar.parentElement!).queryByText("70")).toBeInTheDocument();
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
