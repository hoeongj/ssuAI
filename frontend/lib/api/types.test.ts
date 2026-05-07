import { describe, expect, it } from "vitest";

import type {
  CampusFacilityListResponse,
  MealResponse,
  WeeklyMealResponse,
} from "./types";

describe("backend DTO type fixtures", () => {
  it("keeps meal and campus fixtures aligned with the TypeScript contracts", () => {
    const today = {
      date: "2026-05-07",
      meals: [
        {
          restaurant: "학생식당",
          type: "LUNCH",
          corner: "한식",
          menu: ["김치찌개", "쌀밥"],
        },
      ],
      closures: [],
    } satisfies MealResponse;

    const weekly = {
      startDate: "2026-05-04",
      endDate: "2026-05-08",
      days: [today],
    } satisfies WeeklyMealResponse;

    const facilities = {
      facilities: [
        {
          id: "student-cafeteria",
          name: "학생식당",
          category: "CAFETERIA",
          categoryLabel: "식당",
          location: "학생회관",
          phone: "02-0000-0000",
          extension: "0000",
          fax: "",
          weekdayHours: ["09:00-18:00"],
          weekendHours: [],
          notes: [],
          aliases: ["학식"],
        },
      ],
    } satisfies CampusFacilityListResponse;

    expect(weekly.days[0]?.meals[0]?.type).toBe("LUNCH");
    expect(facilities.facilities[0]?.category).toBe("CAFETERIA");
  });
});
