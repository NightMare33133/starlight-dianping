package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author starlight
 * @since 2026-10-30
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //使用静态代码块初始化SECKILL_SCRIPT
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 设置 Lua 脚本文件的位置 ClassPathResource 是 Spring 提供的一个类，用于加载类路径下的资源文件。
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 设置返回值类型为 Long
        SECKILL_SCRIPT.setResultType(Long.class);

    }

    //引入lua
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();

        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        // 2.判断结果是否为0
        // long类型的result是一个对象，不能直接与0比较，所以要先转换为int类型
        int r = result.intValue();
        if (r != 0) {
            // 2.1.不为0，没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单！");
        }
        // 2.2.为0，代表有购买资格，把下单信息保存到阻塞队列中
        long orderId = redisIdWorker.nextId("order");
        // TODO 保存阻塞队列

        // 3.返回订单id
        return Result.ok(orderId);


//        // 1. 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 2. 判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3. 判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//
//        // 4. 判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//
//        // 这里是先获取锁再进入函数，不会与事务冲突，事务在函数内部执行，锁在函数外部获取，事务提交后锁才释放，所以不会出现事务失效的问题。
//        Long userId = UserHolder.getUser().getId();
//
////        synchronized (userId.toString().intern()) {
////            //这里用this会有问题
////            // 因为事务是通过代理对象来实现的，而在同一个类中直接调用方法不会经过代理对象，所以事务不会生效。
////            // 解决方法是通过AopContext.currentProxy()获取当前对象的代理对象
////            // 获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        // 现在要自己创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        //改用Redisson
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        // 获取锁
//        // 这里选择无参的tryLock()方法，它会立即返回一个布尔值，表示是否成功获取锁。
//        // 如果获取锁失败，说明当前用户已经在处理订单，直接返回错误信息
//        // 这里不使用lock.lock()，因为lock.lock()会阻塞当前线程
//        boolean isLock = lock.tryLock();
//
//        // 判断是否获取锁成功
//        if (!isLock) {
//            // 获取锁失败，返回错误或重试
//            return Result.fail("不允许重复下单！");
//        }
//
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }

    }

//    初版纯java实现的事务
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1. 查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        // 2. 判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3. 判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//
//        // 4. 判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//
//        // 这里是先获取锁再进入函数，不会与事务冲突，事务在函数内部执行，锁在函数外部获取，事务提交后锁才释放，所以不会出现事务失效的问题。
//        Long userId = UserHolder.getUser().getId();
//
////        synchronized (userId.toString().intern()) {
////            //这里用this会有问题
////            // 因为事务是通过代理对象来实现的，而在同一个类中直接调用方法不会经过代理对象，所以事务不会生效。
////            // 解决方法是通过AopContext.currentProxy()获取当前对象的代理对象
////            // 获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        // 现在要自己创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//
//        //改用Redisson
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        // 获取锁
//        // 这里选择无参的tryLock()方法，它会立即返回一个布尔值，表示是否成功获取锁。
//        // 如果获取锁失败，说明当前用户已经在处理订单，直接返回错误信息
//        // 这里不使用lock.lock()，因为lock.lock()会阻塞当前线程
//        boolean isLock = lock.tryLock();
//
//        // 判断是否获取锁成功
//        if (!isLock) {
//            // 获取锁失败，返回错误或重试
//            return Result.fail("不允许重复下单！");
//        }
//
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//
//    }

    @Transactional
    //@NonNull
    public  Result createVoucherOrder(Long voucherId) {
        // 4.1 一人一单
        Long userId = UserHolder.getUser().getId();
        // 上锁
        // .intern() 方法的作用是返回字符串对象的规范表示形式。它确保对于相同内容的字符串，返回的是同一个字符串对象的引用。
        // 这样可以避免因为字符串对象不同而导致的锁失效问题。
        // 不加的话数值即使一样，也会因为对象不同而导致锁失效，无法保证线程安全。
        //但是其实在这里加锁会出现锁和事务不在同一个线程的问题，导致事务失效，所以这里加锁是没有意义的。
            // 4.2 查询订单
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            // 4.3 判断是否存在
            if (count > 0) {
                // 用户已经购买过了
                return Result.fail("用户已经购买过一次！");
            }

            // 4.4 扣减库存
            //setSql("stock = stock - 1").eq("id", voucherId).update()
            //这个代码语句的意思是使用 MyBatis-Plus 的更新方法来执行一个 SQL 更新操作。
            // 具体来说，它会将指定 ID 的秒杀优惠券的库存（stock）字段减 1。
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")// set stock = stock - 1
                    .eq("voucher_id", voucherId)
//                .eq("stock", voucher.getStock()) // 乐观锁，确保库存未被其他线程修改
                    .gt("stock", 0) // 确保库存大于 0
                    // where id = ? and stock = ? 这部分是为了确保在更新库存时，只有当库存值与当前查询到的库存值相等时才会执行更新操作，从而防止并发问题。
                    .update();

            if (!success) {
                return Result.fail("扣减库存失败！");
            }


            // 6. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            // 6.1 订单id
            Long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            // 6.2 用户id
            //Long userId = UserHolder.getUser().getId();
            voucherOrder.setUserId(userId);
            // 6.3 代金券id
            voucherOrder.setVoucherId(voucherId);
            // 6.4 保存订单
            // save(voucherOrder) 这段代码的作用是将创建好的 VoucherOrder 对象保存到数据库中，完成订单的持久化操作。
            save(voucherOrder);


            // 7. 返回订单id

            return Result.ok(orderId);

    }
}
