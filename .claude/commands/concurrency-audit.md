Review concurrency-sensitive code paths.

Focus:
- Per-user ReentrantLock map usage and lock release semantics
- In-memory table thread safety (UserPointTable, PointHistoryTable)
- Potential race conditions when charging/using points concurrently

Deliverable: Findings grouped by severity with suggested test coverage (e.g., PointConcurrencyIntegrationTest)
