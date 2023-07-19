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
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {

        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果手机号错误，返回错误信息
            return Result.fail("手机号错误！请重新输入");
        }
        //3.正确，生成验证码（用到了hutu工具包）
        String code = RandomUtil.randomNumbers(6);
        //4.将验证码保存在session中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.返回(假），可以自己用阿里云或者腾讯实现
        log.debug("发送验证码成功：{}",code);
        // 成功结果
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //0.校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.如果手机号错误，返回错误信息
            return Result.fail("手机号错误！请重新输入");
        }
        //1.校验验证码
//        Object cacheCode = session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        //2.错误返回
        if(cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }
        //3.查询手机号 select * from tb_user where phone = ?
        // 使用mybaits-plus实现查询
        User user = query().eq("phone", phone).one();

        if (user == null) {
            //4.不存在就创建一个保存在用户数据库中
            //逻辑比较复杂就放在方法里面
            user = createUserWithPhone(phone);
        }

        //TODO 把用户保存在Redis当中
        //1.生成token key UUID
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        String token = UUID.randomUUID().toString(true);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        //2.hash存储属性
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //3.设置token访问有效期
//        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);


        //3.将token返回给前端
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //get current user
        Long userId = UserHolder.getUser().getId();
        //get current month
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int day = now.getDayOfMonth();
        //store in redis
        String key = USER_SIGN_KEY + userId + keySuffix;
        stringRedisTemplate.opsForValue().setBit(key,day-1,true);
        //return
        return Result.ok();
    }

    @Override
    public Result signCount() {
        //get current user
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        int day = now.getDayOfMonth();
        //store in redis
        String key = USER_SIGN_KEY + userId + keySuffix;
        //get all sign records until now
        //traverse the num
        //and the last bit with 1
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                //没签到就退出
                break;
            } else {
                count++;
            }
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        //2.设置属性
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //3.保存在数据库，使用mp(hutu工具包）
        save(user);
        //4.返回
        return user;
    }
}
