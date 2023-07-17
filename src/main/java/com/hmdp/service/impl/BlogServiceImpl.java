package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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
        Long userId = UserHolder.getUser().getId();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isLiked));
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
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //1,1没有
        if(BooleanUtil.isFalse(isLiked)) {
            //数据库更改blog点赞+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //redis set加上
            if(isSuccess) {
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        } else {
            //1.2点赞过
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //redis set加上
            if(isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }
}
