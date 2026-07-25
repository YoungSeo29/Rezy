package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query("select count(r) > 0 from Reservation r " +
            "where r.user.userId = :userId " +
            "and r.slot.slotDatetime >= :dayStart and r.slot.slotDatetime < :dayEnd " +
            "and r.status <> :excluded")
    boolean existsActiveOnDate(@Param("userId") String userId,
                               @Param("dayStart") LocalDateTime dayStart,
                               @Param("dayEnd") LocalDateTime dayEnd,
                               @Param("excluded") ReservationStatus excluded);

    // 다가오는 예약 — 아직 방문하지 않은 확정 예약을 가까운 시간 순으로 반환
    @Query("select r from Reservation r " +
            "join r.slot s " +
            "where r.user.userId = :userId " +
            "and s.slotDatetime >= :now " +
            "and r.status = :status " +
            "order by s.slotDatetime asc")
    List<Reservation> findUpcoming(@Param("userId") String userId,
                                   @Param("now") LocalDateTime now,
                                   @Param("status") ReservationStatus status);

    // 지난 예약 — 이미 시간이 지난 확정 예약을 최신순으로 반환
    @Query("select r from Reservation r " +
            "join r.slot s " +
            "where r.user.userId = :userId " +
            "and s.slotDatetime < :now " +
            "and r.status = :status " +
            "order by s.slotDatetime desc")
    List<Reservation> findPast(@Param("userId") String userId,
                               @Param("now") LocalDateTime now,
                               @Param("status") ReservationStatus status);

}
