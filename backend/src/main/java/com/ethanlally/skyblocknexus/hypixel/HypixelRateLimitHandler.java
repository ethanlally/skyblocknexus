package com.ethanlally.skyblocknexus.hypixel;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HypixelRateLimitHandler {

    @ExceptionHandler(HypixelRateLimitException.class)
    ResponseEntity<RateLimitError> handleRateLimit(HypixelRateLimitException exception) {
        long retryAfterSeconds = exception.getRetryAfterSeconds();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(new RateLimitError(
                        "Hypixel's request limit has been reached. Try again in "
                                + retryAfterSeconds + " seconds.",
                        retryAfterSeconds));
    }

    public record RateLimitError(String message, long retryAfterSeconds) {}
}
