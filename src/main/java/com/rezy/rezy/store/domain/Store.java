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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", nullable = false)
    private User manager;

    @PrePersist
    public void prePersist() {
        if (storeId == null) {
            storeId = UUID.randomUUID().toString();
        }
    }

}
