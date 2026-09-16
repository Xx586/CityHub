package com.cityhub.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {
    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                  @Value("${spring.data.redis.port}") int port,
                                  @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        var server = config.useSingleServer().setAddress("redis://" + host + ":" + port);
        if (StringUtils.hasText(password)) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}

