package com.ssuai.domain.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;
import com.ssuai.global.exception.ConnectorTimeoutException;

class LibrarySeatServiceTests {

    @Test
    void delegatesToCacheForRequestedFloor() {
        LibrarySeatCache cache = mock(LibrarySeatCache.class);
        LibrarySeatStatusResponse stub = stubResponse(LibraryFloor.F4);
        when(cache.get(LibraryFloor.F4)).thenReturn(stub);
        LibrarySeatService service = new LibrarySeatService(cache);

        LibrarySeatStatusResponse response = service.getSeatStatus(LibraryFloor.F4);

        assertThat(response).isSameAs(stub);
        ArgumentCaptor<LibraryFloor> floorCaptor = ArgumentCaptor.forClass(LibraryFloor.class);
        verify(cache).get(floorCaptor.capture());
        assertThat(floorCaptor.getValue()).isEqualTo(LibraryFloor.F4);
    }

    @Test
    void connectorExceptionBubblesUpWithoutWrapping() {
        LibrarySeatCache cache = mock(LibrarySeatCache.class);
        when(cache.get(LibraryFloor.F4)).thenThrow(new ConnectorTimeoutException());
        LibrarySeatService service = new LibrarySeatService(cache);

        assertThatThrownBy(() -> service.getSeatStatus(LibraryFloor.F4))
                .isInstanceOf(ConnectorTimeoutException.class);
    }

    private static LibrarySeatStatusResponse stubResponse(LibraryFloor floor) {
        return new LibrarySeatStatusResponse(
                floor.code(),
                floor.displayLabel(),
                36,
                12,
                18,
                6,
                Instant.parse("2026-05-15T10:00:00Z"),
                List.of()
        );
    }
}
