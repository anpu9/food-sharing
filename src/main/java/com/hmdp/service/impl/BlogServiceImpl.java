package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
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
 * @author 虎哥
 * @since 2021-12-22
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
    public Result queryBlogById(Long id) {
        //1.查询blog
        Blog blog = getById(id);
        //2.查询发布blog的user信息
        queryBlogUser(blog);
        //3，查询该博客是否被点赞过
        isBlogLiked(blog);
        //3.返回blog
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        Long blogId = blog.getId();
        String key = BLOG_LIKED_KEY + blogId;
        UserDTO user = UserHolder.getUser();
        if (user == null) { //用户未登录
            return;
        }
        Long userId = user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();

        records.forEach(
                blog -> {
                    // 查询用户
                    queryBlogUser(blog);
                    // 查询当前用户是否点赞过博客
                    isBlogLiked(blog);
                }
        );

        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.判断用户是否点赞过
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        //1,1没有
        if(score == null) {
            //数据库更改blog点赞+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //redis set加上
            if(isSuccess) {
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        } else {
            //1.2点赞过
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //redis set加上
            if(isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        //查询用户集合
        String key = BLOG_LIKED_KEY + id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        //查询用户信息
        if (range == null || range.isEmpty()) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        //解析用户id
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query().in("id",ids)
                .last("ORDER BY FIELD(id," + join +")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        blog.setUserId(userId);
        // 保存探店博文
        save(blog);

        //查询粉丝
        List<Follow> followList = followService.query().eq("follow_user_id", userId).list();
        for (Follow follow : followList) {
            Long userId1 = follow.getUserId();
            // 推送到关注者的收件箱里，收件箱使用sorted-set实现
            String key = FEED_KEY + userId1;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //查询当前用户
        Long userId = UserHolder.getUser().getId();
        //获取她的收件箱
        String key = FEED_KEY + userId;
        //分页读取
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok();
        }
        //解析数据 (blog_id,minTime,offset)
        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            //get blog_id
            ids.add(Long.valueOf(tuple.getValue()));
            //get min time;
            long time = tuple.getScore().longValue();
            if(minTime == time) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        //根据blog_id查询blog
        String join = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id",ids)
                .last("ORDER BY FIELD(id," + join +")").list();
        blogs.forEach(blog -> {
            //2.查询发布blog的user信息
            queryBlogUser(blog);
            //3，查询该博客是否被点赞过
            isBlogLiked(blog);
        });
        //封装
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        //返回
        return Result.ok(scrollResult);
    }
}
