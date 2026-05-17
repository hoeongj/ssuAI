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
    public LibrarySeatStatusResponse fetchSeatStatus(LibraryFloor floor, String token) {
        return switch (floor) {
            case F2 -> snapshot(floor, 344, 230, 112, 2, List.of(
                    new LibrarySeatZone("숭실스퀘어ON(2F)", 112, 75, List.of()),
                    new LibrarySeatZone("오픈열람실(2F)", 232, 155, List.of())
            ));
            case F5 -> snapshot(floor, 104, 70, 32, 2, List.of(
                    new LibrarySeatZone("숭실멀티라운지(5F)", 98, 65, List.of()),
                    new LibrarySeatZone("리클라이너(5F)", 6, 5, List.of())
            ));
            case F6 -> snapshot(floor, 308, 200, 100, 8, List.of(
                    new LibrarySeatZone("마루열람실(6F)", 246, 160, List.of()),
                    new LibrarySeatZone("대학원열람실(6F)", 62, 40, List.of())
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
