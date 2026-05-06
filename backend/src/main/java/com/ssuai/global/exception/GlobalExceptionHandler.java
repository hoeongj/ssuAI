package com.ssuai.global.exception;

import java.util.Optional;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.ssuai.global.response.ApiResponse;
import com.ssuai.global.response.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(this::formatFieldError)
                .orElse(ErrorCode.VALIDATION_FAILED.getDefaultMessage());

        return validationFailed(message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception
    ) {
        String message = exception.getAllValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors()
                        .stream()
                        .map(error -> formatValidationError(result.getMethodParameter().getParameterName(), error)))
                .findFirst()
                .orElse(ErrorCode.VALIDATION_FAILED.getDefaultMessage());

        return validationFailed(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(this::formatConstraintViolation)
                .orElse(ErrorCode.VALIDATION_FAILED.getDefaultMessage());

        return validationFailed(message);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        return validationFailed(exception.getParameterName() + ": required request parameter is missing");
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleApiException(ApiException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ErrorResponse errorResponse = new ErrorResponse(errorCode.name(), exception.getMessage());

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleException(Exception exception) {
        log.error("Unhandled exception occurred: exceptionType={}", exception.getClass().getName(), exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        ErrorResponse errorResponse = new ErrorResponse(errorCode.name(), "Internal server error");

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorResponse));
    }

    private ResponseEntity<ApiResponse<ErrorResponse>> validationFailed(String message) {
        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ErrorResponse errorResponse = new ErrorResponse(errorCode.name(), message);

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorResponse));
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private String formatValidationError(String parameterName, MessageSourceResolvable error) {
        return safeName(parameterName) + ": " + error.getDefaultMessage();
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        return Optional.ofNullable(violation.getPropertyPath())
                .map(Object::toString)
                .map(path -> path.substring(path.lastIndexOf('.') + 1))
                .map(this::safeName)
                .map(name -> name + ": " + violation.getMessage())
                .orElse(violation.getMessage());
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "request";
        }
        return name;
    }
}

