package com.cityhub.config;

import com.cityhub.cache.VoucherCacheInvalidationListener;
import com.cityhub.domain.entity.Voucher;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;

@Configuration
public class CacheConfig {
    public static final String VOUCHER_INVALIDATION_CHANNEL = "cache:invalidation:voucher";

    @Bean
    Cache<Long, List<Voucher>> voucherLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(5_000)
                .expireAfterWrite(Duration.ofSeconds(5))
                .recordStats()
                .build();
    }

    @Bean
    TaskExecutor cacheRebuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cache-rebuild-");
        executor.initialize();
        return executor;
    }

    @Bean
    RedisMessageListenerContainer cacheMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            VoucherCacheInvalidationListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(VOUCHER_INVALIDATION_CHANNEL));
        return container;
    }
}

