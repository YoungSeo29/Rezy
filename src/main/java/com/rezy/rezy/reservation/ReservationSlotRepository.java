package com.rezy.rezy.reservation;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, String> {

    boolean existsByStore(Store store);
}
