package com.rezy.rezy.reservation.repository.projection;

// 조회 전용 결과를 담는 그릇(?)
// 검증 대상 버킷 - 엔티티 전체가 아니라 필요한 두 값만 꺼낸다
// 검사 대상 목록. 오늘 ~ 7일 후 까지만
// 각각 정원이 몇명인지..
public interface CapacityStockView {
    String getSlotCapacityId();   // 검사 대상 버킷 ID
    int getTotalTeams();   // 해당 버킷의 총 정원 (불변)
}