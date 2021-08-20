package com.atguigu.gulimall.member.dao;

import com.atguigu.gulimall.member.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author liyijun
 * @email liyijun@gmail.com
 * @date 2021-08-16 16:31:19
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
