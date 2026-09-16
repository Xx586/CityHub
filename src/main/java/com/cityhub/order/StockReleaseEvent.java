package com.cityhub.order;

public record StockReleaseEvent(Long orderId, Long userId, Long voucherId) {
}

