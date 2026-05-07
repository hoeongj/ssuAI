package com.ssuai.domain.meal.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ssuai.domain.meal.connector.MealConnector;
import com.ssuai.domain.meal.dto.MealClosure;
import com.ssuai.domain.meal.dto.MealItem;
import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.dto.MealRestaurant;
import com.ssuai.global.exception.ConnectorException;

@Service
public class MealService {

    private static final Logger log = LoggerFactory.getLogger(MealService.class);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final MealConnector mealConnector;

    public MealService(MealConnector mealConnector) {
        this.mealConnector = mealConnector;
    }

    public MealResponse getTodayMeal() {
        return getMeal(LocalDate.now(SEOUL_ZONE));
    }

    public MealResponse getMeal(LocalDate date) {
        List<FetchOutcome> outcomes = Arrays.stream(MealRestaurant.values())
                .parallel()
                .map(restaurant -> fetchMeal(date, restaurant))
                .toList();

        List<MealItem> meals = new ArrayList<>();
        List<MealClosure> closures = new ArrayList<>();
        int failureCount = 0;
        ConnectorException lastFailure = null;

        for (FetchOutcome outcome : outcomes) {
            if (outcome.failure() == null) {
                meals.addAll(outcome.partial().meals());
                closures.addAll(outcome.partial().closures());
            } else {
                failureCount++;
                ConnectorException exception = outcome.failure();
                lastFailure = exception;
                closures.add(new MealClosure(
                        outcome.restaurant().displayName(),
                        "조회 실패: " + exception.getErrorCode().name()));
            }
        }

        if (failureCount == MealRestaurant.values().length) {
            throw lastFailure;
        }

        return new MealResponse(date, List.copyOf(meals), List.copyOf(closures));
    }

    private FetchOutcome fetchMeal(LocalDate date, MealRestaurant restaurant) {
        try {
            return new FetchOutcome(restaurant, mealConnector.fetchMeal(date, restaurant), null);
        } catch (ConnectorException exception) {
            log.warn("meal fan-out failure: restaurant={} date={} code={}",
                    restaurant.displayName(), date, exception.getErrorCode().name());
            return new FetchOutcome(restaurant, null, exception);
        }
    }

    private record FetchOutcome(
            MealRestaurant restaurant,
            MealResponse partial,
            ConnectorException failure
    ) {
    }
}
