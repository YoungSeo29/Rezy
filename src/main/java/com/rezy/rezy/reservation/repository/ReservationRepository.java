package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import com.rezy.rezy.reservation.repository.projection.CapacityHeldCountView;
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

    // Confirmed 예약 수 세기
    @Query("select count(r) from Reservation r " +
            "where r.capacity.slotCapacityId = :capacityId and r.status = :status")
    long countByCapacityAndStatus(@Param("capacityId") String capacityId,
                                  @Param("status") ReservationStatus status);

    // 버킷별 잡힌 자리 수(CONFIRMED 예약 + OFFERED 대기)를 쿼리 한 번으로 집계
    @Query("select sc.slotCapacityId as capacityId, " +
            "((select count(r) from Reservation r where r.capacity = sc and r.status = :confirmed) " +
            "+ (select count(w) from WaitingEntry w where w.capacity = sc and w.status = :offered)) as heldCount " +
            "from SlotCapacity sc " +
            "where sc.slotCapacityId in :capacityIds")
    List<CapacityHeldCountView> countHeldByCapacity(@Param("capacityIds") List<String> capacityIds,
                                                    @Param("confirmed") ReservationStatus confirmed,
                                                    @Param("offered") WaitingStatus offered);

}
