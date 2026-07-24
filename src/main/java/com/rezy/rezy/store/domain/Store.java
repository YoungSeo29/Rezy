package com.rezy.rezy.store.domain;

import com.rezy.rezy.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    @Column(name = "store_id", columnDefinition = "VARCHAR(36)")
    private String storeId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "business_hours", nullable = false)
    private String businessHours;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @PrePersist
    public void prePersist() {
        if (storeId == null) {
            storeId = UUID.randomUUID().toString();
        }
    }

    // 가게 등록 시 호출 — 새 Store 객체를 만들어 반환 (아직 DB 저장 전 상태)
    public static Store create(String storeName, String address,
                               String businessHours, User manager) {
        Store store = new Store();
        store.storeName = storeName;
        store.address = address;
        store.businessHours = businessHours;
        store.manager = manager;
        return store;
    }

}
