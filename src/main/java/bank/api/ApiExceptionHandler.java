package bank.api;

import bank.AccountNotFoundException;
import bank.DuplicateAccountException;
import bank.InsufficientFundsException;
import bank.InvalidAccountIdException;
import bank.InvalidAmountException;
import bank.InvalidTransferException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain errors to HTTP statuses. Anything not listed here is a bug and
 * is left to Spring's default 500 so internals are not leaked to the caller.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorResponse(String error, String message) {
    }

    @ExceptionHandler({InvalidAmountException.class, InvalidAccountIdException.class, InvalidTransferException.class})
    public ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadableBody(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is missing or not valid JSON");
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(AccountNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ErrorResponse> conflict(DuplicateAccountException e) {
        return respond(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> insufficientFunds(InsufficientFundsException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", e.getMessage());
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(error, message));
    }
}
