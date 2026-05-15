package com.ssuai.domain.library.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LibraryFloorTests {

    @ParameterizedTest
    @CsvSource({
            "-1, B1, B1",
            "1, F1, 1층",
            "2, F2, 2층",
            "3, F3, 3층",
            "4, F4, 4층",
            "5, F5, 5층",
            "6, F6, 6층"
    })
    void fromCodeResolvesEveryFloorWithDisplayLabel(int code, LibraryFloor expected, String displayLabel) {
        LibraryFloor floor = LibraryFloor.fromCode(code);

        assertThat(floor).isEqualTo(expected);
        assertThat(floor.code()).isEqualTo(code);
        assertThat(floor.displayLabel()).isEqualTo(displayLabel);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -2, 7, 99})
    void fromCodeRejectsUnsupportedCodes(int code) {
        assertThatThrownBy(() -> LibraryFloor.fromCode(code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floor")
                .hasMessageContaining(Integer.toString(code));
    }
}
