package com.atguigu.gulimall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gulimall.product.entity.CategoryEntity;

import java.util.List;
import java.util.Map;

import com.atguigu.common.utils.PageUtils;
/**
 * 商品三级分类
 *
 * @author liyijun
 * @email liyijun@gmail.com
 * @date 2021-08-16 12:14:16
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageUtils queryPage(Map<String, Object> params);


    /**
     * 查询所有分类以及子分类，以树形结构组装起来
     */
    List<CategoryEntity> listWithTree();

    void removeMenuByIds(List<Long> asList);
}

