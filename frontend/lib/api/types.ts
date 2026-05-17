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

export type LibraryFloorCode = 2 | 5 | 6;

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

export type BookStatus = "AVAILABLE" | "CHECKED_OUT" | "UNKNOWN";

export interface LibraryBook {
  id: number;
  title: string;
  author: string;
  publication: string;
  isbn: string;
  thumbnailUrl: string;
  callNumber: string;
  location: string;
  status: BookStatus;
}

export interface LibraryBookSearchResponse {
  total: number;
  page: number;
  size: number;
  items: LibraryBook[];
}

export interface LibraryLoanItem {
  id: number;
  title: string;
  author: string;
  callNumber: string;
  loanDate: string;
  dueDate: string;
  isOverdue: boolean;
  isRenewable: boolean;
}

export interface LibraryLoansResponse {
  total: number;
  loans: LibraryLoanItem[];
}

export interface ScheduleEntry {
  dayOfWeek: number;
  dayLabel: string;
  period: number;
  timeRange: string;
  course: string;
  professor: string;
  room: string;
}

export interface TermSchedule {
  year: number;
  term: number;
  entries: ScheduleEntry[];
}

export interface ScheduleResponse {
  enrollmentYear: number;
  currentYear: number;
  currentTerm: number;
  terms: TermSchedule[];
}

export interface TermGpa {
  year: number;
  term: string;
  requestedCredits: number;
  earnedCredits: number;
  passFailCredits: number;
  gpa: number;
  gpaSum: number;
  arithmeticAverage: number;
  rankInTerm: string;
  rankOverall: string;
  academicWarning: boolean;
  counseling: boolean;
  repeatedYear: boolean;
}

export interface GpaSummary {
  requestedCredits: number;
  earnedCredits: number;
  gpaSum: number;
  gpa: number;
  arithmeticAverage: number;
  passFailCredits: number;
}

export interface GradesResponse {
  history: TermGpa[];
  academicRecord: GpaSummary;
  certificate: GpaSummary;
  detailsByTerm: Record<string, unknown[]>;
}

export interface AssignmentItem {
  courseName: string;
  title: string;
  type: string;
  dueDate: string | null;
}

export interface AssignmentsResponse {
  termId: number;
  items: AssignmentItem[];
}

export interface ChatRequest {
  conversationId?: string;
  message: string;
}

export interface ChatResponse {
  conversationId: string;
  reply: string;
}
