package com.ethanlally.skyblocknexus.hypixel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HypixelNotFoundHandler {

    @ExceptionHandler(HypixelDataNotFoundException.class)
    ResponseEntity<NotFoundError> handleNotFound(HypixelDataNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new NotFoundError(exception.getMessage()));
    }

    public record NotFoundError(String message) {}
}
