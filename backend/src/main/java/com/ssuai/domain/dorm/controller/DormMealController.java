package com.ssuai.domain.dorm.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssuai.domain.dorm.service.DormMealService;
import com.ssuai.domain.meal.dto.WeeklyMealResponse;
import com.ssuai.global.response.ApiResponse;

@RestController
@RequestMapping("/api/dorm/meals")
public class DormMealController {

    private final DormMealService dormMealService;

    public DormMealController(DormMealService dormMealService) {
        this.dormMealService = dormMealService;
    }

    @GetMapping("/this-week")
    public ApiResponse<WeeklyMealResponse> getThisWeekMeal() {
        return ApiResponse.success(dormMealService.getThisWeekMeal());
    }
}
