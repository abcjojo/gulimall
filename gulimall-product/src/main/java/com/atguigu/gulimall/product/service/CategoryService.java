package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.vo.Catalog2Vo;
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

    /**
     *  找出catelogId的全路径
     * @param cateLogId
     * @return
     */
    Long[] findCatelogPath(Long cateLogId);

    void updateCascade(CategoryEntity category);

    /**
     *  查询一级分类
     * @return
     */
    List<CategoryEntity> getLevel1Categorys();

    Map<String, List<Catalog2Vo>> getCatalogJson();

    Long[] findCatalogPath(Long catelogId);
}

