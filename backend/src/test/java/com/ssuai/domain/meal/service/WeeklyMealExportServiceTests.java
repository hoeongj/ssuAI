package com.ssuai.domain.meal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ssuai.domain.meal.connector.MealConnector;
import com.ssuai.domain.meal.dto.MealClosure;
import com.ssuai.domain.meal.dto.MealItem;
import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.dto.MealRestaurant;
import com.ssuai.domain.meal.dto.MealType;
import com.ssuai.domain.meal.dto.WeeklyMealResponse;
import com.ssuai.global.exception.ConnectorUnavailableException;

class WeeklyMealExportServiceTests {

    private final MealConnector mealConnector = mock(MealConnector.class);
    private final MealService mealService = new MealService(mealConnector);
    private final WeeklyMealExportService exportService = new WeeklyMealExportService(mealService);

    @Test
    void fetchWeeklyMealsAggregatesAcrossSevenDaysWithFanOutFailures() {
        LocalDate startDate = LocalDate.of(2026, 5, 3);
        when(mealConnector.fetchMeal(any(LocalDate.class), any(MealRestaurant.class)))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(0);
                    MealRestaurant restaurant = invocation.getArgument(1);
                    if (restaurant == MealRestaurant.SNACK) {
                        throw new ConnectorUnavailableException(new RuntimeException("503"));
                    }
                    return new MealResponse(
                            date,
                            List.of(new MealItem(
                                    restaurant.displayName(),
                                    MealType.LUNCH,
                                    "중식",
                                    List.of("쌀밥"))));
                });

        WeeklyMealResponse response = exportService.fetchWeeklyMeals(startDate);

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 5, 3));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(response.days()).hasSize(7);

        MealResponse firstDay = response.days().get(0);
        assertThat(firstDay.meals()).hasSize(MealRestaurant.values().length - 1);
        assertThat(firstDay.closures().get(0))
                .extracting(MealClosure::restaurant, MealClosure::reason)
                .containsExactly("스낵코너", "조회 실패: CONNECTOR_UNAVAILABLE");

        verify(mealConnector, times(7 * MealRestaurant.values().length))
                .fetchMeal(any(LocalDate.class), any(MealRestaurant.class));
    }
}
