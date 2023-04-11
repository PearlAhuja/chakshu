package com.freecharge.smsprofilerservice.config;

import com.freecharge.smsprofilerservice.constant.ProfilerConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

@Configuration
public class DistributedLockConfig {

    @Bean("redisLockForTemplateCount")
    @Autowired
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory){
        return new RedisLockRegistry(connectionFactory, ProfilerConstant.TEMPLATE_HASHCODE_LOCK_KEY_PREFIX);
    }
}
