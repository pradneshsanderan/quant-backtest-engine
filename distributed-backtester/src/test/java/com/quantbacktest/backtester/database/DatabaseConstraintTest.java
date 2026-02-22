package com.quantbacktest.backtester.database;

import com.quantbacktest.backtester.domain.BacktestJob;
import com.quantbacktest.backtester.domain.BacktestResult;
import com.quantbacktest.backtester.domain.JobStatus;
import com.quantbacktest.backtester.repository.BacktestJobRepository;
import com.quantbacktest.backtester.repository.BacktestResultRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for database constraints and referential integrity.
 * 
 * NOTE: These tests require proper test database configuration.
 * To enable them, configure:
 * - Testcontainers for PostgreSQL, or
 * - H2 database with PostgreSQL compatibility mode
 * 
 * Disabled by default to allow other tests to run without infrastructure
 * dependencies.
 */
@DataJpaTest
@Disabled("Requires test database configuration - enable when infrastructure is ready")
class DatabaseConstraintTest {

    @Autowired
    private BacktestJobRepository backtestJobRepository;

    @Autowired
    private BacktestResultRepository backtestResultRepository;

    @Test
    void testUniqueIdempotencyKey_Enforced() {
        // Arrange
        String idempotencyKey = "test-key-12345";

        BacktestJob job1 = createJob("AAPL", idempotencyKey);
        BacktestJob job2 = createJob("TSLA", idempotencyKey); // Same key, different data

        // Act & Assert
        backtestJobRepository.save(job1);
        assertThrows(DataIntegrityViolationException.class, () -> {
            backtestJobRepository.saveAndFlush(job2);
        }, "Duplicate idempotency key should throw exception");
    }

    @Test
    void testDifferentIdempotencyKeys_Allowed() {
        // Arrange
        BacktestJob job1 = createJob("AAPL", "key-1");
        BacktestJob job2 = createJob("AAPL", "key-2");

        // Act
        BacktestJob saved1 = backtestJobRepository.save(job1);
        BacktestJob saved2 = backtestJobRepository.save(job2);

        // Assert
        assertNotNull(saved1.getId());
        assertNotNull(saved2.getId());
        assertNotEquals(saved1.getId(), saved2.getId());
    }

