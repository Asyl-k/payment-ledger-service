package dev.asyl.portfolio.paymentledger.common;

import dev.asyl.portfolio.paymentledger.account.AccountNotFoundException;
import dev.asyl.portfolio.paymentledger.transfer.CurrencyMismatchException;
import dev.asyl.portfolio.paymentledger.transfer.IdempotencyConflictException;
import dev.asyl.portfolio.paymentledger.transfer.InsufficientFundsException;
import dev.asyl.portfolio.paymentledger.transfer.InvalidIdempotencyKeyException;
import dev.asyl.portfolio.paymentledger.transfer.MissingIdempotencyKeyException;
import dev.asyl.portfolio.paymentledger.transfer.SameAccountException;
import dev.asyl.portfolio.paymentledger.transfer.SystemAccountRestrictedException;
import dev.asyl.portfolio.paymentledger.transfer.TransferNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request fields are invalid",
                "validation_failed"
        );
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                exception.getMessage(),
                "validation_failed"
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request body is missing or contains invalid JSON",
                "malformed_request"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid parameter",
                "Parameter '%s' has an invalid value".formatted(exception.getName()),
                "invalid_parameter"
        );
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleInvalidSort(PropertyReferenceException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid sort parameter",
                exception.getMessage(),
                "invalid_sort"
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Missing header",
                "The %s header is required".formatted(exception.getHeaderName()),
                "idempotency_key_required"
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Missing parameter",
                "Parameter '%s' is required".formatted(exception.getParameterName()),
                "missing_parameter"
        );
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", exception.getMessage(), "account_not_found");
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ProblemDetail handleTransferNotFound(TransferNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Transfer not found", exception.getMessage(), "transfer_not_found");
    }

    @ExceptionHandler(SameAccountException.class)
    public ProblemDetail handleSameAccount(SameAccountException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid transfer", exception.getMessage(), "same_account");
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ProblemDetail handleCurrencyMismatch(CurrencyMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Currency mismatch", exception.getMessage(), "currency_mismatch");
    }

    @ExceptionHandler(SystemAccountRestrictedException.class)
    public ProblemDetail handleSystemAccount(SystemAccountRestrictedException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Restricted account",
                exception.getMessage(),
                "system_account_restricted"
        );
    }

    @ExceptionHandler(InvalidAmountException.class)
    public ProblemDetail handleInvalidAmount(InvalidAmountException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid amount", exception.getMessage(), "invalid_amount");
    }

    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Missing idempotency key",
                exception.getMessage(),
                "idempotency_key_required"
        );
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail handleInvalidIdempotencyKey(InvalidIdempotencyKeyException exception) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Invalid idempotency key",
                exception.getMessage(),
                "idempotency_key_invalid"
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(IdempotencyConflictException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Idempotency conflict",
                exception.getMessage(),
                "idempotency_key_conflict"
        );
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "Insufficient funds",
                exception.getMessage(),
                "insufficient_funds"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataConflict(DataIntegrityViolationException exception) {
        log.debug("Data integrity violation", exception);
        return problem(
                HttpStatus.CONFLICT,
                "Data conflict",
                "The operation conflicts with existing data",
                "data_conflict"
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNotFound(NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not found", "The requested resource does not exist", "not_found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                exception.getMessage(),
                "method_not_allowed"
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred",
                "internal_error"
        );
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
