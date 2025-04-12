package com.zhongyuan.cachedemo.scheduled;

import com.github.benmanes.caffeine.cache.Cache;
import com.zhongyuan.cachedemo.domain.Product;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@EnableScheduling
@Component
public class CacheTask {
    private static final Logger logger = LoggerFactory.getLogger(CacheTask.class);
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private Cache<String, Object> productCache;

    private final AtomicInteger count = new AtomicInteger(0);
    private final String setKeyPrefix = "product:hot:set:";
    private final String zsetKey = "hot:product";
    private static final int HOT_PRODUCT_LIMIT = 50;
    private static final int SET_COUNT = 3;

    public String getCaffeineKey(Serializable id) {
        return "product:local:" + id;
    }

    public String getRedisKey(Serializable id) {
        return "product:redis:" + id;
    }

    @Scheduled(fixedRate = 5000)
    public void loadCacheToLocal() {
        try {
            // 获取当前计数并更新
            int currentCount = count.getAndIncrement();
            
            // 从ZSet获取热门商品ID
            Set<Object> hotProductIds = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, HOT_PRODUCT_LIMIT);
            if (hotProductIds == null || hotProductIds.isEmpty()) {
                logger.warn("No hot products found in Redis ZSet");
                return;
            }

            // 更新Redis Set
            String currentSetKey = setKeyPrefix + (currentCount % SET_COUNT);
            redisTemplate.delete(currentSetKey);
            redisTemplate.opsForSet().add(currentSetKey, hotProductIds);

            // 获取所有Set的key
            List<String> setKeys = new ArrayList<>();
            for (int i = 0; i < SET_COUNT; i++) {
                setKeys.add(setKeyPrefix + (i % SET_COUNT));
            }

            // 获取Set的交集
            Set<Object> intersect = redisTemplate.opsForSet().intersect(setKeys);
            if (intersect == null || intersect.isEmpty()) {
                logger.warn("No intersection found in Redis Sets");
                return;
            }

            // 批量获取商品数据
            List<String> redisKeys = intersect.stream()
                    .map(id -> getRedisKey((String) id))
                    .collect(Collectors.toList());

            List<Object> values = redisTemplate.opsForValue().multiGet(redisKeys);
            if (values == null || values.isEmpty()) {
                logger.warn("No product data found in Redis");
                return;
            }

            // 构建并更新本地缓存
            Map<String, Object> cacheMap = new HashMap<>();
            values.stream()
                    .filter(Objects::nonNull)
                    .filter(o -> o instanceof Product)
                    .map(o -> (Product) o)
                    .forEach(product -> cacheMap.put(getCaffeineKey(product.getId()), product));

            if (!cacheMap.isEmpty()) {
                productCache.putAll(cacheMap);
                logger.info("Successfully updated local cache with {} products", cacheMap.size());
            }
        } catch (Exception e) {
            logger.error("Error occurred while loading cache to local", e);
        }
    }
  
}
