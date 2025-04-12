package com.zhongyuan.cachedemo.service.cache;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Configuration
public class ProductCacheConfig {

    @Bean
    public Cache<String,Object> productCache() {
        Random random = new Random();
        return Caffeine.newBuilder().expireAfter(new Expiry<String, Object>() {

            @Override
            public long expireAfterCreate(@NonNull String s, @NonNull Object o, long l) {
                return TimeUnit.MINUTES.toNanos(5 + random.nextInt(6));  // 5~10分钟
            }

            @Override
            public long expireAfterUpdate(@NonNull String s, @NonNull Object o, long l, @NonNegative long l1) {
                return l1;
            }

            @Override
            public long expireAfterRead(@NonNull String s, @NonNull Object o, long l, @NonNegative long l1) {
                return l1;
            }
        }).build();
    }


}
