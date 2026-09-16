package com.cityhub.order;

import com.cityhub.domain.enums.VoucherOrderStatus;

import java.time.LocalDateTime;

public record OrderView(
        Long id,
        Long voucherId,
        VoucherOrderStatus status,
        String processingStatus,
        LocalDateTime createTime,
        LocalDateTime payTime,
        LocalDateTime cancelTime) {
}

