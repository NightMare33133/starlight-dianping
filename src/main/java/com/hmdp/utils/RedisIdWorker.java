package com.hmdp.utils;


import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// 这个类是一个工具类，用于生成全局唯一的ID。它使用Redis的自增功能来生成ID，确保在分布式环境下也能保证唯一性。
@Component
public class RedisIdWorker {

    // 数字后面的L表示这是一个long类型的数字，避免了整数溢出的问题。
    private static final long BEGIN_TIMESTAMP = 1780272000L; // 2023-06-01 00:00:00 对应的时间戳，单位为秒

    private static final int COUNT_BITS = 32; // 序列号的位数
    private StringRedisTemplate stringRedisTemplate;

    // 这个构造函数用于注入StringRedisTemplate对象，它是Spring Data Redis提供的一个模板类，用于操作Redis数据库。
    // 通过构造函数注入的方式，我们可以在创建RedisIdWorker对象时，
    // 将StringRedisTemplate对象传入，从而在RedisIdWorker中使用它来操作Redis。
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 2. 生成序列号
        // 序列化利用redis的自增长
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        //大写Long和小写long的区别：
        //1. long是Java的基本数据类型，表示一个64位的有符号整数，而Long是long的包装类，提供了一些方法和常量。
        //2. long的默认值是0，而Long的默认值是null。

        // 2.2 自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接并返回
        // 这里使用了位运算符，将时间戳左移32位，然后与序列号进行按位或运算，生成一个64位的唯一ID。
        // 这样生成的ID是有序的，因为时间戳部分是递增的，而序列号部分在同一秒内是递增的。
        // 使用或运算符是因为时间戳和序列号在二进制表示中不会有重叠的位，所以可以直接使用或运算符将它们组合起来。
        // 位运算符 << 表示左移，| 表示按位或。
        return timestamp << COUNT_BITS | count;
    }
//
//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2026, 6, 1, 0, 0, 0);
//        long second = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second);
//        //1780272000
//    }

}
