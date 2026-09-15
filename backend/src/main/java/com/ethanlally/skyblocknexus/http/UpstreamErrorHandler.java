package com.ethanlally.skyblocknexus.http;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class UpstreamErrorHandler {

    @ExceptionHandler(UpstreamResponseException.class)
    public ResponseEntity<UpstreamError> invalidResponse(UpstreamResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new UpstreamError(exception.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<UpstreamError> requestFailed(RestClientException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(new UpstreamError("The upstream service took too long to respond. Please try again."));
            }
        }
        // Do not expose upstream bodies, request URLs, or credentials to the browser.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new UpstreamError("The upstream service could not complete the request. Please try again later."));
    }

    public record UpstreamError(String message) {}
}
