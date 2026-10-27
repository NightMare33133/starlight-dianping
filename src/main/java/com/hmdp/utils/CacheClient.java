package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        // 这个构造函数是用来注入StringRedisTemplate对象的，它会在创建CacheClient对象时被调用。
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void  set(String key, Object value, Long time, TimeUnit unit) {
        // 这个方法是用来将数据写入Redis缓存的，它接收四个参数：
        // key：缓存的键
        // value：缓存的值
        // time：缓存的过期时间
        // unit：缓存过期时间的单位
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void  setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 这个方法是用来将数据写入Redis缓存的，它接收四个参数：
        // key：缓存的键
        // value：缓存的值
        // time：缓存的过期时间
        // unit：缓存过期时间的单位
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }



    //泛型
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //Function<ID, R> function是一个函数式接口，它接收一个ID类型的参数，返回一个R类型的结果。
        // 1.从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }
        // 4.判断命中的是否是空值
        if (json != null) {
            // 返回错误信息
            return null;
        }

        // 4. 不存在，根据id查询数据库
//        R r = getById(id);
        // 这里的数据库查询逻辑我们是不确定的，所以是需要传入的
        R r = dbFallback.apply(id);

        // 5. 不存在，返回错误
        if (r == null) {
            //将空值写入redis缓存，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, java.util.concurrent.TimeUnit.MINUTES);
            return null;
        }
        // 6. 存在，写入redis缓存
        this.set(key, r, time, unit);
        // 7. 返回
        return r;
    }



    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(10);

    // 尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, java.util.concurrent.TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                        Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1.从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 3.不存在，直接返回
            return null;
        }

        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return r;
        }
        // 5.2 已过期，需要缓存重建


        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.execute(() -> {
                //重建缓存
                try {
                    // 这里的数据库查询逻辑我们是不确定的，所以是需要传入的
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }

        // 6.4 失败，返回过期的商铺信息

        // 7. 返回
        return r;
    }

}
