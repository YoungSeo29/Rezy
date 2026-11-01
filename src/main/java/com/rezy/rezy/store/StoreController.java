package com.rezy.rezy.store;

import com.rezy.rezy.store.dto.StoreCreateRequest;
import com.rezy.rezy.store.dto.StoreResponse;
import com.rezy.rezy.store.dto.StoreSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @PostMapping
    public ResponseEntity<StoreResponse> registerStore(
            Authentication authentication,
            @Valid @RequestBody StoreCreateRequest request
    ) {

        StoreResponse response = storeService.registerStore(authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    // 메인 화면 - 가게 목록. 정렬/페이지는 ?page=0&size=20&sort=storeName 으로 바꿀 수 있다
    @GetMapping
    public ResponseEntity<PagedModel<StoreSummaryResponse>> getStores(
            @PageableDefault(size = 20, sort = "storeName") Pageable pageable
    ) {
        // Page 를 그대로 내보내면 응답 JSON 구조가 보장되지 않는다. PagedModel 로 감싼다
        return ResponseEntity.ok(new PagedModel<>(storeService.getStores(pageable)));
    }

    // 가게 선택 - 상세
    @GetMapping("/{storeId}")
    public ResponseEntity<StoreResponse> getStore(@PathVariable String storeId) {

        return ResponseEntity.ok(storeService.getStore(storeId));
    }
}
