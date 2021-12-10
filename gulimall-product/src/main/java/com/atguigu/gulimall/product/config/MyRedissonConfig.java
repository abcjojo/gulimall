package com.atguigu.gulimall.product.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MyRedissonConfig {


    /**
     *  所有对Redisson的使用都通过RedissonClient对象
     * @return
     * @throws IOException
     */
    @Bean
    public RedissonClient redisson() throws IOException {
        // 创建配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.21.130:6379");
        // 根据config创建出RedissonClient实例
        return Redisson.create(config);
    }
}
