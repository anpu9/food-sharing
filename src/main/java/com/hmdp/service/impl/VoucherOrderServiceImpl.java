package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result secKillVoucher(Long voucherId) {
        // 1.查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断秒杀库存
        if (voucher.getStock() <= 0) {
            return Result.fail("库存不足！");
        }
        // TODO 一人一单，查询订单表内是否存在订单（voucher_id,user_id)与当前一致的情况
        Long useId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"lock:"+useId);
        boolean isLock = lock.tryLock(1200);
        if(!isLock) {
            // 获取锁失败，返回错误或重试
            return Result.fail("不允许重复下单！");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId); //this调用，即本类对象调用
        } finally {
            lock.unlock();
        }

    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long useId = UserHolder.getUser().getId();
        int count = query().eq("user_id", useId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("用户已经购买过一次！");
        }
        // 5.减库存,乐观锁CAS法判断stock是否与之前查询结果一致
        boolean success = seckillVoucherService.update().setSql("stock = stock -1") //set
                .eq("voucher_id", voucherId).gt("stock",0) //where id = ? && stock = ?
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }
        // 6.创建订单,添加到数据库里
        VoucherOrder voucherOrder = new VoucherOrder();
        //添加订单id,用户ID，voucherID
        long id = redisIdWorker.nextId("worker");
        voucherOrder.setId(id);
        voucherOrder.setUserId(useId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 7.返回订单id
        return Result.ok(id);
    }

}
