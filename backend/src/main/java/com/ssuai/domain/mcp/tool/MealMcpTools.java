package com.ssuai.domain.mcp.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.ssuai.domain.meal.dto.MealResponse;
import com.ssuai.domain.meal.service.MealService;
import com.ssuai.global.exception.ConnectorException;

@Component
public class MealMcpTools {

    private static final int MAX_ERROR_VALUE_LENGTH = 64;

    private final MealService mealService;

    public MealMcpTools(MealService mealService) {
        this.mealService = mealService;
    }

    @Tool(
            name = "get_today_meal",
            description = "오늘 숭실대학교 학생식당의 메뉴를 조회합니다. 학생식당, 숭실도담식당, FACULTY LOUNGE 등 캠퍼스 내 모든 식당의 코너별 메뉴와 휴무 정보를 함께 반환합니다."
    )
    public MealResponse getTodayMeal() {
        try {
            return mealService.getTodayMeal();
        } catch (ConnectorException exception) {
            throw new IllegalStateException(ConnectorErrorMessages.forResource("학식", exception), exception);
        }
    }

    @Tool(
            name = "get_meal_by_date",
            description = "지정한 날짜(yyyy-MM-dd)의 숭실대학교 학생식당 메뉴를 조회합니다. 캠퍼스 내 모든 식당의 코너별 메뉴와 휴무 정보를 함께 반환합니다."
    )
    public MealResponse getMealByDate(
            @ToolParam(description = "조회할 날짜. 반드시 ISO 형식 yyyy-MM-dd 의 문자열 (예: 2026-05-07). 빈 값/다른 형식이면 에러.")
            String date
    ) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException(
                    "date: yyyy-MM-dd 형식의 날짜 문자열이 필요합니다. 예: 2026-05-07. 받은 값: '"
                            + displayValue(date) + "'.");
        }

        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "date: yyyy-MM-dd 형식이어야 합니다. 예: 2026-05-07. 받은 값: '"
                            + displayValue(date) + "'.",
                    exception);
        }

        try {
            return mealService.getMeal(parsed);
        } catch (ConnectorException exception) {
            throw new IllegalStateException(ConnectorErrorMessages.forResource("학식", exception), exception);
        }
    }

    private static String displayValue(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_ERROR_VALUE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_VALUE_LENGTH) + "...";
    }
}
