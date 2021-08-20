package com.atguigu.gulimall.coupon;


import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.context.config.annotation.RefreshScope;


/**
 *   a、  使用Nacos作为配置中心统一管理配置步骤
 *          1、引入依赖
 *          2、创建 bootstrap。properties 文件
 *          3、需要给配置中心添加一个数据集（ Data Id）gulimall-coupon.properties。 默认规则为：应用名.properties
 *          4、在数据集 gulimall-coupon.properties 中添加配置
 *          5、动态获取配置  @RefreshSocpe  动态获取并刷新配置  @Value 取值
 *          6、如果配置中心和当前应用的配置文件都配置了相同的项，优先使用配置中心的配置
 *
 *
 *   b、  Nacos使用细节
 *          1、命名空间：做配置隔离
 *              默认：public（保留空间）； 默认新增的所有配置都在public空间
 *          2、配置集
 *          3、配置集ID
 *          4、配置分组、
 *
 *
 *   c、  同时加载多个数据集
 *          1、微服务任何配置信息、任何配置文件都可以放在配置中心中
 *          2、只需要在bootstrap.properties说明加载配置中心哪些配置文件即可
 *          3、@Value、@ConfigurationProperties等之前可以在SpringBoot配置文件中获取属性值的注解，都可以使用
 *          4、配置中心优先级高于本地配置文件
 */


@MapperScan("com.atguigu.gulimall.coupon.dao")
@SpringBootApplication
@EnableDiscoveryClient  // nacos 服务发现注解，开启此注解，服务会自动注册到nacos的注册中心
public class GulimallCouponApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallCouponApplication.class, args);
    }
}
