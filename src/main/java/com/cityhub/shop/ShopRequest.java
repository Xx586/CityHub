package com.cityhub.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ShopRequest(
        @NotBlank(message = "商铺名称不能为空") String name,
        @NotNull(message = "商铺类型不能为空") @Positive(message = "商铺类型错误") Long typeId,
        String images,
        String area,
        String address,
        Double x,
        Double y,
        Long avgPrice,
        String openHours) {
}

