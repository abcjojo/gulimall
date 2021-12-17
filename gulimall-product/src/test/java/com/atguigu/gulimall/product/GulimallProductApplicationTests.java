package com.atguigu.gulimall.product;

import com.atguigu.gulimall.product.entity.BrandEntity;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.BrandService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RedissonClient;
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

    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void redisson() {
        System.out.println(redissonClient);
    }


    @Test
    public void test2() {
        Object o = new Object();
        HashMap<Demo, String> hashMap = new HashMap<>();
        Demo a = new Demo("A");
        Demo b = new Demo("A");
        hashMap.put(a, "hello");
        String s = hashMap.get(b);
        System.out.println(s);
    }

@EqualsAndHashCode
    static class Demo {
        String key;

        Demo(String key) {
            this.key = key;
        }
    }

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


    @Test
    public void a() {
        Map map = new TreeMap();
        map.put("b", 11);
        map.put("c", 113);
        map.put("a", 112);
        map.put("B", 110);
        map.put("e", null);
        map.put(null, "121");
        System.out.println("全量map");
        Set set = map.keySet();
        set.forEach(e -> System.out.println("  " + e + ":" + map.get(e)));

        System.out.println("去空后---------");
        set.forEach(e -> {
            if (null == map.get(e)) {
                map.remove(e);
            }
        });

        Set set1 = map.keySet();
        set1.forEach(e -> System.out.println("  " + e + ":" + map.get(e)));


    }

}
