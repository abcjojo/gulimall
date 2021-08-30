package com.atguigu.gulimall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gulimall.product.entity.BrandEntity;

import java.util.Map;

import com.atguigu.common.utils.PageUtils;
/**
 * 品牌
 *
 * @author liyijun
 * @email liyijun@gmail.com
 * @date 2021-08-16 12:14:16
 */
public interface BrandService extends IService<BrandEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void updateByDetail(BrandEntity brand);
}

