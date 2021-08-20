package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author liyijun
 * @email liyijun@gmail.com
 * @date 2021-08-16 16:43:56
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {
	
}
