package com.quantbacktest.backtester.infrastructure;

import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.service.BacktestExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages lifecycle of background workers.
 * Starts workers on application startup and gracefully shuts them down.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkerManager {

    private final ExecutorService workerExecutorService;
    private final QueueService queueService;
    private final BacktestJobRepository backtestJobRepository;
    private final BacktestExecutor backtestExecutor;

    @Value("${backtest.worker.thread-count:3}")
    private int workerThreadCount;

    @Value("${backtest.worker.enabled:true}")
    private boolean workersEnabled;

    private final List<BacktestWorker> workers = new ArrayList<>();

    @PostConstruct
    public void startWorkers() {
        if (!workersEnabled) {
            log.info("Background workers are disabled");
            return;
        }

        log.info("Starting {} backtest workers", workerThreadCount);

        for (int i = 0; i < workerThreadCount; i++) {
            String workerName = "BacktestWorker-" + (i + 1);
            BacktestWorker worker = new BacktestWorker(
                    queueService,
                    backtestJobRepository,
                    backtestExecutor,
                    workerName);

            workers.add(worker);
            workerExecutorService.submit(worker);

            log.info("Started {}", workerName);
        }

        log.info("All {} workers started successfully", workerThreadCount);
    }

    @PreDestroy
    public void stopWorkers() {
        log.info("Stopping all workers...");

        // Signal all workers to stop
        workers.forEach(BacktestWorker::stop);

        // Shutdown executor service
        workerExecutorService.shutdown();

        try {
            if (!workerExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Workers did not terminate gracefully, forcing shutdown");
                workerExecutorService.shutdownNow();
            } else {
                log.info("All workers stopped gracefully");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for workers to stop", e);
            workerExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
