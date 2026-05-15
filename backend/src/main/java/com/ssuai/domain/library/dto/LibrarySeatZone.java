package com.ssuai.domain.library.dto;

import java.util.List;

public record LibrarySeatZone(
        String label,
        int total,
        int available,
        List<String> seatIds
) {

    public LibrarySeatZone {
        seatIds = seatIds == null ? List.of() : List.copyOf(seatIds);
    }
}
