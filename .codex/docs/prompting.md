# Codex Prompt Guide

Use this checklist when asking Codex for help in the hp repository:

1. Goal: one-sentence summary of the change.
2. Context: mention impacted files and relevant domain rules (points in 100 increments, max 1,000,000, user-level locking).
3. Constraints: tooling limits (no network), style (ASCII), dependency policies.
4. Acceptance criteria: behaviours, edge cases, performance and concurrency expectations.
5. Validation: commands to run (prefer `./gradlew test`, targeted tests, Jacoco report).
6. Deliverable: patch, design plan, review report, etc.

Template:
```
Goal: <summary>
Context:
- Files: <path:line>
- Domain rules: points in 100 increments, max balance 1,000,000, ReentrantLock per user
Constraints:
- No new dependencies, keep ASCII output
Acceptance criteria:
- <list>
Validation:
- ./gradlew test --tests "..."
Deliverable: <patch | plan+patch | review>
```