    @Test
    void testNullIdempotencyKey_NotAllowed() {
        // Arrange
        BacktestJob job = createJob("AAPL", null);

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            backtestJobRepository.saveAndFlush(job);
        }, "Null idempotency key should not be allowed");
    }

    @Test
    void testBacktestResult_RequiresValidJob() {
        // Arrange
        BacktestJob job = createJob("AAPL", "key-123");
        BacktestJob savedJob = backtestJobRepository.save(job);

        BacktestResult result = BacktestResult.builder()
                .job(savedJob)
                .totalReturn(new BigDecimal("15.5"))
                .sharpeRatio(new BigDecimal("1.25"))
                .maxDrawdown(new BigDecimal("-8.3"))
                .winRate(new BigDecimal("0.65"))
                .resultJson("[]")
                .build();

        // Act
        BacktestResult savedResult = backtestResultRepository.save(result);

        // Assert
        assertNotNull(savedResult.getId());
        assertEquals(savedJob.getId(), savedResult.getJob().getId());
    }

    @Test
    void testBacktestResult_NullJob_NotAllowed() {
        // Arrange
        BacktestResult result = BacktestResult.builder()
                .job(null) // Null job
                .totalReturn(new BigDecimal("15.5"))
                .sharpeRatio(new BigDecimal("1.25"))
                .maxDrawdown(new BigDecimal("-8.3"))
                .winRate(new BigDecimal("0.65"))
                .resultJson("[]")
                .build();

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            backtestResultRepository.saveAndFlush(result);
        }, "Null job in result should not be allowed");
    }

    @Test
    void testCascadeOnJobDeletion() {
        // Arrange
        BacktestJob job = createJob("AAPL", "cascade-test-key");
        BacktestJob savedJob = backtestJobRepository.save(job);

        BacktestResult result = BacktestResult.builder()
                .job(savedJob)
                .totalReturn(new BigDecimal("15.5"))
                .sharpeRatio(new BigDecimal("1.25"))
                .maxDrawdown(new BigDecimal("-8.3"))
                .winRate(new BigDecimal("0.65"))
                .resultJson("[]")
                .build();
        BacktestResult savedResult = backtestResultRepository.save(result);

        // Act - Delete job
        backtestJobRepository.delete(savedJob);
        backtestJobRepository.flush();

        // Assert - Result should be deleted (if cascade is configured)
        // OR should fail with foreign key constraint (if cascade not configured)
        // This depends on your JPA configuration
        boolean resultExists = backtestResultRepository.findById(savedResult.getId()).isPresent();

        // Document the behavior - adjust based on your cascade configuration
        // If CASCADE: assertFalse(resultExists)
        // If NO CASCADE: The delete would have thrown exception
    }

    @Test
    void testJobStatusEnum_AllValuesValid() {
        // Arrange & Act
        BacktestJob submittedJob = createJobWithStatus(JobStatus.SUBMITTED);
        BacktestJob queuedJob = createJobWithStatus(JobStatus.QUEUED);
        BacktestJob runningJob = createJobWithStatus(JobStatus.RUNNING);
        BacktestJob completedJob = createJobWithStatus(JobStatus.COMPLETED);
        BacktestJob failedJob = createJobWithStatus(JobStatus.FAILED);

        // Assert - All should save successfully
        assertDoesNotThrow(() -> {
            backtestJobRepository.save(submittedJob);
            backtestJobRepository.save(queuedJob);
            backtestJobRepository.save(runningJob);
            backtestJobRepository.save(completedJob);
            backtestJobRepository.save(failedJob);
            backtestJobRepository.flush();
        });
    }

    @Test
    void testRequiredFields_NotNull() {
        // Arrange - Missing required fields
        BacktestJob job = new BacktestJob();
        job.setIdempotencyKey("test-key");
        // Missing strategyName, symbol, dates, etc.

        // Act & Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            backtestJobRepository.saveAndFlush(job);
        }, "Missing required fields should throw exception");
    }

    @Test
    void testDateRange_AnyOrderAllowed() {
        // Arrange - Start date after end date (DB doesn't enforce logic)
        BacktestJob job = createJob("AAPL", "date-test-key");
        job.setStartDate(LocalDate.of(2024, 12, 31));
        job.setEndDate(LocalDate.of(2024, 1, 1));

        // Act
        BacktestJob saved = backtestJobRepository.save(job);

        // Assert - DB allows it (business logic should validate)
        assertNotNull(saved.getId());
        assertTrue(saved.getStartDate().isAfter(saved.getEndDate()));
    }

    @Test
    void testRetryCount_CanBeNegative() {
        // Arrange - Negative retry count (should be prevented by business logic, not
        // DB)
        BacktestJob job = createJob("AAPL", "retry-test-key");
        job.setRetryCount(-1);

        // Act
        BacktestJob saved = backtestJobRepository.save(job);

        // Assert - DB allows it
        assertNotNull(saved.getId());
        assertEquals(-1, saved.getRetryCount());
    }

    @Test
    void testLargeParametersJson_Stored() {
        // Arrange - Very large JSON
        StringBuilder largeJson = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            if (i > 0)
                largeJson.append(",");
            largeJson.append("\"param").append(i).append("\":").append(i);
        }
        largeJson.append("}");

        BacktestJob job = createJob("AAPL", "large-json-key");
        job.setParametersJson(largeJson.toString());

        // Act
        BacktestJob saved = backtestJobRepository.save(job);

        // Assert
        assertNotNull(saved.getId());
        assertEquals(largeJson.toString(), saved.getParametersJson());
    }

    @Test
    void testFindByIdempotencyKey_WorksCorrectly() {
        // Arrange
        String key = "find-test-key";
        BacktestJob job = createJob("AAPL", key);
        backtestJobRepository.save(job);

        // Act
        var found = backtestJobRepository.findByIdempotencyKey(key);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(key, found.get().getIdempotencyKey());
        assertEquals("AAPL", found.get().getSymbol());
    }

    @Test
    void testFindByIdempotencyKey_NotFound() {
        // Act
        var found = backtestJobRepository.findByIdempotencyKey("non-existent-key");

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    void testJobId_AutoGenerated() {
        // Arrange
        BacktestJob job = createJob("AAPL", "autogen-key");
        assertNull(job.getId(), "ID should be null before save");

        // Act
        BacktestJob saved = backtestJobRepository.save(job);

        // Assert
        assertNotNull(saved.getId(), "ID should be generated after save");
        assertTrue(saved.getId() > 0);
    }

    @Test
    void testMultipleResults_SameJob_Allowed() {
        // Arrange
        BacktestJob job = createJob("AAPL", "multi-result-key");
        BacktestJob savedJob = backtestJobRepository.save(job);

        BacktestResult result1 = createResult(savedJob, new BigDecimal("10.5"));
        BacktestResult result2 = createResult(savedJob, new BigDecimal("12.3"));

        // Act - Save multiple results for same job
        BacktestResult saved1 = backtestResultRepository.save(result1);
        BacktestResult saved2 = backtestResultRepository.save(result2);

        // Assert - Should be allowed (if no unique constraint)
        assertNotNull(saved1.getId());
        assertNotNull(saved2.getId());
        assertNotEquals(saved1.getId(), saved2.getId());
    }

    @Test
    void testTimestamps_PersistCorrectly() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        BacktestJob job = createJob("AAPL", "timestamp-key");
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        // Act
        BacktestJob saved = backtestJobRepository.save(job);

        // Assert
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    private BacktestJob createJob(String symbol, String idempotencyKey) {
        return BacktestJob.builder()
                .strategyName("BuyAndHold")
                .symbol(symbol)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parametersJson("{}")
                .status(JobStatus.SUBMITTED)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private BacktestJob createJobWithStatus(JobStatus status) {
        return BacktestJob.builder()
                .strategyName("BuyAndHold")
                .symbol("TEST")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parametersJson("{}")
                .status(status)
                .idempotencyKey("key-" + status.name() + "-" + System.nanoTime())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private BacktestResult createResult(BacktestJob job, BigDecimal totalReturn) {
        return BacktestResult.builder()
                .job(job)
                .totalReturn(totalReturn)
                .sharpeRatio(new BigDecimal("1.25"))
                .maxDrawdown(new BigDecimal("-8.3"))
                .winRate(new BigDecimal("0.65"))
                .resultJson("[]")
                .build();
    }
}
