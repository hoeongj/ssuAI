package com.ssuai.domain.library.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;
import com.ssuai.domain.library.dto.LibrarySeatZone;

class MockLibrarySeatConnectorTests {

    private final MockLibrarySeatConnector connector = new MockLibrarySeatConnector();

    @ParameterizedTest
    @EnumSource(LibraryFloor.class)
    void everyFloorReturnsConsistentSnapshot(LibraryFloor floor) {
        LibrarySeatStatusResponse response = connector.fetchSeatStatus(floor);

        assertThat(response.floor()).isEqualTo(floor.code());
        assertThat(response.floorLabel()).isEqualTo(floor.displayLabel());
        assertThat(response.totalSeats()).isPositive();
        assertThat(response.availableSeats()).isNotNegative();
        assertThat(response.reservedSeats()).isNotNegative();
        assertThat(response.outOfServiceSeats()).isNotNegative();
        assertThat(response.fetchedAt()).isNotNull();
        assertThat(response.zones()).isNotEmpty();
        int zoneTotal = response.zones().stream()
                .mapToInt(LibrarySeatZone::total)
                .sum();
        assertThat(zoneTotal).isEqualTo(response.totalSeats());
        int zoneAvailable = response.zones().stream()
                .mapToInt(LibrarySeatZone::available)
                .sum();
        assertThat(zoneAvailable).isEqualTo(response.availableSeats());
    }

    @Test
    void floorFourPreservesIndividualSeatIdsForPhaseFourReservation() {
        LibrarySeatStatusResponse response = connector.fetchSeatStatus(LibraryFloor.F4);

        boolean anyZoneHasSeatIds = response.zones().stream()
                .anyMatch(zone -> !zone.seatIds().isEmpty());
        assertThat(anyZoneHasSeatIds)
                .as("F4 mock fixture should expose at least one zone with seatIds to keep the Phase 4 reserve flow testable")
                .isTrue();
    }
}
