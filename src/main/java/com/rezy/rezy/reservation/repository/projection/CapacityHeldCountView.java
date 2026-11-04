package com.rezy.rezy.reservation.repository.projection;

// 조회 전용 결과를 담는 그릇
// 잡힌 자리 수 = CONFIRMED 예약 + OFFERED 대기
// OFFERED 는 대기 1번에게 넘겨둔 자리라 Redis 재고에도, CONFIRMED 에도 없다
public interface CapacityHeldCountView {
    String getCapacityId();   // 집계 대상 버킷 ID
    long getHeldCount();      // 그 버킷에서 이미 주인이 정해진 자리 수
}
