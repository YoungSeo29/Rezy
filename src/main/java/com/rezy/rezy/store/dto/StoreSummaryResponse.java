package com.rezy.rezy.store.dto;

import com.rezy.rezy.store.domain.Store;
import lombok.Getter;

@Getter
public class StoreSummaryResponse {

    private final String storeId;
    private final String storeName;
    private final String address;
    private final String businessHours;

    public StoreSummaryResponse(String storeId, String storeName, String address, String businessHours) {
        this.storeId = storeId;
        this.storeName = storeName;
        this.address = address;
        this.businessHours = businessHours;
    }

    public static StoreSummaryResponse from(Store store) {
        return new StoreSummaryResponse(
                store.getStoreId(),
                store.getStoreName(),
                store.getAddress(),
                store.getBusinessHours()
        );
    }
}
