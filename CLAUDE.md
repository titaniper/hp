# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Prompt Engineering Guidelines

When prompting Claude Code or Codex for changes in this repository, follow the structure below so the assistants receive enough context to respond accurately and efficiently.

### 1. Frame the task up front
- State the goal in one sentence (`"Fix null pointer when charging points"`).
- Mention the expected output format (e.g., "provide patch", "summarize findings", "no code changes").
- Clarify whether you want step-by-step reasoning or just the final answer.

### 2. Provide repository context Claude cannot infer
- Reference relevant files with workspace-relative paths and line numbers when possible (`PointService.kt:45`).
- Summarize domain rules that influence the change (point increments of 100, max 1,000,000 balance).
- Call out existing documentation or comments that must stay consistent.
- Note any external constraints (no network, keep ASCII, reuse ReentrantLock strategy, etc.).

### 3. Define acceptance criteria
- List functional expectations and edge cases to cover.
- Specify tests or commands to run (`./gradlew test`, targeted test classes, Jacoco report if needed).
- Include performance or concurrency conditions (e.g., "maintain per-user locking").

### 4. Explain how to validate the work
- Point to required test suites or manual checks.
- State whether to update or create new tests.
- Mention follow-up tasks such as documenting API changes or updating README.

### 5. Ask for the right level of detail
- Decide between a quick fix, deep architectural review, or brainstorming options.
- Request iterative feedback if unsure of the direction ("Propose three approaches before coding").
- For large tasks, ask the assistant to outline a plan before editing.

### Prompt templates

**Bug fix**
```
Goal: <short description>
Context:
- Issue observed at <path:line>
- Related classes: <file list>
Constraints:
- Preserve <rule>
- No new dependencies
Acceptance criteria:
- <list>
Validation:
- ./gradlew test --tests "..."
Deliverable: <patch | summary>
```

**Feature addition**
```
Goal: <feature>
Context:
- Current behaviour: <summary>
- Domain rules: <summary>
Plan request: Ask for a brief implementation plan first.
Implementation constraints:
- Follow existing locking strategy
- Update docs in CLAUDE.md if behaviour changes
Acceptance criteria:
- <list>
Validation:
- ./gradlew build
Deliverable: Proposed plan -> apply_patch diff
```

**Code review**
```
Goal: Review MR #<id>
Diff summary: <key changes>
Focus areas: correctness, concurrency, test coverage
Questions: <open questions>
Deliverable: Findings ordered by severity, then risks/tests.
```

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
