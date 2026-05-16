package com.ssuai.domain.saint.connector;

import java.time.LocalDate;

/**
 * Shared helpers for schedule connectors (Real and Mock).
 *
 * <p>{@link #parseEnrollmentYear(String)} reads the leading four digits
 * of the SSU student id — a leave-of-absence or re-admission does not
 * mutate the prefix, so it is the stable anchor for "iterate back to
 * the term the student first enrolled in" (Task 16 spec §3.4).
 *
 * <p>{@link #termFor(LocalDate)} cuts the academic year at end of
 * August (1학기 = Mar–Aug, 2학기 = Sep–Feb). The exact boundary day
 * does not matter for an internal hint and matches the spec's
 * {@code monthValue <= 8 ? 1 : 2} convention.
 */
final class SaintScheduleHelpers {

    private SaintScheduleHelpers() {
    }

    static int parseEnrollmentYear(String studentId) {
        if (studentId == null || studentId.length() < 4) {
            throw new IllegalArgumentException("studentId must contain at least 4 leading digits");
        }
        String prefix = studentId.substring(0, 4);
        try {
            int year = Integer.parseInt(prefix);
            if (year < 1900 || year > 2100) {
                throw new IllegalArgumentException("studentId prefix is not a plausible year: " + prefix);
            }
            return year;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("studentId prefix is not numeric: " + prefix, exception);
        }
    }

    static int termFor(LocalDate date) {
        return date.getMonthValue() <= 8 ? 1 : 2;
    }
}
