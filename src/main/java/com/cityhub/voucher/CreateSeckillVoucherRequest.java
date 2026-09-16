package com.cityhub.voucher;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateSeckillVoucherRequest(
        @NotNull Long shopId,
        @NotBlank String title,
        String subTitle,
        String rules,
        @NotNull @Min(0) Long payValue,
        @NotNull @Min(0) Long actualValue,
        @NotNull @Min(1) Integer stock,
        @NotNull @FutureOrPresent LocalDateTime beginTime,
        @NotNull @Future LocalDateTime endTime
) {
}
