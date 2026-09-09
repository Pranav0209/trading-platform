package com.zkuba.trading.exception;

import com.zkuba.trading.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            GlobalException exception,
            HttpServletRequest request) {
        logger.warn("Global exception occurred: {} - {}", exception.getCode(), exception.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            exception.getStatus().value(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(exception.getStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        
        List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                    error.getField(),
                    error.getDefaultMessage()
                ))
                .toList();

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        logger.debug("Validation error: {}", message);

        ErrorResponse response = ErrorResponse.withFieldErrors(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "VAL-422",
            message,
            request.getRequestURI(),
            fieldErrors
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        
        logger.warn("Constraint violation: {}", exception.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "VAL-422",
            exception.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        
        String message = "Invalid parameter: " + exception.getName() + 
                        ". Expected type: " + exception.getRequiredType().getSimpleName();
        logger.warn("Type mismatch: {}", message);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "VAL-422",
            message,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        
        logger.error("Data integrity violation: {}", exception.getMessage(), exception);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "ORD-409",
            "Data conflict occurred. Unable to process request due to database constraint violation.",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        
        logger.error("Unexpected exception occurred", exception);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "SYS-500",
            "Internal server error. Please contact support if the problem persists.",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}