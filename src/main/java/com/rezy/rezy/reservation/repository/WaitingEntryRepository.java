package com.rezy.rezy.reservation.repository;

import com.rezy.rezy.reservation.domain.WaitingEntry;
import com.rezy.rezy.reservation.domain.WaitingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitingEntryRepository extends JpaRepository<WaitingEntry, String> {

    @Query("select count(w) from WaitingEntry w " +
            "where w.capacity.slotCapacityId = :capacityId and w.status = :status")
    long countByCapacityAndStatus(@Param("capacityId") String capacityId,
                                  @Param("status") WaitingStatus status);

    // 같은 버킷 줄에 이미 서 있는지 (번호표 중복 방지)
    @Query("select count(w) > 0 from WaitingEntry w " +
            "where w.user.userId = :userId and w.capacity.slotCapacityId = :capacityId " +
            "and w.status in :statuses")
    boolean existsInLine(@Param("userId") String userId,
                         @Param("capacityId") String capacityId,
                         @Param("statuses") List<WaitingStatus> statuses);

    // 줄 맨 앞 - 버킷 행 락을 쥔 뒤에 부른다
    // FOR UPDATE 인 이유 : 일반 SELECT 는 트랜잭션 스냅샷을 읽어서,
    // 버킷 락을 기다리는 사이 다른 취소가 승급시킨 1번을 여전히 WAITING 으로 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitingEntry w " +
            "where w.capacity.slotCapacityId = :capacityId and w.status = :status " +
            "order by w.createdAt asc, w.waitingId asc")
    List<WaitingEntry> findLineForUpdate(@Param("capacityId") String capacityId,
                                         @Param("status") WaitingStatus status,
                                         Pageable pageable);

    @Query("select w.capacity.slotCapacityId from WaitingEntry w where w.waitingId = :id")
    Optional<String> findCapacityIdById(@Param("id") String id);

    // 확정 - 아직 OFFERED 이고 시간 안일 때만. 0건이면 이미 만료됐거나 다음으로 넘어간 것
    @Modifying
    @Query("update WaitingEntry w set w.status = com.rezy.rezy.reservation.domain.WaitingStatus.CONFIRMED " +
            "where w.waitingId = :id " +
            "and w.status = com.rezy.rezy.reservation.domain.WaitingStatus.OFFERED " +
            "and w.offerExpiresAt > :now")
    int confirmOffer(@Param("id") String id, @Param("now") LocalDateTime now);

    // 만료 - 확정과 조건이 겹치지 않으므로 둘 중 하나만 1건이 된다
    @Modifying
    @Query("update WaitingEntry w set w.status = com.rezy.rezy.reservation.domain.WaitingStatus.EXPIRED " +
            "where w.waitingId = :id " +
            "and w.status = com.rezy.rezy.reservation.domain.WaitingStatus.OFFERED " +
            "and w.offerExpiresAt <= :now")
    int expireOffer(@Param("id") String id, @Param("now") LocalDateTime now);

    @Query("select w.waitingId from WaitingEntry w " +
            "where w.status = com.rezy.rezy.reservation.domain.WaitingStatus.OFFERED " +
            "and w.offerExpiresAt <= :now")
    List<String> findExpiredOfferIds(@Param("now") LocalDateTime now);
}
