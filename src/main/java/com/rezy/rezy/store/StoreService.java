package com.rezy.rezy.store;

import com.rezy.rezy.global.exception.NotFoundException;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.store.dto.StoreCreateRequest;
import com.rezy.rezy.store.dto.StoreResponse;
import com.rezy.rezy.store.dto.StoreSummaryResponse;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다."));

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

    // 메인 화면 가게 목록 - 가게가 늘어나도 한 화면치만 읽도록 페이징
    @Transactional(readOnly = true)
    public Page<StoreSummaryResponse> getStores(Pageable pageable) {
        return storeRepository.findAll(pageable).map(StoreSummaryResponse::from);
    }

    // 가게 선택 시 상세 - 여기서 달력(슬롯 조회)으로 이어진다
    @Transactional(readOnly = true)
    public StoreResponse getStore(String storeId) {

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));

        return StoreResponse.from(store);
    }
}
