package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.save2Redis(1L,60L);
    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) { //生成100个id
                long id = redisIdWorker.nextId("order");
                System.out.println("id =" + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        //提交300次，共生成3w id
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time :" + (end - begin));

    }

    @Test
    void loadShop() {
        //1.query all data from db
        List<Shop> list = shopService.list();
        //2.group by typeid
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.add into redis (key,point,id) by groups(type,list)
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            String key = "shop:geo:" + entry.getKey();
            List<Shop> value = entry.getValue();
            //add geo once in entry
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
