package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author starlight
 * @since 2026-11-12
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        //老师这了用的是records.forEach(this::queryBlogUser); 这个是方法引用，和上面是一样的

        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1. 查询blog
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在");
        }
        //2. 查询blog有关的用户
        queryBlogUser(blog);

        //3.查询blog是否被当前用户点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1. 获取登录用户
//        Long userId = UserHolder.getUser().getId();
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 如果用户未登录，直接返回
            return;
        }
        // 2. 判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {

        // 这里应该实现点赞的逻辑
        // 1. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2. 判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;

        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //BooleanUtil.isFalse(isMember)
        if(score == null){
            // 3. 如果未点赞，执行点赞操作
            // 3.1. 数据库点赞数 +1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2. 保存用户到 Redis 的点赞set集合
            if(isSuccess){
                //一开始用的set现在要改Zset
                //stringRedisTemplate.opsForSet().add(key, userId.toString());
                // zadd key value score
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }else {
            // 4. 如果已点赞，执行取消点赞操作

            // 4.1. 数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2. 从 Redis 的点赞set集合中移除用户
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        
        
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1. 查询top5的点赞用户 zrange key start end
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析登录用户
        //top5.stream().map(Long::valueOf).collect(Collectors.toList()) 这段代码是将top5集合中的每个元素（字符串类型的用户ID）转换为Long类型，
        // 并收集到一个新的List<Long>中。具体来说：
        // top5.stream()：将top5集合转换为一个Stream<String>，以便进行流式操作。
        // map(Long::valueOf)：使用map操作将每个字符串类型的用户ID转换为Long类型。
        // Long::valueOf是一个方法引用，等价于lambda表达式s -> Long.valueOf(s)。
        // collect(Collectors.toList())：将转换后的Long类型的用户ID收集到一个List<Long>中。
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
        // 这段代码是使用MyBatis-Plus的查询构建器来查询数据库中的用户信息，并保持查询结果的顺序与给定的ID列表一致。具体来说：
        // query()：创建一个查询构建器，用于构建数据库查询。
        // in("id", ids)：添加一个条件，查询用户表中id字段的值在给定的ids列表中的记录。
        // last("ORDER BY FIELD(id," + idStr + ")")：添加一个自定义的SQL片段，用于指定查询结果的排序顺序。
        // 这里使用了MySQL的FIELD函数，根据idStr中指定的顺序对查询结果进行排序。
        // idStr是一个以逗号分隔的字符串，表示用户ID的顺序。list()：执行查询并返回结果列表。
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list() // 保持查询顺序与ids一致
                .stream()
                .map(user ->
                        BeanUtil.copyProperties(user, UserDTO.class)
                )
                .collect(Collectors.toList());


        // 2. 查询当前用户对某个笔记的点赞情况
        return Result.ok(userDTOS);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("保存笔记失败");
        }
        // 3.查询笔记作者所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        // 4. 推送笔记id给所有粉丝
        follows.forEach(follow -> {
            // 4.1 获取粉丝id
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            // 4.2 将blogId推送到收件箱
            // 对应redis命令： zadd key value score 利用时间戳排序
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2. 查询收件箱 ZREVRANGEBYSCORE key max min limit offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3. 非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        // 4. 解析数据：blogId、minTime(时间戳)、offset
        // List<Long> ids变量 用于存储从Redis中获取的博客ID列表。
        // typedTuples是一个包含博客ID和对应时间戳的集合，通过遍历typedTuples，将每个博客ID转换为Long类型并添加到ids列表中。
        // 最终，ids列表将包含所有从Redis中获取的博客ID，用于后续查询数据库中的博客详细信息。
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;// 先定义的话，最后走完循环后，minTime会被赋值为最后一个元素的时间戳
        int os = 1; // 记录同一时间戳的数量，初始值为1，因为至少有一个元素
        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            // 4.1. 获取Id
            String idStr = tuple.getValue();
            ids.add(Long.valueOf(idStr));
            // 4.2. 获取分数(时间戳)
            long time = tuple.getScore().longValue();
            // 4.3. 统计数量
            // 这里的逻辑是为了统计具有相同时间戳的元素数量
            // 如果当前元素的时间戳与minTime相同，则增加计数器
            // 如果不同，则更新minTime为当前元素的时间戳，并将计数器重置为1
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }


        }
        // 4. 根据ids查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();

        // 5. 查询blog有关的用户和是否被点赞
        for (Blog blog : blogs) {
            //5.1 查询blog有关的用户
            queryBlogUser(blog);
            //5.2 查询blog是否被当前用户点赞
            isBlogLiked(blog);
        }
        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);

    }

}
