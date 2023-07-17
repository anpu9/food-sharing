package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    /**
     * 判断当前用户是否关注了这个用户
     * @param id
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result isFollow(Long id) {
        //1.获取用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            Result.ok(false);
        }
        //2.获取用户ID，查询关注
        Long userId = user.getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", id).count();
        //如果有，则返回true,反之返回false

        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long id, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String key = "follows:"+userId;
        if(BooleanUtil.isTrue(isFollow)) {
            //关注
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);
            boolean save = save(follow);
            if (save) {
                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        } else {
            //取关
            boolean remove = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", id));
            if(remove) {
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result FollowCommons(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.ok();
        }
        //查询用户ID
        String key1 = "follows:" + user.getId();
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //解析用户id
        List<UserDTO> dtos = userService.listByIds(ids).stream()
                .map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(dtos);
    }
}
