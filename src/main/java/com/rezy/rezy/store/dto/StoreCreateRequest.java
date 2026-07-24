package com.rezy.rezy.store.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class StoreCreateRequest {

    @NotBlank
    private String storeName;

    @NotBlank
    private String address;

    @NotBlank
    private String businessHours;
}
