export interface ApiResponse<T> {
  data: T | null;
  error: ApiErrorBody | null;
  traceId: string;
}

export interface ApiErrorBody {
  code: string;
  message: string;
}

export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public traceId: string,
    public httpStatus: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export type MealType = "ALL_DAY" | "BREAKFAST" | "LUNCH" | "DINNER";

export interface MealItem {
  restaurant: string;
  type: MealType;
  corner: string;
  menu: string[];
}

export interface MealClosure {
  restaurant: string;
  reason: string;
}

export interface MealResponse {
  date: string;
  meals: MealItem[];
  closures: MealClosure[];
}

export interface WeeklyMealResponse {
  startDate: string;
  endDate: string;
  days: MealResponse[];
}

export type CampusFacilityCategory =
  | "CAFETERIA"
  | "CONVENIENCE_STORE"
  | "CAFE"
  | "BOOKSTORE_STATIONERY"
  | "SNACK"
  | "BAKERY"
  | "GIFT_SHOP"
  | "PRINT_SHOP";

export interface CampusFacility {
  id: string;
  name: string;
  category: CampusFacilityCategory;
  categoryLabel: string;
  location: string;
  phone: string;
  extension: string;
  fax: string;
  weekdayHours: string[];
  weekendHours: string[];
  notes: string[];
  aliases: string[];
}

export interface CampusFacilityListResponse {
  facilities: CampusFacility[];
}

export type LibraryFloorCode = -1 | 1 | 2 | 3 | 4 | 5 | 6;

export interface LibrarySeatZone {
  label: string;
  total: number;
  available: number;
  seatIds: string[];
}

export interface LibrarySeatStatusResponse {
  floor: LibraryFloorCode;
  floorLabel: string;
  totalSeats: number;
  availableSeats: number;
  reservedSeats: number;
  outOfServiceSeats: number;
  fetchedAt: string;
  zones: LibrarySeatZone[];
}

export interface ChatRequest {
  conversationId?: string;
  message: string;
}

export interface ChatResponse {
  conversationId: string;
  reply: string;
}
