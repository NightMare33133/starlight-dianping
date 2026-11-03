package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private static final String KEY_PREFIX = "lock:";
    // 生成一个唯一的ID前缀，用于标识锁的所有者
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //使用静态代码块初始化UNLOCK_SCRIPT
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // 设置 Lua 脚本文件的位置 ClassPathResource 是 Spring 提供的一个类，用于加载类路径下的资源文件。
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 设置返回值类型为 Long
        UNLOCK_SCRIPT.setResultType(Long.class);

    }

    //不注入是因为这个类没有被spring管理,只能通过这种方式去实现
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程标识
        String threadId =  ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // threadId + "" 是为了将 long 类型的线程 ID 转换为 String 类型，因为 Redis 的值必须是字符串类型。

        // BooleanUtil.isTrue(success) 是为了避免自动拆箱导致的空指针异常
        return Boolean.TRUE.equals(success);
    }

    // 基于 Lua 脚本实现释放锁，保证原子性
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
                );
        //Collections.singletonList 是一个工具方法，
        // 用于创建一个只包含一个元素的不可变列表，这里用来传递锁的键给 Lua 脚本。

    }

    // 初版
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId =  ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断标识是否一致
//        if(threadId.equals(id)){
//            //一致，释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
