package com.zhongyuan.cachedemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.zhongyuan.cachedemo.domain.Product;
import com.zhongyuan.cachedemo.mapper.ProductMapper;
import com.zhongyuan.cachedemo.service.ProductService;

import jodd.util.concurrent.ThreadFactoryBuilder;

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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private final ExecutorService executorService = new ThreadPoolExecutor(
    2,                                 // 核心线程数
    5,                                 // 最大线程数
    60L,                               // 空闲线程存活时间
    TimeUnit.SECONDS,                  // 时间单位
    new LinkedBlockingQueue<>(100),    // 工作队列
    r -> {
        Thread thread = new Thread(r, "product-service-pool-" + Thread.currentThread().getId());
        thread.setDaemon(true);
        return thread;
    },  // 自定义线程工厂，设置有意义的线程名称和守护线程
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);

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
        // 外部先检查一次
        if (count != null && count > MAX_REDIS_CACHE_ITEM_COUNT) {
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
                                // 转换成redis的key
                                List<String> collect = range.stream().map(r ->
                                        getRedisKey((Serializable) r)
                                        ).collect(Collectors.toList());
                                // 删除redis
                                redisTemplate.delete(collect);
                                // 淘汰
                                redisTemplate.opsForZSet().removeRange(zsetKey, 0, removeCount);
                                // 为什么淘汰caffine,回收效率太低，redis都LRU淘汰了,Caffine八成已经过期了所以再去淘汰Caffine 没啥意义
                                // Cafffine 手动淘汰只会发生在 Update 阶段
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