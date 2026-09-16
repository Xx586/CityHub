package com.cityhub.voucher;

import com.cityhub.config.CacheConfig;
import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.domain.entity.SeckillVoucher;
import com.cityhub.domain.entity.Voucher;
import com.cityhub.mapper.SeckillVoucherMapper;
import com.cityhub.mapper.VoucherMapper;
import com.cityhub.seckill.SeckillKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VoucherService {
    private static final String VOUCHER_CACHE_PREFIX = "cache:vouchers:shop:";

    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final Cache<Long, List<Voucher>> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CacheMode cacheMode;

    public VoucherService(VoucherMapper voucherMapper,
                          SeckillVoucherMapper seckillVoucherMapper,
                          Cache<Long, List<Voucher>> localCache,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry,
                          @Value("${cityhub.cache.mode:multilevel}") CacheMode cacheMode) {
        this.voucherMapper = voucherMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.localCache = localCache;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.cacheMode = cacheMode;
    }

    public List<Voucher> findByShopId(Long shopId) {
        return switch (cacheMode) {
            case database -> loadFromDatabase(shopId);
            case redis -> loadFromRedis(shopId);
            case multilevel -> loadFromMultiLevel(shopId);
        };
    }

    @Transactional
    public Long createSeckillVoucher(CreateSeckillVoucherRequest request) {
        if (!request.endTime().isAfter(request.beginTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        Voucher voucher = new Voucher();
        voucher.setShopId(request.shopId());
        voucher.setTitle(request.title());
        voucher.setSubTitle(request.subTitle() == null ? "" : request.subTitle());
        voucher.setRules(request.rules() == null ? "" : request.rules());
        voucher.setPayValue(request.payValue());
        voucher.setActualValue(request.actualValue());
        voucher.setType(1);
        voucher.setStatus(1);
        voucherMapper.insert(voucher);

        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(request.stock());
        seckillVoucher.setBeginTime(request.beginTime());
        seckillVoucher.setEndTime(request.endTime());
        seckillVoucherMapper.insert(seckillVoucher);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Long id = voucher.getId();
                redisTemplate.opsForValue().set(SeckillKeys.stock(id), request.stock().toString());
                redisTemplate.opsForValue().set(SeckillKeys.begin(id), epochMillis(request.beginTime()));
                redisTemplate.opsForValue().set(SeckillKeys.end(id), epochMillis(request.endTime()));
                evictShopVouchers(request.shopId());
            }
        });
        return voucher.getId();
    }

    public void evictShopVouchers(Long shopId) {
        localCache.invalidate(shopId);
        redisTemplate.delete(VOUCHER_CACHE_PREFIX + shopId);
        redisTemplate.convertAndSend(CacheConfig.VOUCHER_INVALIDATION_CHANNEL, shopId.toString());
    }

    private List<Voucher> loadFromMultiLevel(Long shopId) {
        List<Voucher> local = localCache.getIfPresent(shopId);
        if (local != null) {
            meterRegistry.counter("cityhub.cache.requests", "layer", "caffeine", "result", "hit").increment();
            return local;
        }
        meterRegistry.counter("cityhub.cache.requests", "layer", "caffeine", "result", "miss").increment();
        List<Voucher> result = loadFromRedis(shopId);
        localCache.put(shopId, result);
        return result;
    }

    private List<Voucher> loadFromRedis(Long shopId) {
        String key = VOUCHER_CACHE_PREFIX + shopId;
        String json = redisTemplate.opsForValue().get(key);
        if (StringUtils.hasText(json)) {
            meterRegistry.counter("cityhub.cache.requests", "layer", "redis-voucher", "result", "hit").increment();
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (JsonProcessingException exception) {
                redisTemplate.delete(key);
            }
        }
        meterRegistry.counter("cityhub.cache.requests", "layer", "redis-voucher", "result", "miss").increment();
        List<Voucher> vouchers = loadFromDatabase(shopId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vouchers),
                    Duration.ofMinutes(10).plusSeconds(ThreadLocalRandom.current().nextInt(121)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize voucher list", exception);
        }
        return vouchers;
    }

    private List<Voucher> loadFromDatabase(Long shopId) {
        meterRegistry.counter("cityhub.cache.requests", "layer", "database", "result", "load").increment();
        return voucherMapper.selectByShopId(shopId);
    }

    private String epochMillis(java.time.LocalDateTime value) {
        return Long.toString(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }
}
