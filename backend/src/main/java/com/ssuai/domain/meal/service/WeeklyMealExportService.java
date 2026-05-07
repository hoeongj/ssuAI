package com.ssuai.domain.meal.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.dto.WeeklyMealResponse;

@Service
public class WeeklyMealExportService {

    private static final int DAYS_PER_WEEK = 7;

    private final MealService mealService;

    public WeeklyMealExportService(MealService mealService) {
        this.mealService = mealService;
    }

    public WeeklyMealResponse fetchWeeklyMeals(LocalDate startDate) {
        List<MealResponse> days = IntStream.range(0, DAYS_PER_WEEK)
                .mapToObj(dayOffset -> mealService.getMeal(startDate.plusDays(dayOffset)))
                .toList();

        return new WeeklyMealResponse(startDate, startDate.plusDays(DAYS_PER_WEEK - 1), days);
    }
}
