package com.ssuai.global.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ssuai.global.response.ApiResponse;

@Validated
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public ApiResponse<HelloResponse> hello(
            @RequestParam @NotBlank @Size(max = 50) String name
    ) {
        return ApiResponse.success(new HelloResponse("Hello, " + name));
    }

    public record HelloResponse(String greeting) {
    }
}

