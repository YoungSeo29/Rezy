package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.SlotCapacity;
import com.rezy.rezy.reservation.repository.projection.CapacityStockView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlotCapacityRepository extends JpaRepository<SlotCapacity, String> {

    // 비관적 락으로 인원 버킷 조회 - SELECT ... FOR UPDATE
    // 조회 시점에 해당 행만 잠금
    // 같은 버킷 노리는 요청은 대기, 다른 버킷을 향한 요청은 그대로 통과
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sc from SlotCapacity  sc where sc.slotCapacityId = :id")
    Optional<SlotCapacity> findByIdForUpdate(@Param("id") String id);

    // 하루 1건 검증에 필요한 날짜만 조회 (락 없음)
    @Query("select sc.slot.slotDatetime from SlotCapacity sc where sc.slotCapacityId = :id")
    Optional<LocalDateTime> findSlotDatetimeById(@Param("id") String id);

    // 조회 없이 조건부 차감 한방에
    // 원자적 update
    @Modifying
    @Query("update SlotCapacity sc set sc.remainingTeams = sc.remainingTeams -1 where sc.slotCapacityId = :id and sc.remainingTeams >0")
    int decreaseRemaining(@Param("id") String id);

    // Redis에 재고를 처음 올릴 때 쓸 초기 값
    // Entity 전체가 아니라 숫자 하나만 가져옴
    @Query("select sc.remainingTeams from SlotCapacity sc where sc.slotCapacityId = :id")
    Optional<Integer> findRemainingTeamsById(@Param("id") String id);

    @Query("select c.totalTeams from SlotCapacity c where c.slotCapacityId = :id")
    Optional<Integer> findTotalTeamsById(@Param("id") String id);

    // 정합성 검증 대상 버킷 조회 - 지정한 기간 안의 슬롯에 달린 것만
    // 지난 슬롯은 제외
    @Query("select sc.slotCapacityId as slotCapacityId, sc.totalTeams as totalTeams " +
            "from SlotCapacity sc join sc.slot s " +
            "where s.slotDatetime >= :from and s.slotDatetime < :to")
    List<CapacityStockView> findStockViewsBySlotDatetimeRange(@Param("from") LocalDateTime from,
                                                              @Param("to") LocalDateTime to);
}
