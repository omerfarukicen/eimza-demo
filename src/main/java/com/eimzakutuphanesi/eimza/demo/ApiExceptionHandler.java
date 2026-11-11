package com.eimzakutuphanesi.eimza.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.eimzakutuphanesi.eimza.EimzaException;
import com.eimzakutuphanesi.eimza.PinException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PinException.class)
    public ResponseEntity<Map<String, String>> pin(PinException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({EimzaException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> business(RuntimeException e) {
        log.warn("İşlem başarısız: {}", e.getMessage(), e);
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Beklenmeyen hata", e);
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.internalServerError().body(Map.of("error", message));
    }
}
