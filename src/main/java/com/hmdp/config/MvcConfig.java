package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    //这个类具有@Configuration注解，表示它是一个配置类，用于配置Spring MVC的相关设置。
    //这个类将来由Spring容器管理，Spring会自动扫描并加载这个类中的配置。
    //所以在这里，我们可以使用依赖注入的方式来获取StringRedisTemplate对象，从而在LoginInterceptor中使用它。
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/voucher/**",
                        "/blog/hot",
                        "/shop-type/**",
                        "/upload/**"
                );


    }
}
