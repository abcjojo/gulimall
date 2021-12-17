package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

/**
 *  封装页面传递的查询条件参数
 */
@Data
public class SearchParam {

    // 检索关键词
    private String keywords;
    // 3级分类id
    private String catalog3Id;

    /**
     *  sort = saleCount_asc/desc
     *  sort = skuPrice_asc/desc
     *  sort = hotScore_asc/desc
     */
    // 排序条件
    private String sort;

    /**
     *    其他筛选条件
     *        hasStock：是否有货；  skuPrice:价格区间  brandId、catalog3Id、attrs
     *        hasStock = 0/1
     *        skuPrice = 100_200/_200/100_
     *        brandId = 1
     *        attrs = 2_5寸/6寸
     */
    private Integer hasStock;   // 时候只显示有货
    private String skuPrice;    // 价格区间
    private List<Long> brandId; // 品牌名称 可多选
    private List<String> attrs; // 商品属性
    private Integer pageNum;    // 页码

    private String _queryString; // 原声的所有查询条件
}
