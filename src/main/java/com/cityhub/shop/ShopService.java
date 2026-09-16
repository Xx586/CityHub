package com.cityhub.shop;

import com.cityhub.cache.CacheClient;
import com.cityhub.common.BusinessException;
import com.cityhub.common.ErrorCode;
import com.cityhub.domain.entity.Shop;
import com.cityhub.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ShopService {
    private static final String SHOP_CACHE_KEY = "cache:shop:";
    private final ShopMapper shopMapper;
    private final CacheClient cacheClient;

    public Shop findById(Long id) {
        Shop shop = cacheClient.queryWithLogicalExpire(SHOP_CACHE_KEY + id, Shop.class,
                ignored -> shopMapper.selectById(id), Duration.ofMinutes(30));
        if (shop == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商铺不存在");
        }
        return shop;
    }

    @Transactional
    public Long create(ShopRequest request) {
        Shop shop = toEntity(request);
        shopMapper.insert(shop);
        return shop.getId();
    }

    @Transactional
    public void update(Long id, ShopRequest request) {
        if (shopMapper.selectById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商铺不存在");
        }
        Shop shop = toEntity(request);
        shop.setId(id);
        shopMapper.updateById(shop);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheClient.evict(SHOP_CACHE_KEY + id);
            }
        });
    }

    private Shop toEntity(ShopRequest request) {
        Shop shop = new Shop();
        shop.setName(request.name());
        shop.setTypeId(request.typeId());
        shop.setImages(request.images() == null ? "" : request.images());
        shop.setArea(request.area() == null ? "" : request.area());
        shop.setAddress(request.address() == null ? "" : request.address());
        shop.setX(request.x());
        shop.setY(request.y());
        shop.setAvgPrice(request.avgPrice());
        shop.setOpenHours(request.openHours() == null ? "" : request.openHours());
        return shop;
    }
}
