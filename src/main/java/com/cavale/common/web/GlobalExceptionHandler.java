package com.cavale.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import com.cavale.common.exception.ConflictException;
import com.cavale.common.exception.ResourceNotFoundException;
import com.cavale.demo.DemoUnavailableException;
import com.cavale.integration.intervals.IntervalsException;
import com.cavale.integration.strava.StravaException;
import com.cavale.training.service.PlanImportException;
import com.cavale.user.service.EmailAlreadyUsedException;
import com.cavale.user.service.InvalidCredentialsException;
import com.cavale.user.service.InvalidRefreshTokenException;
import com.cavale.user.service.UserNotFoundException;

/**
 * Central exception → HTTP mapping. Returns RFC 9457 problem-details bodies.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Bean Validation raised on a SERVICE method rather than a controller
     * argument — the MCP front door calls services directly, so its inputs
     * only meet the constraints here. Same body as the controller case so both
     * doors answer identically.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setDetail("One or more fields are invalid.");

        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath().toString();
            errors.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Email already in use");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Authentication failed");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Session expired");
        problem.setDetail("Sign in again to continue.");
        return problem;
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("User not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Not allowed");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(PlanImportException.class)
    ProblemDetail handlePlanImport(PlanImportException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Import failed");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(DemoUnavailableException.class)
    ProblemDetail handleDemoUnavailable(DemoUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Demo unavailable");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(StravaException.class)
    ProblemDetail handleStrava(StravaException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Strava");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(IntervalsException.class)
    ProblemDetail handleIntervals(IntervalsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Intervals.icu");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Safety net for constraint violations that race past application-level
     * checks (e.g. two concurrent registrations with the same email — the
     * unique constraint is the real guarantee).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail("The request conflicts with existing data.");
        return problem;
    }
}
