package com.rezy.rezy.reservation.domain;

public enum WaitingStatus {
    WAITING,    // 줄 서는 중
    OFFERED,    // 자리를 넘겨받음. offer_expires_at 전에 확정해야 한다
    CONFIRMED,  // 확정 → reservations 에 CONFIRMED 행이 생김
    EXPIRED     // 시간 안에 확정 안 함 → 다음 번호로 넘어감
}
