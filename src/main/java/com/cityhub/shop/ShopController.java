package com.cityhub.shop;

import com.cityhub.common.ApiResponse;
import com.cityhub.domain.entity.Shop;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {
    private final ShopService shopService;

    @GetMapping("/{id}")
    public ApiResponse<Shop> findById(@PathVariable Long id) {
        return ApiResponse.ok(shopService.findById(id));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody ShopRequest request) {
        return ApiResponse.ok(shopService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ShopRequest request) {
        shopService.update(id, request);
        return ApiResponse.ok();
    }
}

