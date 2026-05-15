package com.ssuai.domain.library.connector;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;

public interface LibrarySeatConnector {

    LibrarySeatStatusResponse fetchSeatStatus(LibraryFloor floor);
}
