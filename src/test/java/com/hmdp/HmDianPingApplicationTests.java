package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static java.lang.System.currentTimeMillis;

@SpringBootTest
class HmDianPingApplicationTests {


    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 创建一个固定大小的线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker(){
        // 线程数量
        CountDownLatch latch = new CountDownLatch(300);

        // Runnable task = () -> 代表创建一个Runnable接口的匿名实现类，
        // 重写了run方法，里面的代码就是线程执行的任务。
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 线程执行完任务后，调用latch.countDown()方法，表示当前线程已经完成了任务，计数器减一。
            latch.countDown();
        };
        long begin = currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        try {
            // 这里的latch.await()方法是用来阻塞当前线程，直到计数器的值为0。
            // 也就是说，当前线程会一直等待，直到所有的子线程都执行完任务，
            // 调用了latch.countDown()方法，计数器减为0，当前线程才会继续执行。
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long end  = currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testCacheClient() {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop, 10L, TimeUnit.SECONDS);
    }

}
