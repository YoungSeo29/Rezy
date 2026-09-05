package com.rezy.rezy.global.redis;

// 키 접두사를 두 곳(조회, 예약 시 재고 차감)에서 사용하기에 따로 뺌.
public final class RedisKeys {

    private RedisKeys() {}

    public static final String STOCK_PREFIX = "slot:cap:";

    public static String stock(String slotCapacityId) {
        return STOCK_PREFIX + slotCapacityId;
    }
}
