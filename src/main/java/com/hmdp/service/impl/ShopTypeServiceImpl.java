package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.*;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY;
        // 1. 从 Redis 中查询店铺类型缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 3. 存在，直接反序列化成 List 返回
            List<ShopType> typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(typeList);
        }

        // 4. 不存在，查询数据库并按 sort 排序
        // 这个是原本contoller层的代码，现在我们将查询逻辑放到service层，controller层只负责调用service层的方法。
        List<ShopType> typeList = query().orderByAsc("sort").list();

        // 5. 数据库也不存在，返回错误
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺分类不存在！");
        }

        // 6. 存在，写入 Redis 缓存（分类很少变，也可以设置更长 TTL）
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        // 7. 返回
        return Result.ok(typeList);
    }
}
