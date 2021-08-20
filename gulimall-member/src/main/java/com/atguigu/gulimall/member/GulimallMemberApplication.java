package com.atguigu.gulimall.member;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;


/**
 *  远程调用步骤
 *      1、引入open-feign
 *      2、编写一个接口，告诉SpringCloud这个接口需要调用远程服务
 *          2.1、声明接口的每一个方法都是需要和带调用方法的方法头一致，且请求地址为全路径
 *      3、开启远程调用功能
 */
@MapperScan("com.atguigu.gulimall.member.dao")
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.atguigu.gulimall.member.feign")     // 开启远程服务调用注解
public class GulimallMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(GulimallMemberApplication.class, args);
    }
}
