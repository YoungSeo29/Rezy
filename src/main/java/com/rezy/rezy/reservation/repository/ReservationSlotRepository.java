package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, String> {

    boolean existsByStore(Store store);
}
