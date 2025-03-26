package com.zhongyuan.cachedemo;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhongyuan.cachedemo.domain.Product;
import com.zhongyuan.cachedemo.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Currency;
import java.util.concurrent.*;

@SpringBootTest
class CacheDemoApplicationTests {


    @Resource
    ProductService productService;
    @Test
    void contextLoads() {
        Product byId = productService.getById(300);
        System.out.println(byId);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10,10,
                5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1400));
        for (int i = 1; i <=1400; i++) {
            int finalI = i;
            executor.execute(() -> {
                long start = System.currentTimeMillis();
                productService.getById(finalI);
                long end = System.currentTimeMillis();

                System.out.println("查询ID="+finalI+"时间:"+(end-start));

            });
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

}
