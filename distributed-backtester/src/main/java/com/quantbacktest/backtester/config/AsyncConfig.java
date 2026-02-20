package com.quantbacktest.backtester.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async configuration for background workers and task execution.
 * Enables asynchronous processing for long-running operations.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${backtest.worker.thread-count:3}")
    private int workerThreadCount;

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("AsyncTask-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "workerExecutorService")
    public ExecutorService workerExecutorService() {
        return Executors.newFixedThreadPool(workerThreadCount,
                r -> {
                    Thread thread = new Thread(r);
                    thread.setName("BacktestWorker-" + thread.getId());
                    thread.setDaemon(false);
                    return thread;
                });
    }
}
