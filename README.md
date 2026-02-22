# 📈 Distributed Backtesting Engine

> A production-grade, concurrency-safe backtesting engine for quantitative trading strategies

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-12%2B-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-6%2B-red.svg)](https://redis.io/)
[![Test Coverage](https://img.shields.io/badge/coverage-80%25+-success.svg)](./TEST_SUMMARY.md)
[![License](https://img.shields.io/badge/license-Proprietary-lightgrey.svg)](LICENSE)

Test and optimize trading strategies against historical market data with distributed job processing, comprehensive performance metrics, and enterprise-grade reliability.

---

## ✨ Features

- ** Concurrency-Safe**: Pessimistic + optimistic locking, idempotency guarantees, no race conditions
- ** High Performance**: 40+ jobs/second throughput, 50x faster parameter sweeps with batch processing
- ** Built-in Strategies**: Moving Average Crossover, Buy & Hold, easily extensible
- ** Rich Metrics**: Sharpe ratio, Sortino ratio, max drawdown, total return, and more
- ** Distributed Processing**: Redis-based job queue with configurable worker threads
- ** Persistent Storage**: PostgreSQL with comprehensive indexing and query optimization
- ** Thoroughly Tested**: 145+ tests, 80%+ coverage, concurrency validation
- ** Production-Ready**: Actuator metrics, health checks, Prometheus integration
- ** Parameter Optimization**: Test multiple parameter combinations in parallel
- ** Retry Logic**: Automatic retry with exponential backoff for failed jobs

---

##  Quick Start

### Prerequisites

- **Java 17+** | **Maven 3.6+** | **PostgreSQL 12+** | **Redis 6+**

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/quant-backtest-engine.git
cd quant-backtest-engine/distributed-backtester

# Build the project
mvn clean install

# Set up PostgreSQL database
psql -U postgres -c "CREATE DATABASE backtester_db;"

# Run the application
mvn spring-boot:run
```

The application will start on `http://localhost:8080/api`

### Verify Installation

```bash
curl http://localhost:8080/api/actuator/health
```

---

##  Usage

### Submit a Backtest

```bash
curl -X POST http://localhost:8080/api/backtests \
  -H "Content-Type: application/json" \
  -d '{
    "strategyName": "MovingAverageCrossover",
    "symbol": "AAPL",
    "startDate": "2023-01-01",
    "endDate": "2023-12-31",
    "initialCapital": 10000.00,
    "parameters": {
      "shortPeriod": 20,
      "longPeriod": 50
    }
  }'
```

**Response:**
```json
{
  "jobId": 123,
  "status": "QUEUED",
  "submittedAt": "2024-01-15T10:30:00"
}
```

### Get Results

```bash
curl http://localhost:8080/api/backtests/123
```

**Response:**
```json
{
  "jobId": 123,
  "status": "COMPLETED",
  "duration": 245,
  "result": {
    "totalReturn": 1250.50,
    "returnPercentage": 12.51,
    "sharpeRatio": 1.85,
    "sortinoRatio": 2.12,
    "maxDrawdown": -8.5,
    "numberOfTrades": 42,
    "finalPortfolioValue": 11250.50
  }
}
```

### Parameter Sweep (Optimization)

Test multiple parameter combinations:

```bash
curl -X POST http://localhost:8080/api/backtests/sweeps \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MA Optimization",
    "symbol": "AAPL",
    "startDate": "2023-01-01",
    "endDate": "2023-12-31",
    "initialCapital": 10000.00,
    "optimizationMetric": "sharpeRatio",
    "strategies": [{
      "strategyName": "MovingAverageCrossover",
      "parameterCombinations": [
        {"shortPeriod": 10, "longPeriod": 50},
        {"shortPeriod": 20, "longPeriod": 200}
      ]
    }]
  }'
```

---

##  Architecture

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP (REST API)
       ▼
┌──────────────────────────────────────┐
│    Spring Boot Application           │
│  ┌────────────┐   ┌───────────────┐ │
│  │ REST API   │   │  Background   │ │
│  │ Controllers│   │  Workers (3)  │ │
│  └─────┬──────┘   └───────┬───────┘ │
│        │   Service Layer   │         │
│        └─────────┬─────────┘         │
└──────────────────┼───────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
   ┌─────────┐         ┌──────────┐
   │PostgreSQL│         │  Redis   │
   │ Storage │         │  Queue   │
   └─────────┘         └──────────┘
```

### Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Language | Java 17 | Core application |
| Framework | Spring Boot 3.2.2 | REST API, DI, lifecycle |
| Database | PostgreSQL | Persistent storage |
| Cache/Queue | Redis | Job queue + caching |
| Testing | JUnit 5 + Mockito | Unit & integration tests |
| Build | Maven | Dependency management |
| Monitoring | Spring Actuator | Health & metrics |

---

##  Performance

### High-Load Test Results (100 Concurrent Jobs)

| Metric | Value |
|--------|-------|
| **Throughput** | 40.6 jobs/second |
| **Total Time** | 2.46 seconds |
| **Success Rate** | 100% |
| **Race Conditions** | 0 |
| **Duplicates** | 0 |

### Parameter Sweep Performance

- **50x improvement** after batch optimization
- 100-job sweep completes in ~3.5 seconds
- Redis caching provides ~95% cache hit rate

---

##  Available Strategies

### 1. Buy and Hold
Simple strategy that buys maximum shares on first tick and holds until the end.

**Parameters**: None

### 2. Moving Average Crossover
Technical analysis strategy using golden cross (buy) and death cross (sell) signals.

**Parameters**:
- `shortPeriod` - Fast MA period (e.g., 10, 20)
- `longPeriod` - Slow MA period (e.g., 50, 200)

### Create Your Own

Implement the `Strategy` interface:

```java
@Component
public class MyStrategy implements Strategy {
    @Override
    public void onTick(MarketData data, Portfolio portfolio) {
        // Your trading logic here
    }
    
    @Override
    public void onFinish(Portfolio portfolio) {
        // Cleanup
    }
    
    @Override
    public String getName() {
        return "MyStrategy";
    }
}
```

---

##  Configuration

Configure via environment variables:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=backtester_db
DB_USERNAME=postgres
DB_PASSWORD=password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Application
SERVER_PORT=8080
WORKER_THREAD_COUNT=3  # Adjust based on CPU cores
LOG_LEVEL=INFO
```

**Production Tuning:**
```yaml
backtest:
  worker:
    thread-count: ${CPU_CORES}  # 1 worker per core

spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Workers + API threads
```

---

##  Testing

Run the comprehensive test suite:

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=HighLoadIntegrationTest

# Generate coverage report
mvn test
# Open: target/site/jacoco/index.html
```

**Test Coverage:**
- 145+ tests across 12 test classes
- 80%+ code coverage (enforced by JaCoCo)
- Concurrency safety validated
- High-load scenarios tested

---

##  Documentation

- **[PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)** - Comprehensive project documentation (1000+ lines)
- **[CONCURRENCY_IMPROVEMENTS.md](CONCURRENCY_IMPROVEMENTS.md)** - Concurrency safety details (422 lines)
- **[TEST_SUMMARY.md](TEST_SUMMARY.md)** - Test documentation (471 lines)
- **[API Reference](PROJECT_OVERVIEW.md#api-reference)** - Complete endpoint documentation
- **[Configuration Guide](PROJECT_OVERVIEW.md#configuration)** - Environment setup

---

##  Monitoring

### Health Check
```bash
curl http://localhost:8080/api/actuator/health
```

### Metrics
```bash
curl http://localhost:8080/api/actuator/metrics/backtest.execution.time
```

### Prometheus
```bash
curl http://localhost:8080/api/actuator/prometheus
```

---

## 🛠️ Development

### Project Structure

```
distributed-backtester/
├── src/main/java/com/quantbacktest/backtester/
│   ├── controller/         # REST API endpoints
│   ├── service/           # Business logic
│   ├── domain/            # Strategies & entities
│   ├── repository/        # Data access
│   ├── infrastructure/    # Workers & queue
│   └── config/            # Spring configuration
├── src/test/java/         # Test suite (145+ tests)
└── src/main/resources/    # Configuration files
```

### Building

```bash
# Clean build
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Run locally
mvn spring-boot:run

# Package for deployment
mvn package
```

---

## 🚢 Deployment

### Docker

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/distributed-backtester-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t backtester:latest .
docker run -p 8080:8080 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  backtester:latest
```

### Kubernetes

See [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md#3-deploy-to-production) for complete Kubernetes deployment configuration.

---


##  Security

### Concurrency Safety

✅ Pessimistic locking prevents duplicate processing  
✅ Optimistic locking detects concurrent modifications  
✅ Idempotency keys prevent duplicate submissions  
✅ Atomic Redis operations (BLPOP/RPUSH)  
✅ Transaction management with rollback  

### Data Integrity

✅ Foreign key constraints  
✅ Unique constraints on idempotency keys  
✅ Input validation with Jakarta Bean Validation  
✅ Retry logic with bounded attempts  

---

