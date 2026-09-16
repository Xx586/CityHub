package com.cityhub.seckill;

import com.cityhub.domain.entity.SeckillVoucher;
import com.cityhub.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRedisInitializer {
    private final SeckillVoucherMapper voucherMapper;
    private final StringRedisTemplate redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        for (SeckillVoucher voucher : voucherMapper.selectList(null)) {
            Long id = voucher.getVoucherId();
            redisTemplate.opsForValue().setIfAbsent(SeckillKeys.stock(id), voucher.getStock().toString());
            redisTemplate.opsForValue().set(SeckillKeys.begin(id), Long.toString(
                    voucher.getBeginTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
            redisTemplate.opsForValue().set(SeckillKeys.end(id), Long.toString(
                    voucher.getEndTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        }
        log.info("Initialized {} seckill vouchers in Redis", voucherMapper.selectCount(null));
    }
}

