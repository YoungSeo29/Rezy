package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.Reservation;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    @Query("select count(r) > 0 from Reservation r " +
            "where r.user.userId = :userId " +
            "and r.slot.slotDatetime >= :dayStart and r.slot.slotDatetime < :dayEnd " +
            "and r.status <> :excluded")
    boolean existsActiveOnDate(@Param("userId") String userId,
                               @Param("dayStart") LocalDateTime dayStart,
                               @Param("dayEnd") LocalDateTime dayEnd,
                               @Param("excluded") ReservationStatus excluded);

}
