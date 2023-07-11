package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;


@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 1.将任意java对象序列化为Json,并储存在String类型的key中，并可以设置TTL时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    // 2.将任意java对象序列化为Json,并储存在String类型的key中，并可以设置逻辑过期时间，用于处理逻辑击穿
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 封装逻辑过期
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    // 3.根据指定的key查找缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透
    public <T, ID> T queryWithPenetration(String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        //1.从redis查缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            /**
             * 在缓存中存在，直接序列化
             */
            return JSONUtil.toBean(json, type);
        }
        if (json != null) { //不为null，但为空字符串
            return null;
        }
        /**
         * 3.未命中，查数据库
         * -存在，写入缓存
         * -不存在，返回错误
         */
        T t = dbFallback.apply(id);
        if (t == null) {
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", time, unit);
            // 结束
            return null;
        }
        this.set(key, t, time, unit);
        return t;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    // 4.根据指定的key查找缓存，并反序列化为指定类型，利用逻辑过期的方式解决缓存击穿
    public <T, ID> T queryWithLogicalExpire(String keyPrefix, String lockPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            return null;
        }

        //4.命中，需要JSON反序列化为对象
        com.hmdp.entity.RedisData redisData = JSONUtil.toBean(json, com.hmdp.entity.RedisData.class);

        T data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //5.判断是否过期
        //6.未过期，返回shop数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return data;
        }
        //7.过期，尝试获取锁
        String lockKey = lockPrefix + id;
        boolean isLock = tryLock(lockKey);

        if (isLock) { //9.获取成功，开启新线程，缓存重建
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    //查询数据
                    T newData = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, newData, time, unit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 释放锁
                unlock(lockKey);
            }
        }
        //8.获取失败，直接返回旧数据
        return data;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
