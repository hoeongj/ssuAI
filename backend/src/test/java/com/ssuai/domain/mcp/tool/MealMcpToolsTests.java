package com.ssuai.domain.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.service.MealService;
import com.ssuai.global.exception.ConnectorTimeoutException;
import com.ssuai.global.exception.ConnectorUnavailableException;

class MealMcpToolsTests {

    private final MealService mealService = mock(MealService.class);
    private final MealMcpTools tools = new MealMcpTools(mealService);

    @Test
    void getTodayMealDelegatesToService() {
        MealResponse expected = new MealResponse(LocalDate.of(2026, 5, 7), List.of());
        when(mealService.getTodayMeal()).thenReturn(expected);

        MealResponse response = tools.getTodayMeal();

        assertThat(response).isSameAs(expected);
        verify(mealService).getTodayMeal();
    }

    @Test
    void getMealByDateParsesIsoDateAndDelegatesToService() {
        LocalDate date = LocalDate.of(2026, 5, 7);
        MealResponse expected = new MealResponse(date, List.of());
        when(mealService.getMeal(date)).thenReturn(expected);

        MealResponse response = tools.getMealByDate("2026-05-07");

        assertThat(response).isSameAs(expected);
        verify(mealService).getMeal(date);
    }

    @Test
    void getTodayMealWrapsConnectorTimeoutWithFriendlyMessage() {
        ConnectorTimeoutException exception = new ConnectorTimeoutException();
        when(mealService.getTodayMeal()).thenThrow(exception);

        assertThatThrownBy(tools::getTodayMeal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("학식")
                .hasMessageContaining("지연")
                .satisfies(thrown -> assertThat(thrown.getCause()).isSameAs(exception));
    }

    @Test
    void getMealByDateWrapsConnectorUnavailableWithFriendlyMessage() {
        LocalDate date = LocalDate.of(2026, 5, 7);
        ConnectorUnavailableException exception = new ConnectorUnavailableException();
        when(mealService.getMeal(date)).thenThrow(exception);

        assertThatThrownBy(() -> tools.getMealByDate("2026-05-07"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("학식")
                .hasMessageContaining("연결할 수 없")
                .satisfies(thrown -> assertThat(thrown.getCause()).isSameAs(exception));
    }

    @Test
    void getMealByDateRejectsNullDate() {
        assertThatThrownBy(() -> tools.getMealByDate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd")
                .hasMessageContaining("'null'");
        verifyNoInteractions(mealService);
    }

    @Test
    void getMealByDateRejectsBlankDate() {
        assertThatThrownBy(() -> tools.getMealByDate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd")
                .hasMessageContaining("''");
        verifyNoInteractions(mealService);
    }

    @Test
    void getMealByDateRejectsInvalidDateWithFriendlyMessage() {
        assertThatThrownBy(() -> tools.getMealByDate("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd")
                .hasMessageContaining("'abc'")
                .hasCauseInstanceOf(DateTimeParseException.class);
        verifyNoInteractions(mealService);
    }
}
