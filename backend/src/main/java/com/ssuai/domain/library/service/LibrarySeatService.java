package com.ssuai.domain.library.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ssuai.domain.library.dto.LibraryFloor;
import com.ssuai.domain.library.dto.LibrarySeatStatusResponse;
import com.ssuai.global.exception.ConnectorException;

@Service
public class LibrarySeatService {

    private static final Logger log = LoggerFactory.getLogger(LibrarySeatService.class);

    private final LibrarySeatCache cache;

    public LibrarySeatService(LibrarySeatCache cache) {
        this.cache = cache;
    }

    public LibrarySeatStatusResponse getSeatStatus(LibraryFloor floor) {
        try {
            return cache.get(floor);
        } catch (ConnectorException exception) {
            log.warn("library seat fetch failure: floor={} code={}",
                    floor.displayLabel(), exception.getErrorCode().name());
            throw exception;
        }
    }
}
