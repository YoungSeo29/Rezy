package com.rezy.rezy.reservation.repository.projection;

// 조회 전용 결과를 담는 그릇(?)
// 실제로 예약된 수
public interface CapacityConfirmedCountView {
    String getCapacityId();   // 집계 대상 버킷 ID
    long getConfirmedCount();   // 그 버킷의 CONFIRMED 예약 수
}