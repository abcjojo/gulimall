package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.gulimall.ware.vo.SkuHasStockVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageUtils(page);
    }

    /**
     *  查询sku对应是否有库存
     * @param skuids
     * @return
     */
    @Override
    public List<SkuHasStockVo> getSkusHasStock(List<Long> skuids) {

        List<SkuHasStockVo> collect = skuids.stream()
                .map(skuId -> {
                    SkuHasStockVo vo = new SkuHasStockVo();
                    // 查询当前sku的总库存量
                    // select sum(stock - stock_locked) from wms_ware_sku where sku_id = ?
                    Long count = baseMapper.getSkuStock(skuId);
                    vo.setSkuId(skuId);
                    vo.setHasStock(count == null ? false : count > 0);
                    return vo;
                })
                .collect(Collectors.toList());

        return collect;
    }

}