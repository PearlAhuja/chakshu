package com.freecharge.smsprofilerservice.config;

import com.freecharge.smsprofilerservice.constant.CacheName;
import com.freecharge.smsprofilerservice.constant.RedisKeyName;
import com.freecharge.vault.PropertiesConfig;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheBuilderConfig {

    private int domainTtlInMinutes;
    private int templateTtlInMinutes;

    @Autowired
    public CacheBuilderConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig) {
        final Map<String, Object> redisServerProperties = propertiesConfig.getProperties();
        this.domainTtlInMinutes = (int) redisServerProperties.get("cache.ttl.minutes.domain");
        this.templateTtlInMinutes = (int) redisServerProperties.get("redis.server.ttl.minutes.template");
    }

    @Bean
    @Primary
    @Autowired
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        Cache domainCache = new CaffeineCache(CacheName.DOMAIN_DATA, Caffeine.newBuilder().expireAfterWrite(domainTtlInMinutes, TimeUnit.MINUTES).build());
        Cache templateHashCodeCount = new CaffeineCache(CacheName.TEMPLATE_HASH_CODE_COUNT, Caffeine.newBuilder().expireAfterWrite(domainTtlInMinutes, TimeUnit.MINUTES).build());
        Cache allModelData = new CaffeineCache(CacheName.ALL_MODEL_DATA, Caffeine.newBuilder().build());
        Cache tokenData = new CaffeineCache(CacheName.TOKEN_DATA, Caffeine.newBuilder().build());
        Cache tokenNameFinderData = new CaffeineCache(CacheName.TOKEN_NAME_FINDER_NLP, Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build());
        Cache transactionCategoryData = new CaffeineCache(CacheName.TRANSACTION_CATEGORY_BY_CATEGORY, Caffeine.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).build());
        Cache activeSenderData = new CaffeineCache(CacheName.ACTIVE_SENDERS, Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build());
        Cache senderByShortCode = new CaffeineCache(CacheName.SENDER_BY_SHORTCODE, Caffeine.newBuilder().build());
        Cache activeSenderDataIncome = new CaffeineCache(CacheName.ACTIVE_SENDERS_INCOME, Caffeine.newBuilder().build());
        Cache shortcodeCounterCache = new CaffeineCache(CacheName.SHORTCODE_COUNTER,Caffeine.newBuilder().build());
        cacheManager.setCaches(Arrays.asList(transactionCategoryData, domainCache, templateHashCodeCount, allModelData, tokenNameFinderData, tokenData, activeSenderData,activeSenderDataIncome,senderByShortCode, shortcodeCounterCache));
        return cacheManager;
    }


    @Bean
    @Autowired
    public CacheManager redisCacheManager(JedisConnectionFactory jedisConnectionFactory) {
        RedisCacheConfiguration templateCache = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(templateTtlInMinutes));
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(RedisKeyName.TEMPLATE_DATA_KEY, templateCache);
        return RedisCacheManager.RedisCacheManagerBuilder.fromConnectionFactory(jedisConnectionFactory)
                .withInitialCacheConfigurations(cacheConfigurations).build();
    }
}
