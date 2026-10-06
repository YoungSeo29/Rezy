package com.rezy.rezy.reservation.batch;

import com.rezy.rezy.global.redis.RedisKeys;
import com.rezy.rezy.reservation.domain.ReservationStatus;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.reservation.repository.projection.CapacityConfirmedCountView;
import com.rezy.rezy.reservation.repository.projection.CapacityStockView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// 10분마다 Redis 재고와 DB 기준값을 대조하고, 같은 불일치가 2주기 연속이면 CAS로 보정
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconciliationBatch {

    private final SlotCapacityRepository slotCapacityRepository;
    private final ReservationRepository reservationRepository;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> stockCasScript;

    // 며칠치 슬롯 검사할지
    private static final int LOOKAHEAD_DAYS = 7;

    // 같은 불일치가 두 번 연속일 때만 보정
    // 서버 재시작시 비워져도 ok. 어차피 다음 주기에 다시 관찰됨.
    private final Map<String, Mismatch> previousMismatches = new ConcurrentHashMap<>();

    // 직전에 본 불일치
    private record Mismatch(String redisValue, int expected) {}

    // trx 안걺. 커넥션 오래 쥐면 API랑 경쟁 가능성 잇음
    @Scheduled(fixedDelayString = "${app.stock-reconciliation.interval-ms:600000}")
    public void reconcile() {

        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(LOOKAHEAD_DAYS);

        // 1) 검사 대상 버킷 (쿼리 1회)
        List<CapacityStockView> targets = slotCapacityRepository.findStockViewsBySlotDatetimeRange(from, to);

        if(targets.isEmpty()) {
            previousMismatches.clear();
            log.info("[재고검증] 대상 없음");

            return;
        }

        List<String> capacityIds = targets.stream().map(CapacityStockView::getSlotCapacityId).toList();

        // 2) 버킷별 CONFIRMED 예약 수 (쿼리 1회)
        Map<String, Long> confirmedCounts = reservationRepository
                .countConfirmedGroupByCapacity(capacityIds, ReservationStatus.CONFIRMED)
                .stream()
                .collect(Collectors.toMap(CapacityConfirmedCountView::getCapacityId,
                        CapacityConfirmedCountView::getConfirmedCount));

        // 3) Redis 값 (왕복 1회)
        List<String> keys = capacityIds.stream().map(RedisKeys::stock).toList();
        List<String> redisValues = redisTemplate.opsForValue().multiGet(keys);

        int checked = 0, skipped = 0, mismatched = 0, corrected = 0, casFailed = 0;

        for (int i = 0; i < targets.size(); i++) {

            CapacityStockView target = targets.get(i);
            String capacityId = target.getSlotCapacityId();
            String redisValue = (redisValues == null) ? null : redisValues.get(i);

            // 키가 없으면 건너뛴다 - 다음 예약 때 loadStockIfAbsent 가 올바르게 만든다
            if (redisValue == null) {
                skipped++;
                previousMismatches.remove(capacityId);
                continue;
            }

            checked++;

            // 기대 재고 = 총정원 - CONFIRMED 예약 수
            int expected = target.getTotalTeams()
                    - confirmedCounts.getOrDefault(capacityId, 0L).intValue();

            // 값이 맞으면 과거 관찰 기록을 지운다 (연속 카운트 초기화)
            if (redisValue.equals(String.valueOf(expected))) {
                previousMismatches.remove(capacityId);
                continue;
            }

            mismatched++;
            Mismatch current = new Mismatch(redisValue, expected);
            Mismatch previous = previousMismatches.get(capacityId);

            // 직전 주기에 "똑같은 불일치" 를 본 적이 없으면 기록만 하고 넘어간다
            // 진행 중인 예약 때문에 생긴 일시적 착시는 다음 주기에 재현되지 않는다
            if (!current.equals(previous)) {
                log.warn("[재고검증] 불일치 관찰 capacityId={}, redis={}, 기대={}, 연속={}회",
                        capacityId, redisValue, expected, 1);
                previousMismatches.put(capacityId, current);
                continue;
            }

            log.warn("[재고검증] 불일치 재확인 capacityId={}, redis={}, 기대={}, 연속={}회",
                    capacityId, redisValue, expected, 2);

            // 4) CAS 보정 - 읽었던 값 그대로일 때만 교체
            Long result = redisTemplate.execute(stockCasScript,
                    List.of(RedisKeys.stock(capacityId)), redisValue, String.valueOf(expected));

            if (result != null && result == 1L) {
                corrected++;
                previousMismatches.remove(capacityId);
                log.warn("[재고검증] 보정 완료 capacityId={}, {} -> {}", capacityId, redisValue, expected);
            } else {
                // 읽은 뒤 사용자 예약이 끼어들어 값이 바뀐 경우. 다음 주기에 다시 본다
                casFailed++;
                previousMismatches.put(capacityId, current);
                log.warn("[재고검증] CAS 실패 - 그 사이 값이 바뀜 capacityId={}, 읽은값={}", capacityId, redisValue);
            }
        }

        // 이번 주기에 보지 않은 버킷의 기록은 버린다 (기간이 지나면 계속 쌓이므로)
        previousMismatches.keySet().retainAll(new HashSet<>(capacityIds));

        log.info("[재고검증] 검사 {} / 스킵 {} / 불일치 {} / 보정 {} / CAS실패 {}",
                checked, skipped, mismatched, corrected, casFailed);

    }


}
