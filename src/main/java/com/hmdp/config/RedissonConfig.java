package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 1. 创建配置对象
        Config config = new Config();
        // 2. 添加单点 Redis 地址并设置密码
        // useSingleServer() 方法用于配置单点 Redis 服务器的地址和密码。
        // SingleServer对应的是单机模式的Redis服务器，适用于简单的应用场景。
        config.useSingleServer()
                .setAddress("redis://127.0.0.1:6379")
                .setPassword("123456");
        // 3. 创建并返回 RedissonClient 客户端
        return Redisson.create(config);
    }
}
