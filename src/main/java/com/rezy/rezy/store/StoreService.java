package com.rezy.rezy.store;

import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.store.dto.StoreCreateRequest;
import com.rezy.rezy.store.dto.StoreResponse;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    @Transactional
    public StoreResponse registerStore(String managerId, StoreCreateRequest request) {

        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if(manager.getRole() != UserRole.MANAGER) {
            throw new IllegalStateException("가게 등록은 사장님 권한이 필요합니다.");
        }

        // 사장님 1명당 가게 1개 제한
        if (storeRepository.existsByManager(manager)) {
            throw new IllegalStateException("이미 등록한 가게가 있습니다. 사장님당 가게는 하나만 등록할 수 있습니다.");
        }

        Store store = Store.create( request.getStoreName(), request.getAddress(), request.getBusinessHours(), manager );

        storeRepository.save(store);

        return StoreResponse.from(store);


    }
}
