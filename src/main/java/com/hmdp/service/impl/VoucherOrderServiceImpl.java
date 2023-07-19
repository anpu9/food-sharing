package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherHandler());
    }
    private class VoucherHandler implements Runnable {
        @Override
        public void run() {
            String key = "stream.orders";
            while(true) {
                try {
                    //1.尝试获取队列中的订单信息
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)
                            ), StreamOffset.create(key, ReadOffset.lastConsumed())
                    );
                    if(records == null || records.isEmpty()) {
                        //1.2获取失败
                        continue;
                    }
                    //1.1获取成功
                    Map<Object, Object> values = records.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                    //3.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(key,"g1",records.get(0).getId());
                } catch (Exception e) {
                    //读取pending-list的消息
                    log.error("读取队列消息异常：",e);
                    handlerPendingList();
                }
            }
        }

        private void handlerPendingList() {
            String key = "stream.orders";
            while(true) {
                try {
                    //1.尝试获取pendinglist中的订单信息
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(key, ReadOffset.from("0"))
                    );
                    if(records == null || records.isEmpty()) {
                        //1.2获取失败
                        break;
                    }
                    //1.1获取成功
                    Map<Object, Object> values = records.get(0).getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.创建订单
                    handlerVoucherOrder(voucherOrder);
                    //3.ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(key,"g1",records.get(0).getId());
                } catch (Exception e) {
                    //读取pending-list的消息
                    log.error("读取pending-list消息异常：",e);
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
//    private class VoucherHandler implements Runnable {
//        @Override
//        public void run() {
//            while(true) {
//                try {
//                    //1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //2.创建订单
//                    handlerVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    //打印
//                    log.error("处理订单异常：", e);
//                }
//            }
//        }
//    }
    private  IVoucherOrderService proxy;

    /**
     * 异步 下单任务
     * @param voucherOrder
     */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long useId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + useId);
        boolean isLock = lock.tryLock();
        if(!isLock) {
            // 获取锁失败，返回错误或重试
            log.error("不允许重复下单！");
            return;
        }
        try {
            proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder); //this调用，即本类对象调用
        } finally {
            lock.unlock();
        }
    }

    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀优惠券 - 抢单 & 异步下单
     * @param voucherId
     * @return
     */
    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long id = redisIdWorker.nextId("worker");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.EMPTY_LIST, //KEYS
                voucherId.toString(), userId.toString(),id.toString() //ARGV
        );
        //判断是否有下单资格
        int r = result.intValue();
        //没有下单资格
        if(r != 0) {
            return Result.fail(r == 1? "库存不足！":"不能重复下单！");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id,完成抢单
        return Result.ok(id);
    }
//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.EMPTY_LIST, //KEYS
//                voucherId, userId //ARGV
//        );
//        //判断是否有下单资格
//        int r = result.intValue();
//        //没有下单资格
//        if(r != 0) {
//            return Result.fail(r == 1? "库存不足！":"不能重复下单！");
//        }
//        //有下单资格
//        //TODO 将创建订单的业务添加到阻塞队列
//        // 6.创建订单,添加到阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //添加订单id,用户ID，voucherID
//        long id = redisIdWorker.nextId("worker");
//        voucherOrder.setId(id);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        //返回订单id,完成抢单
//        return Result.ok(id);
//    }
//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        // 1.查询秒杀券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        // 3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//        // 4.判断秒杀库存
//        if (voucher.getStock() <= 0) {
//            return Result.fail("库存不足！");
//        }
//        // TODO 一人一单，查询订单表内是否存在订单（voucher_id,user_id)与当前一致的情况
//        Long useId = UserHolder.getUser().getId();
////        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:"+useId);
//        RLock lock = redissonClient.getLock("lock:order:" + useId);
//        boolean isLock = lock.tryLock();
//        if(!isLock) {
//            // 获取锁失败，返回错误或重试
//            return Result.fail("不允许重复下单！");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId); //this调用，即本类对象调用
//        } finally {
//            lock.unlock();
//        }
//
//    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long useId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int count = query().eq("user_id", useId).eq("voucher_id", voucherId).count();
        if(count > 0){
            log.error("不允许重复购买！");
            return ;
        }
        // 5.减库存,乐观锁CAS法判断stock是否与之前查询结果一致

        boolean success = seckillVoucherService.update().setSql("stock = stock -1") //set
                .eq("voucher_id", voucherId).gt("stock",0) //where id = ? && stock = ?
                .update();
        if (!success) {
            log.error("库存不足！");
            return ;
        }
        save(voucherOrder);
    }

//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long useId = UserHolder.getUser().getId();
//        int count = query().eq("user_id", useId).eq("voucher_id", voucherId).count();
//        if(count > 0){
//            return Result.fail("用户已经购买过一次！");
//        }
//        // 5.减库存,乐观锁CAS法判断stock是否与之前查询结果一致
//        boolean success = seckillVoucherService.update().setSql("stock = stock -1") //set
//                .eq("voucher_id", voucherId).gt("stock",0) //where id = ? && stock = ?
//                .update();
//        if (!success) {
//            return Result.fail("库存不足！");
//        }
//        // 6.创建订单,添加到数据库里
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //添加订单id,用户ID，voucherID
//        long id = redisIdWorker.nextId("worker");
//        voucherOrder.setId(id);
//        voucherOrder.setUserId(useId);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        // 7.返回订单id
//        return Result.ok(id);
//    }

}
