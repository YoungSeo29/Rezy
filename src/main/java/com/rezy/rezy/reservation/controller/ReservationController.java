package com.rezy.rezy.reservation.controller;

import com.rezy.rezy.reservation.dto.MyReservationResponse;
import com.rezy.rezy.reservation.dto.MyWaitingResponse;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.dto.ReservationListType;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.dto.ReserveResponse;
import com.rezy.rezy.reservation.service.ReservationService;
import com.rezy.rezy.reservation.service.WaitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final WaitingService waitingService;

    // 예약하기- slotCapacityId로 예약
    @PostMapping
    public ResponseEntity<ReserveResponse> reserve(
            Authentication authentication,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReserveResponse response = reservationService.reserve(authentication.getName(), request);

        // 예약이 바로 생기면 201(created), 만석이라 줄만 서면 202(accepted)
        HttpStatus status = (response.getReservation() != null) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    // 대기 확정 - 자리를 넘겨받은(OFFERED) 뒤 5분 안에 눌러야 한다
    @PostMapping("/waitings/{waitingId}/confirm")
    public ResponseEntity<ReservationResponse> confirmWaiting(
            Authentication authentication,
            @PathVariable String waitingId
    ) {
        ReservationResponse response = waitingService.confirm(authentication.getName(), waitingId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 내 대기 목록 - 지금 몇 번째인지 / 내 차례인지
    @GetMapping("/waitings/me")
    public ResponseEntity<List<MyWaitingResponse>> getMyWaitings(Authentication authentication) {
        return ResponseEntity.ok(waitingService.getMyWaitings(authentication.getName()));
    }

    // 예약 취소
    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            Authentication authentication,
            @PathVariable String reservationId
    ) {
        reservationService.cancelReservation(authentication.getName(), reservationId);

        return ResponseEntity.noContent().build();
    }

    // 마이페이지
    @GetMapping("/me")
    public ResponseEntity<List<MyReservationResponse>> getMyReservations(
            Authentication authentication,
            @RequestParam(defaultValue = "UPCOMING") ReservationListType type
    ) {
        return ResponseEntity.ok(reservationService.getMyReservations(authentication.getName(), type));
    }
}
