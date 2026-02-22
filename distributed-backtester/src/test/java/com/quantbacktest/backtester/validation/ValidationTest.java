package com.quantbacktest.backtester.validation;

import com.quantbacktest.backtester.controller.dto.BacktestSubmissionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for input validation and constraint violations.
 */
class ValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRequest_NoViolations() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    void testMissingStrategyName_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStrategyName(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<BacktestSubmissionRequest> violation = violations.iterator().next();
        assertEquals("strategyName", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("required"));
    }

    @Test
    void testEmptyStrategyName_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStrategyName("");

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("required"));
    }

    @Test
    void testBlankStrategyName_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStrategyName("   ");

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
    }

    @Test
    void testMissingSymbol_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setSymbol(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("symbol", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testEmptySymbol_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setSymbol("");

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
    }

    @Test
    void testMissingStartDate_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setStartDate(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("startDate", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testMissingEndDate_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setEndDate(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("endDate", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testNullParameters_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setParameters(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("parameters", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testEmptyParameters_Valid() {
        // Arrange - Empty map is valid (e.g., for BuyAndHold strategy)
        BacktestSubmissionRequest request = createValidRequest();
        request.setParameters(new HashMap<>());

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty(), "Empty parameters should be valid");
    }

    @Test
    void testNullInitialCapital_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setInitialCapital(null);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertEquals("initialCapital", violations.iterator().next().getPropertyPath().toString());
    }

    @Test
    void testNegativeInitialCapital_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setInitialCapital(new BigDecimal("-1000.00"));

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        ConstraintViolation<BacktestSubmissionRequest> violation = violations.iterator().next();
        assertEquals("initialCapital", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("positive"));
    }

    @Test
    void testZeroInitialCapital_Violation() {
        // Arrange
        BacktestSubmissionRequest request = createValidRequest();
        request.setInitialCapital(BigDecimal.ZERO);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("positive"));
    }

    @Test
    void testMultipleViolations() {
        // Arrange
        BacktestSubmissionRequest request = new BacktestSubmissionRequest();
        // All fields null

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.size() >= 5, "Should have violations for all required fields");
    }

    @Test
    void testValidDifferentStrategies() {
        // BuyAndHold
        BacktestSubmissionRequest request1 = createValidRequest();
        request1.setStrategyName("BuyAndHold");
        request1.setParameters(new HashMap<>());
        assertTrue(validator.validate(request1).isEmpty());

        // MovingAverageCrossover
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", 10);
        params.put("longPeriod", 20);
        BacktestSubmissionRequest request2 = createValidRequest();
        request2.setStrategyName("MovingAverageCrossover");
        request2.setParameters(params);
        assertTrue(validator.validate(request2).isEmpty());
    }

    @Test
    void testDateRangeCombinations() {
        // Same date
        BacktestSubmissionRequest request1 = createValidRequest();
        request1.setStartDate(LocalDate.of(2024, 1, 1));
        request1.setEndDate(LocalDate.of(2024, 1, 1));
        assertTrue(validator.validate(request1).isEmpty(), "Same day should be valid");

        // Start after end (logical error but passes validation)
        BacktestSubmissionRequest request2 = createValidRequest();
        request2.setStartDate(LocalDate.of(2024, 12, 31));
        request2.setEndDate(LocalDate.of(2024, 1, 1));
        assertTrue(validator.validate(request2).isEmpty(),
                "Validator doesn't check date order - business logic should");

        // Historical dates
        BacktestSubmissionRequest request3 = createValidRequest();
        request3.setStartDate(LocalDate.of(2020, 1, 1));
        request3.setEndDate(LocalDate.of(2023, 12, 31));
        assertTrue(validator.validate(request3).isEmpty());

        // Future dates
        BacktestSubmissionRequest request4 = createValidRequest();
        request4.setStartDate(LocalDate.of(2030, 1, 1));
        request4.setEndDate(LocalDate.of(2030, 12, 31));
        assertTrue(validator.validate(request4).isEmpty(), "Future dates pass validation");
    }

    @Test
    void testEdgeCaseInitialCapitalValues() {
        // Very small positive value
        BacktestSubmissionRequest request1 = createValidRequest();
        request1.setInitialCapital(new BigDecimal("0.01"));
        assertTrue(validator.validate(request1).isEmpty());

        // Very large value
        BacktestSubmissionRequest request2 = createValidRequest();
        request2.setInitialCapital(new BigDecimal("999999999999.99"));
        assertTrue(validator.validate(request2).isEmpty());

        // Exactly 1
        BacktestSubmissionRequest request3 = createValidRequest();
        request3.setInitialCapital(BigDecimal.ONE);
        assertTrue(validator.validate(request3).isEmpty());
    }

    @Test
    void testSpecialCharactersInSymbol() {
        // Normal ticker
        BacktestSubmissionRequest request1 = createValidRequest();
        request1.setSymbol("AAPL");
        assertTrue(validator.validate(request1).isEmpty());

        // With special characters (should be valid at validation level)
        BacktestSubmissionRequest request2 = createValidRequest();
        request2.setSymbol("BRK.B");
        assertTrue(validator.validate(request2).isEmpty());

        // Lowercase
        BacktestSubmissionRequest request3 = createValidRequest();
        request3.setSymbol("tsla");
        assertTrue(validator.validate(request3).isEmpty());

        // Numbers
        BacktestSubmissionRequest request4 = createValidRequest();
        request4.setSymbol("3M");
        assertTrue(validator.validate(request4).isEmpty());
    }

    @Test
    void testComplexParameterValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("intValue", 42);
        params.put("doubleValue", 3.14159);
        params.put("stringValue", "test");
        params.put("boolValue", true);
        params.put("arrayValue", new int[] { 1, 2, 3 });

        BacktestSubmissionRequest request = createValidRequest();
        request.setParameters(params);

        // Act
        Set<ConstraintViolation<BacktestSubmissionRequest>> violations = validator.validate(request);

        // Assert
        assertTrue(violations.isEmpty(), "Complex parameter values should be valid");
    }

    private BacktestSubmissionRequest createValidRequest() {
        Map<String, Object> params = new HashMap<>();
        params.put("shortPeriod", 5);
        params.put("longPeriod", 20);

        return BacktestSubmissionRequest.builder()
                .strategyName("MovingAverageCrossover")
                .symbol("AAPL")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .parameters(params)
                .initialCapital(new BigDecimal("10000.00"))
                .build();
    }
}
