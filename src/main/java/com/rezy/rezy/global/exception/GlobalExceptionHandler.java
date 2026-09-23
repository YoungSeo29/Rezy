package com.rezy.rezy.global.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 규칙 위반(이미 존재 등) → 409 Conflict + JSON
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }

    // 잘못된 입력값 → 400 Bad Request + JSON
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    // @Valid 검증 실패 → 400 + 어떤 필드가 왜 틀렸는지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }

    // DB 유니크 제약 위반(같은 날 중복 예약) → 409 Conflict
    // 동시 요청이라 애플리케이션 검사를 통과했더라도 DB 가 최종적으로 막는다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "같은 날짜에는 하루에 한 건만 예약할 수 있습니다."));
    }

}
