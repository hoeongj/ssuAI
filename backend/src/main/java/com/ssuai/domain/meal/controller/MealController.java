package com.ssuai.domain.meal.controller;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.dto.WeeklyMealResponse;
import com.ssuai.domain.meal.service.MealService;
import com.ssuai.domain.meal.service.WeeklyMealExportService;
import com.ssuai.global.response.ApiResponse;

@Validated
@RestController
@RequestMapping("/api/meals")
public class MealController {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int ISO_DATE_LENGTH = 10;

    private final MealService mealService;
    private final WeeklyMealExportService weeklyMealExportService;

    public MealController(MealService mealService, WeeklyMealExportService weeklyMealExportService) {
        this.mealService = mealService;
        this.weeklyMealExportService = weeklyMealExportService;
    }

    @GetMapping("/today")
    public ApiResponse<MealResponse> getTodayMeal() {
        return ApiResponse.success(mealService.getTodayMeal());
    }

    @GetMapping("/weekly")
    public ApiResponse<WeeklyMealResponse> getWeeklyMeals(
            @RequestParam(required = false)
            @Size(max = ISO_DATE_LENGTH)
            String startDate
    ) {
        LocalDate resolved = resolveStartDate(startDate);
        return ApiResponse.success(weeklyMealExportService.fetchWeeklyMeals(resolved));
    }

    private LocalDate resolveStartDate(String startDate) {
        if (startDate == null || startDate.isBlank()) {
            return LocalDate.now(SEOUL_ZONE).with(DayOfWeek.MONDAY);
        }

        try {
            return LocalDate.parse(startDate);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "startDate는 yyyy-MM-dd 형식이어야 합니다. 받은 값: '" + startDate + "'.",
                    exception);
        }
    }
}
