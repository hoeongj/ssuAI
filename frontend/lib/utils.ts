import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

const seoulDatePartsFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const koreanLongDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "long",
  day: "numeric",
  weekday: "short",
});

const koreanShortDateFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  month: "numeric",
  day: "numeric",
  weekday: "short",
});

export function millisecondsUntilNextSeoulMidnight(now = new Date()) {
  const parts = seoulDatePartsFormatter.formatToParts(now);
  const year = Number(parts.find((part) => part.type === "year")?.value);
  const month = Number(parts.find((part) => part.type === "month")?.value);
  const day = Number(parts.find((part) => part.type === "day")?.value);
  const nextSeoulMidnightUtc = Date.UTC(year, month - 1, day + 1, -9, 0, 0);

  return Math.max(nextSeoulMidnightUtc - now.getTime(), 60_000);
}

export function formatKoreanDate(date: string) {
  return koreanLongDateFormatter.format(new Date(`${date}T00:00:00+09:00`));
}

export function formatShortKoreanDate(date: string) {
  return koreanShortDateFormatter.format(new Date(`${date}T00:00:00+09:00`));
}

export function getSeoulDateString(now = new Date()) {
  return seoulDatePartsFormatter.format(now);
}

export function normalizeSearchQuery(query: string) {
  return query.trim().toLowerCase();
}

export function mealTypeLabel(type: string) {
  switch (type) {
    case "ALL_DAY":
      return "상시";
    case "BREAKFAST":
      return "아침";
    case "LUNCH":
      return "점심";
    case "DINNER":
      return "저녁";
    default:
      return type;
  }
}
