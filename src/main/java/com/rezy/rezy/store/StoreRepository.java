package com.rezy.rezy.store;

import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, String> {

    boolean existsByManager(User manager);

    // 스케줄 생성 대상 가게를 찾기 위함
    Optional<Store> findByManagerUserId(String managerId);
}
