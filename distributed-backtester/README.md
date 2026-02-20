# Distributed Backtester

A monolithic Spring Boot backend service for distributed backtesting of quantitative trading strategies.

## Technology Stack

- **Java**: 17
- **Framework**: Spring Boot 3.2.2
- **Build Tool**: Maven
- **Database**: PostgreSQL
- **Cache**: Redis
- **Monitoring**: Spring Boot Actuator

## Project Structure

```
src/main/java/com/quantbacktest/backtester/
├── controller/          # REST API controllers
├── service/            # Business logic layer
├── domain/             # Domain entities and models
├── repository/         # Data access layer (JPA repositories)
├── infrastructure/     # External integrations and infrastructure
└── config/             # Configuration classes
```

## Dependencies

- Spring Web - REST API endpoints
- Spring Data JPA - Database access
- PostgreSQL Driver - Database connectivity
- Spring Data Redis - Caching and distributed data
- Spring Boot Actuator - Health checks and metrics
- Lombok - Boilerplate code reduction

## Configuration

The application can be configured via environment variables:

### Database (PostgreSQL)

- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 5432)
- `DB_NAME` - Database name (default: backtester_db)
- `DB_USERNAME` - Database username (default: postgres)
- `DB_PASSWORD` - Database password (default: password)

### Cache (Redis)

- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_PASSWORD` - Redis password (optional)

### Application

- `SERVER_PORT` - Application port (default: 8080)
- `LOG_LEVEL` - Application log level (default: INFO)

## Build and Run

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- Redis 6+

### Build

```bash
mvn clean install
```

### Run

```bash
mvn spring-boot:run
```

The application will start on `http://localhost:8080/api`

## Health Check

Access the health endpoint at: `http://localhost:8080/api/actuator/health`

## Actuator Endpoints

- `/api/actuator/health` - Health status
- `/api/actuator/info` - Application info
- `/api/actuator/metrics` - Application metrics
- `/api/actuator/prometheus` - Prometheus metrics

## License

Proprietary
