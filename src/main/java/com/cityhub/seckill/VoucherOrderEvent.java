package com.cityhub.seckill;

import java.time.Instant;

public record VoucherOrderEvent(
        String eventId,
        int eventVersion,
        Long orderId,
        Long userId,
        Long voucherId,
        Instant createdAt) {
}

