package com.salonplatform.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.LocaleResolver;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, resolve(ex.getMessage(), request), ex.getMessage(), request);
    }

    @ExceptionHandler({BadRequestException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            RuntimeException ex, HttpServletRequest request) {
        String code = ex instanceof BadCredentialsException ? "error.badCredentials" : ex.getMessage();
        return error(HttpStatus.BAD_REQUEST, resolve(code, request), code, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, resolve("error.forbidden", request), "error.forbidden", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        Locale locale = localeResolver.resolveLocale(request);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("code", "error.validation.failed");
        body.put("message", messageSource.getMessage("error.validation.failed", null, locale));
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, resolve("error.internal", request), "error.internal", request);
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message, String code, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private String resolve(String codeOrMessage, HttpServletRequest request) {
        if (codeOrMessage == null) {
            return messageSource.getMessage("error.internal", null, localeResolver.resolveLocale(request));
        }
        Locale locale = localeResolver.resolveLocale(request);
        if (codeOrMessage.startsWith("error.")) {
            return messageSource.getMessage(codeOrMessage, null, codeOrMessage, locale);
        }
        return codeOrMessage;
    }
}
