package com.zhongyuan.cachedemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.zhongyuan.cachedemo.domain.Product;
import com.zhongyuan.cachedemo.mapper.ProductMapper;
import com.zhongyuan.cachedemo.service.ProductService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @author Windows11
 * @description 针对表【product】的数据库操作Service实现
 * @createDate 2025-03-12 23:10:53
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product>
        implements ProductService {

    public static final int MAX_REDIS_CACHE_ITEM_COUNT = 600;
    public static final double REMOVE_RATE = 0.2;
    private final String zsetKey = "hot:product";
    @Resource
    RedissonClient redissonClient;
    @Resource
    Cache<String, Object> productCacheManager;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public String getCaffeineKey(Serializable id) {
        return "product:local:" + id;
    }

    public String getRedisKey(Serializable id) {
        return "product:redis:" + id;
    }


    @Override
    public Product getById(Serializable id) {
        Object product = productCacheManager.getIfPresent(getCaffeineKey(id));

        if (product != null) {
            redisTemplate.opsForZSet().add(zsetKey, id, System.currentTimeMillis());
            return (Product) product;
        }

        product = redisTemplate.opsForValue().get(getRedisKey(id));
        if (product != null) {
            redisTemplate.opsForZSet().add(zsetKey, id, System.currentTimeMillis());
            productCacheManager.put(getCaffeineKey(id), product);
            return (Product) product;
        }

        product = super.getById(id);
        if (product != null) {
            redisTemplate.opsForZSet().add(zsetKey, id, System.currentTimeMillis());
            productCacheManager.put(getCaffeineKey(id), product);
            redisTemplate.opsForValue().set(getRedisKey(id), product);
            checkRedisUsage();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return (Product) product;
        }
        return null;
    }

    private void checkRedisUsage() {
        Long count = redisTemplate.opsForZSet().count(zsetKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        if (count != null && count > MAX_REDIS_CACHE_ITEM_COUNT) {
            ExecutorService executorService = Executors.newFixedThreadPool(10);
            executorService.submit(() -> {
                RLock lock = redissonClient.getLock("DELETE");
                try {
                    if (lock.tryLock()) {
                        Long cnt = redisTemplate.opsForZSet().count(zsetKey, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                        if (cnt != null && cnt > MAX_REDIS_CACHE_ITEM_COUNT) {
                            int removeCount = (int) (MAX_REDIS_CACHE_ITEM_COUNT * REMOVE_RATE);
                            System.out.println("!delete触发!");
                            Set<Object> range = redisTemplate.opsForZSet().range(zsetKey, 0, removeCount);
                            if (range != null) {
                                List<String> collect = range.stream().map(r ->
                                        getRedisKey((Serializable) r)
                                ).collect(Collectors.toList());
                                redisTemplate.delete(collect);
                                redisTemplate.opsForZSet().removeRange(zsetKey, 0, removeCount);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    lock.unlock();
                }
            });
        }
    }
}