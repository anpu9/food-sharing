package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryByid(Long id) {
        // cache penetration
//        Shop shop = cacheClient.queryWithPenetration(CACHE_SHOP_KEY,1,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //solution1-mutex
//        Shop shop = queryWithMutex(id);
        //solution2-logical expiration
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,LOCK_SHOP_KEY,1,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if (shop == null) return Result.fail("该店铺不存在!");
        return Result.ok(shop);

    }

//    public Shop queryWithMutex(Long id)  {
//        String key = CACHE_SHOP_KEY + id;
//        // 1、从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        // 2、判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 存在,直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中的值是否是空值
//        if (shopJson != null) {
//            //返回一个错误信息
//            return null;
//        }
//        // 4.实现缓存重构
//        //4.1 获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 4.2 判断否获取成功
//            if(!isLock){
//                //4.3 失败，则休眠重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.4 成功，根据id查询数据库
//            shop = getById(id);
//            // 5.不存在，返回错误
//            if(shop == null){
//                //将空值写入redis
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                //返回错误信息
//                return null;
//            }
//            //6.写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_NULL_TTL,TimeUnit.MINUTES);
//
//        }catch (Exception e){
//            throw new RuntimeException(e);
//        }
//        finally {
//            //7.释放互斥锁
//            unlock(lockKey);
//        }
//        return shop;
//    }

//    public Shop queryWithPenetration(Long id) {
//        //1.从redis查缓存
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            /**
//             * 在缓存中存在，直接序列化
//             */
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        if (shopJson != null) { //不为null，但为空字符串
//            return null;
//        }
//        /**
//         * 3.未命中，查数据库
//         * -存在，写入缓存
//         * -不存在，返回错误
//         */
//        Shop shop = getById(id);
//        if (shop == null) {
//            // 将空值写入redis
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            // 结束
//            return null;
//        }
//        // write to redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire( Long id ) {
//        String key = CACHE_SHOP_KEY + id;
//        // 1.从redis查询商铺缓存
//        String json = stringRedisTemplate.opsForValue().get(key);
//        // 2.判断是否存在
//        if (StrUtil.isBlank(json)) {
//            // 3.存在，直接返回
//            return null;
//        }
//        // 4.命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now())) {
//            // 5.1.未过期，直接返回店铺信息
//            return shop;
//        }
//        // 5.2.已过期，需要缓存重建
//        // 6.缓存重建
//        // 6.1.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 6.2.判断是否获取锁成功
//        if (isLock){
//            CACHE_REBUILD_EXECUTOR.submit( ()->{
//
//                try{
//                    //重建缓存
//                    this.saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    unlock(lockKey);
//                }
//            });
//        }
//        // 6.4.返回过期的商铺信息
//        return shop;
//    }
    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            Result.fail("更新的店铺不存在！");
        }
        // 写入数据库
        updateById(shop);
        // 删缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否用地理坐标排序
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // query from redis
        //pagination arg
        int from = (current-1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000), //查询的距离范围
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        //截取from
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.EMPTY_LIST);
        }
        // get members(shop_id)
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String name = result.getContent().getName();
            Distance distance = result.getDistance();
            ids.add(Long.valueOf(name));
            distanceMap.put(name,distance);
        });

        // query shop by id and set distance
        String join = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + join + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 返回数据
        return Result.ok(shops);
    }

    public void save2Redis (Long id, Long expireSeconds) {
//        1/1•充询店锅数店
        Shop shop = getById (id);
//        1/2.封花逻得过期时间
        RedisData redisData = new RedisData ();
        redisData.setData (shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        1/3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }


}
