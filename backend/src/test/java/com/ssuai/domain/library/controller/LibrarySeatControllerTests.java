package com.ssuai.domain.library.controller;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;
import com.ssuai.domain.library.dto.LibrarySeatZone;
import com.ssuai.domain.library.service.LibrarySeatService;
import com.ssuai.global.exception.ConnectorTimeoutException;
import com.ssuai.global.exception.ConnectorUnavailableException;
import com.ssuai.global.exception.LibraryAuthRequiredException;

@ActiveProfiles("test")
@WebMvcTest(LibrarySeatController.class)
class LibrarySeatControllerTests {

    private final MockMvc mockMvc;

    @MockitoBean
    private LibrarySeatService libraryService;

    @Autowired
    LibrarySeatControllerTests(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void getSeatStatusReturnsSuccessEnvelope() throws Exception {
        LibrarySeatStatusResponse response = new LibrarySeatStatusResponse(
                4, "4층", 36, 12, 18, 6,
                Instant.parse("2026-05-15T07:30:14Z"),
                List.of(new LibrarySeatZone("창가", 8, 3, List.of("412", "415")))
        );
        when(libraryService.getSeatStatusForSession(eq(LibraryFloor.F4), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/library/seats").param("floor", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.floor").value(4))
                .andExpect(jsonPath("$.data.floorLabel").value("4층"))
                .andExpect(jsonPath("$.data.availableSeats").value(12))
                .andExpect(jsonPath("$.data.totalSeats").value(36))
                .andExpect(jsonPath("$.data.zones[0].label").value("창가"))
                .andExpect(jsonPath("$.data.zones[0].seatIds[0]").value("412"))
                .andExpect(jsonPath("$.error").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value(not(emptyOrNullString())));
    }

    @Test
    void getSeatStatusAcceptsBasementFloor() throws Exception {
        LibrarySeatStatusResponse response = new LibrarySeatStatusResponse(
                -1, "B1", 24, 9, 12, 3,
                Instant.parse("2026-05-15T07:30:14Z"),
                List.of()
        );
        when(libraryService.getSeatStatusForSession(eq(LibraryFloor.B1), anyString())).thenReturn(response);

        mockMvc.perform(get("/api/library/seats").param("floor", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.floor").value(-1))
                .andExpect(jsonPath("$.data.floorLabel").value("B1"));
    }

    @Test
    void getSeatStatusRejectsUnsupportedFloor() throws Exception {
        mockMvc.perform(get("/api/library/seats").param("floor", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(libraryService);
    }

    @Test
    void getSeatStatusRejectsMissingFloor() throws Exception {
        mockMvc.perform(get("/api/library/seats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(libraryService);
    }

    @Test
    void connectorTimeoutMapsTo504() throws Exception {
        when(libraryService.getSeatStatusForSession(eq(LibraryFloor.F4), anyString()))
                .thenThrow(new ConnectorTimeoutException());

        mockMvc.perform(get("/api/library/seats").param("floor", "4"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("CONNECTOR_TIMEOUT"));
    }

    @Test
    void connectorUnavailableMapsTo503() throws Exception {
        when(libraryService.getSeatStatusForSession(eq(LibraryFloor.F4), anyString()))
                .thenThrow(new ConnectorUnavailableException());

        mockMvc.perform(get("/api/library/seats").param("floor", "4"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("CONNECTOR_UNAVAILABLE"));
    }

    @Test
    void libraryAuthRequiredMapsTo401() throws Exception {
        when(libraryService.getSeatStatusForSession(eq(LibraryFloor.F4), anyString()))
                .thenThrow(new LibraryAuthRequiredException());

        mockMvc.perform(get("/api/library/seats").param("floor", "4"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("LIBRARY_SESSION_REQUIRED"));
    }

}
