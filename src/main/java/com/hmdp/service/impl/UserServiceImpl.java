package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.date.DateUtil.dayOfMonth;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;
import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author starlight
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Override
    public Result signCount() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();//1-31
        //5.获取本月截止今天为止的所有签到记录，返回的会是一个十进制的数字，每一位代表当天是否签到，0表示未签到，1表示已签到
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result == null || result.isEmpty()){
            //没有签到记录，返回0
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num == null || num == 0){
            //没有签到记录，返回0
            return Result.ok(0);
        }
        //6.循环便利
        int count = 0;
        while(true){
            //6.1让这个数字与1做与运算，得到最后一位，判断是否为0
            if((num&1) == 0){
                //如果为0，说明未签到，结束
                break;
            }else {
                //如果不为0，说明已签到，计数器+1
                count++;
            }
            //把这个数字右移一位，抛弃掉最后一个bit位，继续判断下一位是否为0
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result sign() {
        //1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //2.获取当前日期
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //4.获取今天是本月的第几天
        int day = now.getDayOfMonth();//1-31
        //5.写入redis SETBIT key offset 1
        // true表示签到，false表示未签到，offset是从0开始的，所以要减1
        stringRedisTemplate.opsForValue().setBit(key, day - 1, true);

        return Result.ok();
    }

    @Resource// 这个注解是Spring提供的，作用是自动注入依赖对象。
    // 它可以用在类的成员变量、方法和构造函数上，用于将Spring容器中的Bean自动注入到当前类中，
    // 从而实现依赖注入的功能。
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        // 3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);// 生成6位随机数作为验证码

//        // 4. 保存验证码到 session
//        session.setAttribute("code", code);
        // 4. 保存验证码到 redis 这两个常量来自于Utils包下的RedisConstants类，分别表示验证码的键前缀和过期时间
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, java.util.concurrent.TimeUnit.MINUTES);

        // 5. 发送验证码
        // TODO 发送短信验证码 --- IGNORE ---
        log.debug("发送短信验证码成功，验证码: {}", code);

        // 6. 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2. 如果不符合，返回错误信息
            return Result.fail("手机号格式错误!");
        }
        //  3.从redis获取验证码并校验
        // 3. 校验验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            // 3. 不一致 直接报错
            return Result.fail("验证码错误!");

        }

        // 4. 一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5. 判断用户是否存在
        if (user == null) {
            // 6. 不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 7. 保存用户信息到 session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 7. 保存用户信息到 redis
        // 7.1 生成随机token，作为登录令牌
        // 注意这里的UUID是Hutool的 UUID工具类，而不是Java自带的UUID类。Hutool的UUID工具类提供了更多的功能和更简洁的API。
        String token = UUID.randomUUID().toString(true);

        // 7.2 将User对象转为HashMap格式存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //为什么要转成map？因为后面putall会比put更快，put是一个一个的存，而putall是一次性存储整个map，效率更高。
        // 7.3 存储数据
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4 设置token的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, java.util.concurrent.TimeUnit.MINUTES);
        // 到这里其实还没完，因为你这样设定的话，如果用户持续处于登录状态那30分钟后就会过期了，
        // 所以我们需要在用户每次访问的时候都刷新一下token的有效期，这样就可以实现“只要用户持续使用，就不会过期”的效果。
        // 所以接下来我们需要在拦截器中实现这个功能，具体的实现方式是在拦截器中获取到token，然后去刷新它的过期时间。

        // 8. 返回token
        return Result.ok(token);
    }
    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        // 随机生成昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2. 保存用户
        save(user);
        return user;
    }
}
