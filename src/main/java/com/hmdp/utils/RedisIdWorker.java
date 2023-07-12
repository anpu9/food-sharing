package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    public static final long BEGIN_TIMESTAMP = 997412640;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        String timeKey = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd:"));
        long count = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + timeKey);
        //3.拼接并返回
        return timestamp<< 32 | count;
    }

    public static void main(String[] args) {
        //1.define
        LocalDateTime time = LocalDateTime.of(2001,8,10,3,4);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("time:" + second);

    }
}
