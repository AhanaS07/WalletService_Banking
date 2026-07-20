package com.tnf.wallet_service.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tnf.common_dto.dto.common.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

// Exceptions to meaningful HTTP responses, using the shared ErrorResponse contract.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WalletNotFoundException ex, HttpServletRequest request) {
        logger.warn("Wallet not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({ InvalidAmountException.class, InsufficientBalanceException.class,
            WalletLimitExceededException.class })
    // 422 Unprocessable Entity: request was well-formed but a wallet business rule rejected it.
    public ResponseEntity<ErrorResponse> handleBusinessRule(RuntimeException ex, HttpServletRequest request) {
        logger.warn("Wallet operation rejected: {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // A transfer that failed. If the debit was rolled back (reconciled) no money moved -> 409
    // Conflict (safe to retry). If rollback also failed the state is inconsistent -> 500.
    @ExceptionHandler(WalletTransferException.class)
    public ResponseEntity<ErrorResponse> handleTransferFailure(WalletTransferException ex, HttpServletRequest request) {
        HttpStatus status = ex.isReconciled() ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
        logger.error("Transfer failed (reconciled={}): {}", ex.isReconciled(), ex.getMessage());
        return build(status, ex.getMessage(), request);
    }

    // Rejects an unknown walletType string (WalletType.valueOf) and other bad arguments.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        logger.warn("Invalid request argument: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // Bean-validation failures on @Valid request bodies; field errors are flattened into the message.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("Validation failed: {}", message);
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
