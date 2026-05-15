package com.ssuai.domain.library.connector;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;
import com.ssuai.domain.library.dto.LibrarySeatZone;

/**
 * Deterministic fixture for `ssuai.connector.library-seat=mock` (default).
 * Numbers are chosen so each floor exposes a distinct, realistic-looking
 * shape; they do not reflect actual library occupancy. The real Jsoup-based
 * connector replaces this bean when the upstream URL/markup is settled.
 */
@Component
@ConditionalOnProperty(name = "ssuai.connector.library-seat", havingValue = "mock", matchIfMissing = true)
public class MockLibrarySeatConnector implements LibrarySeatConnector {

    @Override
    public LibrarySeatStatusResponse fetchSeatStatus(LibraryFloor floor) {
        return switch (floor) {
            case B1 -> snapshot(floor, 24, 9, 12, 3, List.of(
                    new LibrarySeatZone("그룹스터디", 12, 5, List.of()),
                    new LibrarySeatZone("개인열람", 12, 4, List.of())
            ));
            case F1 -> snapshot(floor, 48, 18, 26, 4, List.of(
                    new LibrarySeatZone("창가", 16, 6, List.of("101", "104", "107")),
                    new LibrarySeatZone("중앙", 32, 12, List.of())
            ));
            case F2 -> snapshot(floor, 60, 22, 32, 6, List.of(
                    new LibrarySeatZone("창가", 20, 9, List.of("201", "203", "208", "212")),
                    new LibrarySeatZone("중앙", 40, 13, List.of())
            ));
            case F3 -> snapshot(floor, 40, 15, 20, 5, List.of(
                    new LibrarySeatZone("창가", 12, 5, List.of("305", "311")),
                    new LibrarySeatZone("중앙", 28, 10, List.of())
            ));
            case F4 -> snapshot(floor, 36, 12, 18, 6, List.of(
                    new LibrarySeatZone("창가", 8, 3, List.of("412", "415", "418")),
                    new LibrarySeatZone("중앙", 28, 9, List.of())
            ));
            case F5 -> snapshot(floor, 32, 11, 16, 5, List.of(
                    new LibrarySeatZone("창가", 10, 4, List.of("501", "503")),
                    new LibrarySeatZone("중앙", 22, 7, List.of())
            ));
            case F6 -> snapshot(floor, 20, 7, 10, 3, List.of(
                    new LibrarySeatZone("창가", 8, 3, List.of("601")),
                    new LibrarySeatZone("중앙", 12, 4, List.of())
            ));
        };
    }

    private LibrarySeatStatusResponse snapshot(
            LibraryFloor floor,
            int total,
            int available,
            int reserved,
            int outOfService,
            List<LibrarySeatZone> zones
    ) {
        return new LibrarySeatStatusResponse(
                floor.code(),
                floor.displayLabel(),
                total,
                available,
                reserved,
                outOfService,
                Instant.now(),
                zones
        );
    }
}
