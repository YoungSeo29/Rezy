package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, String> {

    boolean existsByStore(Store store);

    // 특정 가게의 특정 기간 슬롯을 시간순으로 조회 - 날짜별 예약 현황 화면용
    List<ReservationSlot> findByStoreAndSlotDatetimeBetweenOrderBySlotDatetimeAsc(Store store, LocalDateTime start, LocalDateTime end);

    // 특정 기간에 슬롯이 존재하는 시각들만 조회 — 달력에 표시할 "예약 가능 날짜" 계산용
    @Query("select s.slotDatetime from ReservationSlot s " +
            "where s.store = :store " +
            "and s.slotDatetime >= :start and s.slotDatetime <= :end")
    List<LocalDateTime> findSlotDatetimes(@Param("store") Store store,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}
