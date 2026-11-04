package com.rezy.rezy.reservation.batch;

import com.rezy.rezy.reservation.repository.WaitingEntryRepository;
import com.rezy.rezy.reservation.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// 확정 시간이 지난 OFFERED 를 EXPIRED 로 바꾸고 자리를 다음 번호에게 넘긴다
// 10초에 한번씩, 마감시간 지났는데 예약 확정 버튼 안누른 사람 찾기
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingQueueScheduler {

    private final WaitingEntryRepository waitingEntryRepository;
    private final WaitingService waitingService;

    // trx 안걺. 건마다 짧은 trx 로 처리 - 커넥션 오래 쥐면 API랑 경쟁
    @Scheduled(fixedDelayString = "${app.waiting-queue.interval-ms:10000}")
    public void expireOffers() {

        List<String> expiredIds = waitingEntryRepository.findExpiredOfferIds(LocalDateTime.now());

        // 대상 없으면 바로 끝
        if (expiredIds.isEmpty()) {
            return;
        }

        // expired: 마감 지났는데 확정 안누른 사람
        // skipped: 아슬아슬하게 confirmed
        // failed: 처리하다가 에러. 다음 주기에 다시 시도
        int expired = 0, skipped = 0, failed = 0;

        for (String waitingId : expiredIds) {
            try {
                if (waitingService.expireOffer(waitingId)) {
                    expired++;
                } else {
                    // 목록을 읽은 뒤 확정이 끼어든 경우
                    skipped++;
                }
            } catch (RuntimeException e) {
                // 한 건 실패로 나머지까지 멈추지 않는다. 다음 주기에 다시 잡힌다
                failed++;
                log.warn("[대기열] 만료 처리 실패 waitingId={}", waitingId, e);
            }
        }

        log.info("[대기열] 만료 {} / 스킵 {} / 실패 {}", expired, skipped, failed);
    }
}
