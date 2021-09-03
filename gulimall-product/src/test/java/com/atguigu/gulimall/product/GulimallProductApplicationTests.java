package com.atguigu.gulimall.product;

import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;


/**
 * 1、引入oss-starter
 * 2、配置key，endpoint相关信息即可
 * 3、使用OSSClient 进行相关操作
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallProductApplicationTests {

    @Autowired
    private BrandService brandService;

    @Test
    public void test1() {

//        List<BrandEntity> list = brandService.list(new QueryWrapper<BrandEntity>().eq("brand_id", 1L));
//        list.forEach(item -> System.out.println(item));

//        BrandEntity brandEntity = new BrandEntity();
//        brandEntity.setBrandId(2L);
//        brandEntity.setDescript("小米");
//        brandService.updateById(brandEntity);
    }

    public static void main(String[] args) {
        //创建键盘录入
        Scanner sc = new Scanner(System.in);
        System.out.println("请输入你的QQ号码");
        String qq = sc.nextLine();
        System.out.println("checkQQ:\t" + checkQQ(qq));
    }
    public static boolean checkQQ(String qq){
        //matches()  告知字符串是否匹配给定的正则表达式
        return qq.matches("^[1-9][0-9]{4,13}");
    }

}
