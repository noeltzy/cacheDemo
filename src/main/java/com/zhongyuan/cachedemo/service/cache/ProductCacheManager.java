package com.zhongyuan.cachedemo.service.cache;


import com.github.benmanes.caffeine.cache.Cache;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.Serializable;

@Component
public class ProductCacheManager {

    @Resource
    Cache<String, Object> productCache;

    public static final int MAX_REDIS_CACHE_ITEM_COUNT = 600;
    public static final double REMOVE_RATE = 0.2;
    private final String zsetKey = "hot:product";
    @Resource
    RedissonClient redissonClient;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public String getCaffeineKey(Serializable id) {
        return "product:local:" + id;
    }

    public String getRedisKey(Serializable id) {
        return "product:redis:" + id;
    }

    public Object get(Serializable id) {
        return  productCache.getIfPresent(getCaffeineKey(id));
    }

    public void put(Serializable id, Object value) {
        productCache.put(getCaffeineKey(id), value);
    }





}


