package com.rezy.rezy.store;

import com.rezy.rezy.store.dto.StoreCreateRequest;
import com.rezy.rezy.store.dto.StoreResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
}
