package com.quantbacktest.backtester.infrastructure;

/**
 * Service interface for job queue operations.
 */
public interface QueueService {

    /**
     * Push a job ID to the queue.
     *
     * @param jobId the job ID to enqueue
     */
    void push(Long jobId);

    /**
     * Pop a job ID from the queue.
     *
     * @return the job ID, or null if queue is empty
     */
    Long pop();
}
