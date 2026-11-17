package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;
import static java.lang.System.currentTimeMillis;

@SpringBootTest
class HmDianPingApplicationTests {


    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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


    // ... 注入以下两个 Bean
    @Resource
    private IUserService userService;
//    @Resource
//    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testCreate1000Tokens() throws Exception {
        // 1. 查询数据库里的前 1000 个用户
        List<User> userList = userService.query().last("limit 1000").list();
        // 2. 准备输出文件（存到你的项目根目录下）
        File file = new File("/Users/starlight/Projects/tokens.txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (User user : userList) {
                // 2.1 随机生成一个登录 Token (UUID)
                String token = UUID.randomUUID().toString(true);
                // 2.2 存入 Redis（复用登录逻辑的 Hash 结构）
                UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
                Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                        CopyOptions.create().setIgnoreNullValue(true)
                                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

                String tokenKey = LOGIN_USER_KEY + token;
                stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
                stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
                // 2.3 把 token 写入文本文件，一行一个
                writer.println(token);
            }
        }
        System.out.println("🎉 成功为 1000 个用户完成登录，Token 已保存至: " + file.getAbsolutePath());
    }

    @Test
    void localShopData(){
        //1.查询店铺信息
        List<Shop> list = shopService.list();
        //2.把店铺分组，按typeId分组，typeId相同的放在一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺集合
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            // 3.3.将同类型的店铺集合写入redis GEOADD key 经度 纬度 member
            for (Shop shop : shops) {
                //stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                // 3.3.1.创建GeoLocation对象，封装shopId和坐标
                // 3.3.2.将GeoLocation对象添加到locations集合中
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            // 3.3.3.使用stringRedisTemplate.opsForGeo().add(key, locations)方法将locations集合写入redis
            stringRedisTemplate.opsForGeo().add(key, locations);
        }

    }


}
