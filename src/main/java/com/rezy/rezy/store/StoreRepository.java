package com.rezy.rezy.store;

import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, String> {

    boolean existsByManager(User manager);
}
