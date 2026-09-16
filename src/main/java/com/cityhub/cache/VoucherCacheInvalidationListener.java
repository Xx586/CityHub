package com.cityhub.cache;

import com.cityhub.domain.entity.Voucher;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VoucherCacheInvalidationListener implements MessageListener {
    private final Cache<Long, List<Voucher>> voucherLocalCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            voucherLocalCache.invalidate(Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8)));
        } catch (NumberFormatException ignored) {
            // Ignore malformed invalidation events; the short local TTL remains the consistency fallback.
        }
    }
}

