package com.freecharge.smsprofilerservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freecharge.vault.PropertiesConfig;
import com.freecharge.vault.SecretsConfig;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.util.Map;
import java.util.Objects;

@Configuration
@Slf4j
//@EnableRedisRepositories(basePackages = "com.freecharge.smsprofilerservice.dao.redis")
public class RedisConfig {
    private final ObjectMapper mapper;
    private final String redisServerHost;
    private final String redisPassword;
    @Value("${redis.connection.ssl.enable:true}")
    private boolean sslEnable;
    private final int redisServerPort;
    private final int redisMaxIdle;
    private final int redisMaxTotal;
    private final int redisDbIndex;
    private final int templateTtlInMinutes;

    @Autowired
    public RedisConfig(@Qualifier("applicationProperties") PropertiesConfig propertiesConfig,
                       @NonNull ObjectMapper mapper,
                       @Qualifier("secretsProperties") SecretsConfig secretConfig) {
        final Map<String, Object> redisServerProperties = propertiesConfig.getProperties();
        final Map<String, Object> secrets = secretConfig.getSecrets();
        this.mapper = mapper;
        this.templateTtlInMinutes = (int) redisServerProperties.get("redis.server.ttl.minutes.template");
        this.redisServerPort = (int) redisServerProperties.get("redis.server.port");
        this.redisMaxIdle = (int) redisServerProperties.get("redis.server.pool.config.max.idle");
        this.redisMaxTotal = (int) redisServerProperties.get("redis.server.pool.config.max.total");
        this.redisDbIndex = (int) redisServerProperties.get("redis.server.database.index");
        this.redisServerHost = (String) redisServerProperties.get("redis.server.host");
        this.redisPassword = (String) secrets.get("redis.server.password");
    }

    /**
     * docker run -d -p 6379:6379 -v DATA-PATH:/data --name docker_redis_local redis
     * winpty docker run -it --rm --link docker_redis_local:redis redis bash -c 'redis-cli -h redis'
     *
     * @return
     */

    @Bean
    public JedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisServerHost);
        redisConfig.setPort(redisServerPort);
        JedisClientConfiguration jedisConfig;
        if (sslEnable) {
            redisConfig.setDatabase(redisDbIndex);
            redisConfig.setPassword(RedisPassword.of(redisPassword));
            jedisConfig = JedisClientConfiguration
                    .builder()
                    .useSsl()
                    .build();
        } else {
            jedisConfig = JedisClientConfiguration
                    .builder()
                    .build();
        }
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisConfig, jedisConfig);
        Objects.requireNonNull(jedisConnectionFactory.getPoolConfig()).setMaxTotal(redisMaxTotal);
        jedisConnectionFactory.getPoolConfig().setMaxIdle(redisMaxIdle);

        return jedisConnectionFactory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        jackson2JsonRedisSerializer.setObjectMapper(mapper);
        template.setConnectionFactory(redisConnectionFactory());
        template.setKeySerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(jackson2JsonRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        return template;
    }

}
