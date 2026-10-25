package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

public class LoginInterceptor implements HandlerInterceptor {

        // 注意，这里不能使用@Autowired注解和@Resource注解来注入StringRedisTemplate，
        // 因为LoginInterceptor是一个拦截器，它不是一个Spring管理的Bean，
        // 所以无法使用依赖注入的方式获取StringRedisTemplate对象。
        // 因此这里要使用构造函数注入的方式来获取StringRedisTemplate对象。
        // 因为这个LoginInterceptor类是我们自己创建的，
        // 所以我们可以在创建LoginInterceptor对象的时候，手动传入StringRedisTemplate对象。
        private StringRedisTemplate stringRedisTemplate;

        public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
            this.stringRedisTemplate = stringRedisTemplate;
        }



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {


            // 这里是原本用Session的方式处理
//        //1. 获取session
//        HttpSession session = request.getSession();
//        //2. 获取session中的用户
//        Object user = session.getAttribute("user");

        // 1. 获取请求头中的token

        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 如果token为空，说明用户未登录，返回401状态码
            response.setStatus(401);
            return false;
        }
        // 2.基于TOKEN获取redis中的用户
        // entries方法会返回一个Map对象，包含了Redis中存储的所有键值对。
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);


        //3. 判断用户是否存在
        if (userMap.isEmpty()) {
            //4. 不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }

        // 5. 将查询到的Hash数据转为UserDTO对象
        //fillBeanWithMap方法是Hutool工具类提供的一个方法，它可以将一个Map对象中的键值对填充到一个JavaBean对象中。
        // 这个方法的第一个参数是要填充的Map对象，第二个参数是要填充的JavaBean对象，
        // 第三个参数是一个布尔值，表示是否忽略Map中不存在的属性。
        // 如果第三个参数为true，则忽略Map中不存在的属性；如果为false，则会抛出异常。
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6. 存在，保存用户信息到ThreadLocal
//        UserHolder.saveUser((UserDTO) user); 这个是原本用Session的方式处理
        UserHolder.saveUser(userDTO);
        // 7. 刷新token有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, java.util.concurrent.TimeUnit.MINUTES);
        //8. 放行
        return true;


    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }


}
