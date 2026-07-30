package com.rezy.rezy.reservation.controller;

import com.rezy.rezy.reservation.dto.DailySlotResponse;
import com.rezy.rezy.reservation.service.ReservationSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stores/{storeId}/slots")
@RequiredArgsConstructor
public class SlotQueryController {

    private final ReservationSlotService reservationSlotService;

    // 날짜별 슬롯 조회 - 달력에서 날짜 클릭하면 호출 (시간대, 잔여 인원 반환)
    @GetMapping
    public ResponseEntity<DailySlotResponse> getSlotsByDate(
            @PathVariable String storeId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reservationSlotService.getSlotsByDate(storeId, date));
    }

    // 예약 가능 날짜 목록 - 달력 처음 그릴 때 호출. 슬롯 있는 날짜들 반환
    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> getAvailableDates(
            @PathVariable String storeId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(reservationSlotService.getAvailableDates(storeId, year, month));
    }
}
