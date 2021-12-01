package com.atguigu.gulimall.product;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 *      JSR303
 *          1、给Bean添加校验注解
 *          2、开启校验功能 @Valid
 *              效果：校验错误以后会有默认的响应
 *          3、给校验的Bean后紧跟一个BindingResult，就可以获取到校验的结果
 *          4、分组校验
 *              a、@NotBlack(message = “品牌名必须提交”，groups = {AddGroups.calss, UpdateGroup.class})， 这里在校验注解的groups
 *              里添加什么标识，就会在相应的情况下进行校验
 *              b、@Validated({AddGroup.class})
 *          5、自定义校验
 *              a、编写一个自定义的校验注解
 *              b、编写一个自定义的校验器
 *              c、关联自定义的校验器和自定义校验注解
 *
 *       模板引擎
 *              1、thymeleaf-starter：关闭缓存
 *              2、静态资源都放在static文件夹下皆可以按照路径直接访问
 *              3、页面放在templates下，直接访问
 *                 SpringBoot访问项目的时候，默认找index
 *
 *
 */
@MapperScan("com.atguigu.gulimall.product.dao")
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class GulimallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallProductApplication.class, args);
    }
}
