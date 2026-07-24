package com.rezy.rezy.store.dto;

import com.rezy.rezy.store.domain.Store;
import lombok.Getter;

@Getter
public class StoreResponse {

    private final String storeId;
    private final String storeName;
    private final String address;
    private final String businessHours;
    private final String managerId;

    public StoreResponse(String storeId, String storeName, String address, String businessHours, String managerId) {
        this.storeId = storeId;
        this.storeName = storeName;
        this.address = address;
        this.businessHours = businessHours;
        this.managerId = managerId;
    }

    // Store 엔티티를 응답 DTO로 변환
    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getStoreId(),
                store.getStoreName(),
                store.getAddress(),
                store.getBusinessHours(),
                store.getManager().getUserId()
        );
    }
}
