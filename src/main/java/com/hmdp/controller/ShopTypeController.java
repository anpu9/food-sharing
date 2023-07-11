package com.hmdp.controller;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @GetMapping("list")
    public Result queryTypeList() {
        //1.查Redis
        String typeListJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE);
        //2.查到直接序列化返回
        if (StrUtil.isNotBlank(typeListJson)) {
            List<ShopType> typeList = JSONUtil.toList(typeListJson,ShopType.class).stream().sorted(Comparator.comparingInt(ShopType::getSort)).collect(Collectors.toList());
            return Result.ok(typeList);
        }
        //3.没查到查数据库
        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
        //4.是否为空
        if (CollectionUtil.isEmpty(typeList)){
            return Result.fail("商铺分类信息为空！");
        }
        //5.不为空，写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE,JSONUtil.toJsonStr(typeList));
        //6.返回
        return Result.ok(typeList);
    }
}
