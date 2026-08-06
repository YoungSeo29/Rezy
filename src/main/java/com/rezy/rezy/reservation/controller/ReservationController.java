package com.rezy.rezy.reservation.controller;

import com.rezy.rezy.reservation.dto.MyReservationResponse;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.dto.ReservationListType;
import com.rezy.rezy.reservation.dto.ReservationResponse;
import com.rezy.rezy.reservation.service.ReservationService;
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

    // 예약하기- slotCapacityId로 예약
    @PostMapping
    public synchronized ResponseEntity<ReservationResponse> reserve(
            Authentication authentication,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        ReservationResponse response = reservationService.reserve(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<MyReservationResponse>> getMyReservations(
            Authentication authentication,
            @RequestParam(defaultValue = "UPCOMING") ReservationListType type
    ) {
        return ResponseEntity.ok(reservationService.getMyReservations(authentication.getName(), type));
    }
}
