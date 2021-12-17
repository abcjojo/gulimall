package com.atguigu.gulimall.search.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient("gulimall-product")
public interface ProductFeignService {

    /**
     * 信息
     */
    @GetMapping("/product/attr/info/{attrId}")
    public R attrInfo(@PathVariable("attrId") Long attrId);


    /**
     *  根据品牌id集合查询品牌信息
     * @param brandIds
     * @return
     */
    @RequestMapping("/product/brand/infos")
    public R brandsInfos(@RequestParam("brandId") List<Long> brandIds);
}
