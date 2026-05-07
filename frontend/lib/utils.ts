import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function millisecondsUntilNextSeoulMidnight(now = new Date()) {
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const parts = formatter.formatToParts(now);
  const year = Number(parts.find((part) => part.type === "year")?.value);
  const month = Number(parts.find((part) => part.type === "month")?.value);
  const day = Number(parts.find((part) => part.type === "day")?.value);
  const nextSeoulMidnightUtc = Date.UTC(year, month - 1, day + 1, -9, 0, 0);

  return Math.max(nextSeoulMidnightUtc - now.getTime(), 60_000);
}

export function formatKoreanDate(date: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(new Date(`${date}T00:00:00+09:00`));
}

export function mealTypeLabel(type: string) {
  switch (type) {
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
