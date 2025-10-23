# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application implementing a point management system with TDD practices. The project demonstrates:
- Domain-driven design with rich domain models
- User-specific concurrency control using ReentrantLock
- In-memory storage simulation with artificial delays
- Comprehensive test coverage (unit, integration, and concurrency tests)

**Language**: Kotlin with Spring Boot
**Java Version**: 17
**Testing Frameworks**: JUnit 5, Kotest

## Build and Test Commands

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "io.hhplus.tdd.point.PointServiceJUnitTest"

# Run a specific test method
./gradlew test --tests "io.hhplus.tdd.point.PointServiceJUnitTest.charge should increase user point"

# Run the application
./gradlew bootRun

# Generate code coverage report (Jacoco)
./gradlew test jacocoTestReport
# Report location: build/reports/jacoco/test/html/index.html

# Clean build artifacts
./gradlew clean
```

## Architecture

### Layered Architecture
- **Controller Layer** (`PointController`): REST API endpoints for point operations
- **Service Layer** (`PointService`): Business logic orchestration and concurrency control
- **Domain Layer** (`UserPoint`, `PointHistory`): Domain models containing business rules
- **Repository Layer** (`UserPointTable`, `PointHistoryTable`): In-memory data storage simulation

### Key Design Patterns

**Rich Domain Model**: Business rules (validation, calculations) are encapsulated in domain models (`UserPoint.charge()`, `UserPoint.use()`) rather than in service layer. This improves cohesion and testability.

**User-Specific Locking**: `PointService` uses `ConcurrentHashMap<Long, ReentrantLock>` to serialize requests per user while allowing parallel processing for different users. Locks are automatically cleaned up when no threads are waiting.

**Immutable Data Classes**: Domain models use Kotlin data classes with `val` properties and return new instances on state changes (e.g., `UserPoint.charge()` returns a new `UserPoint`).

### Business Rules
- Points can only be charged/used in increments of 100
- Maximum balance is 1,000,000 points
- All policy violations return HTTP 400 via `ApiControllerAdvice`
- Business logic validation happens in domain models (`UserPoint` companion object)

### Concurrency Strategy
The application uses **user-level pessimistic locking**:
- `PointService.withUserLock()` acquires a ReentrantLock per user ID
- Same user's requests are serialized (data consistency)
- Different users' requests run in parallel (high throughput)
- Locks are removed from map when no threads are waiting (memory efficiency)

**Limitation**: This approach only works within a single server instance. For multi-server deployments, consider distributed locks (Redis, database row locks).

### Storage Layer
`UserPointTable` and `PointHistoryTable` are in-memory stores using HashMap with simulated I/O delays (0-300ms via `Thread.sleep()`). They are **not thread-safe** and rely on external synchronization from `PointService`.

## Testing Strategy

The codebase has three test layers:

1. **Unit Tests** (`PointServiceJUnitTest`, `PointServiceKotestSpec`)
   - Test business logic in isolation
   - Verify policy enforcement (amount units, max balance, insufficient balance)
   - Two implementations: one with JUnit 5, one with Kotest (demonstrating different approaches)

2. **Integration Tests** (`PointControllerIntegrationTest`)
   - Test REST API endpoints end-to-end
   - Verify HTTP status codes and response bodies
   - Test exception handling via `ApiControllerAdvice`

3. **Concurrency Tests** (`PointConcurrencyIntegrationTest`)
   - Verify that concurrent charge requests for the same user are properly serialized
   - Ensure no data loss under concurrent load

Run tests during development to ensure changes don't break existing functionality.

## Code Style Notes

The codebase features extensive inline documentation with:
- Detailed explanations of Spring annotations
- Concurrency pattern explanations
- Kotlin idiom usage (Elvis operator, data classes, higher-order functions)
- Business rule documentation in code comments

When adding new features, follow this documentation style to maintain consistency.
- "rr"