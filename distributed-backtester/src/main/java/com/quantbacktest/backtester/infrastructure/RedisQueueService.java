package com.quantbacktest.backtester.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-based implementation of the QueueService.
 * Uses Redis list operations for FIFO job queue.
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
        log.info("Pushing job {} to Redis queue: {}", jobId, QUEUE_NAME);
        try {
            redisTemplate.opsForList().rightPush(QUEUE_NAME, jobId);
            log.debug("Successfully pushed job {} to queue", jobId);
        } catch (Exception e) {
            log.error("Failed to push job {} to Redis queue", jobId, e);
            throw new RuntimeException("Failed to enqueue job", e);
        }
    }

    @Override
    public Long pop() {
        try {
            // Use blocking pop with timeout for efficient polling
            Object value = redisTemplate.opsForList()
                    .leftPop(QUEUE_NAME, POP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (value != null) {
                Long jobId = ((Number) value).longValue();
                log.info("Popped job {} from Redis queue", jobId);
                return jobId;
            }

            return null;
        } catch (Exception e) {
            log.error("Failed to pop job from Redis queue", e);
            throw new RuntimeException("Failed to dequeue job", e);
        }
    }
}
