package com.rezy.rezy.reservation.controller;

import com.rezy.rezy.reservation.service.ReservationSlotService;
import com.rezy.rezy.reservation.dto.ScheduleCreateRequest;
import com.rezy.rezy.reservation.dto.ScheduleCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores/me/schedule")
@RequiredArgsConstructor
public class ReservationSlotController {

    private final ReservationSlotService reservationSlotService;

    // 예약 스케쥴 생성 - 로그인 한 사장님 가게에 설정대로 슬롯들 만들고, 요약 반환
    @PostMapping
    public ResponseEntity<ScheduleCreateResponse> create(
            Authentication authentication,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {

        ScheduleCreateResponse response = reservationSlotService.createSchedule(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
