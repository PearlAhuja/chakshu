package com.freecharge.smsprofilerservice.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The type App config.
 */
@Configuration
@EnableAsync
public class AppExecutorConfig implements AsyncConfigurer {

    @Value("${spring.thread.pool.core.pool.size}")
    private Integer springCorePoolSize;
    @Value("${spring.thread.pool.maximum.pool.size}")
    private Integer springMaximumPoolSize;
    @Value("${spring.thread.pool.keep.alive.time}")
    private Integer springKeepAliveTime;
    @Value("${spring.blocking.queue.capacity}")
    private Integer springQueueCapacity;
    @Value("${spring.thread.prefix.name}")
    private String threadPrefixName;


    /**
     * Gets async executor.
     *
     * @return the async executor
     */
    @Bean(name = "threadPoolTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(springCorePoolSize);
        executor.setMaxPoolSize(springMaximumPoolSize);
        executor.setQueueCapacity(springQueueCapacity);
        executor.setKeepAliveSeconds(springKeepAliveTime);
        executor.setThreadNamePrefix(threadPrefixName);
        executor.initialize();
        return executor;
    }

    @Bean(name = "executorServiceForScheduler")
    public ExecutorService executorServiceForScheduler() {
        return Executors.newFixedThreadPool(10);
    }

    @Bean(name = "executorServiceForAppRequest")
    public ExecutorService executorServiceForAppRequest() {
        return Executors.newFixedThreadPool(10);
    }
}
