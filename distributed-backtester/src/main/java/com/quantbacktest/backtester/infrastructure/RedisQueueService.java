package com.quantbacktest.backtester.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of the QueueService.
 * Uses Redis list operations for FIFO job queue.
 * Thread-safe: Redis operations are atomic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisQueueService implements QueueService {

    private static final String QUEUE_NAME = "backtest-jobs";
    private static final long POP_TIMEOUT_SECONDS = 1;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void push(Long jobId) {
        if (jobId == null) {
            log.error("Cannot push null job ID to queue");
            throw new IllegalArgumentException("Job ID cannot be null");
        }

        log.info("Pushing job {} to Redis queue: {}", jobId, QUEUE_NAME);
        try {
            // RPUSH is atomic in Redis - thread-safe
            Long queueSize = redisTemplate.opsForList().rightPush(QUEUE_NAME, jobId);
            log.debug("Successfully pushed job {} to queue. Queue size: {}", jobId, queueSize);
        } catch (DataAccessException e) {
            log.error("Redis error while pushing job {} to queue: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to enqueue job due to Redis error", e);
        } catch (Exception e) {
            log.error("Unexpected error while pushing job {} to queue: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to enqueue job", e);
        }
    }

    @Override
    public Long pop() {
        try {
            // BLPOP (blocking left pop) is atomic in Redis - thread-safe
            // Multiple workers can safely call this without race conditions
            Object value = redisTemplate.opsForList()
                    .leftPop(QUEUE_NAME, POP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (value != null) {
                Long jobId = ((Number) value).longValue();
                log.info("Popped job {} from Redis queue", jobId);
                return jobId;
            }

            // Timeout - no job available
            return null;
        } catch (ClassCastException e) {
            log.error("Invalid data type in queue. Expected Number: {}", e.getMessage(), e);
            return null;
        } catch (DataAccessException e) {
            log.error("Redis error while popping from queue: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to dequeue job due to Redis error", e);
        } catch (Exception e) {
            log.error("Unexpected error while popping from queue: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to dequeue job", e);
        }
    }
}
