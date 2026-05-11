import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { FacilitySearchCard } from "@/components/facility/FacilitySearchCard";
import { searchFacilities } from "@/lib/api/facility";
import type { CampusFacilityListResponse } from "@/lib/api/types";
import { renderWithProviders } from "@/test-utils/render-with-providers";

vi.mock("@/lib/api/facility", () => ({
  searchFacilities: vi.fn(),
}));

const mockFacilities: CampusFacilityListResponse = {
  facilities: [
    {
      id: "mock-student-cafeteria",
      name: "Mock 학생식당",
      category: "CAFETERIA",
      categoryLabel: "식당",
      location: "학생회관 1층",
      phone: "02-0000-0000",
      extension: "0000",
      fax: "",
      weekdayHours: ["09:00-18:00"],
      weekendHours: [],
      notes: ["Mock data for component test."],
      aliases: ["학식", "학생식당"],
    },
  ],
};

beforeEach(() => {
  vi.mocked(searchFacilities).mockReset();
});

describe("FacilitySearchCard", () => {
  it("searches from typed input and renders matching facilities", async () => {
    const user = userEvent.setup();
    vi.mocked(searchFacilities).mockResolvedValue(mockFacilities);

    renderWithProviders(<FacilitySearchCard />);

    await user.type(screen.getByRole("textbox", { name: "시설 검색어" }), "학식");

    await waitFor(() => {
      expect(searchFacilities).toHaveBeenCalledWith("학식");
    });
    expect(await screen.findByText("Mock 학생식당")).toBeInTheDocument();
    expect(screen.getByText("학생회관 1층")).toBeInTheDocument();
    expect(screen.getByText("평일: 09:00-18:00")).toBeInTheDocument();
  });

  it("limits search input to 64 characters", async () => {
    const user = userEvent.setup();
    vi.mocked(searchFacilities).mockResolvedValue({ facilities: [] });

    renderWithProviders(<FacilitySearchCard />);

    const input = screen.getByRole("textbox", { name: "시설 검색어" });
    await user.type(input, "a".repeat(70));

    expect(input).toHaveValue("a".repeat(64));
  });
});
