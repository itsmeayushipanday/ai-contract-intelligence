# Progress

## Completed
- Phase 0: JDK 21, Git, Python 3.12.10, Docker 29.8.2 installed. Repo scaffolded, pgvector DB verified.
- Phase 1.1: Spring Boot 4.1.1 skeleton (Java 21, Maven wrapper) in `backend/`. Connects to PostgreSQL, Flyway migration V1 enables pgvector. `BackendApplicationTests.contextLoads` passes (run via `backend/run-local.ps1 test`).

- Phase 1.2: `contracts` table (Flyway V2), `Contract` entity, `ContractStatus` enum, `ContractRepository`. `ContractRepositoryTest` (4 tests, real Postgres) plus the context test: 5 tests, all passing.

## In progress
- Phase 1.3: service layer, DTOs, CRUD controller.

## Pending
- Phase 1.4 validation, global error handling, controller/service tests.
- Phases 2-13 (see ProblemStatement).

## Known limitations
- Tests currently need the local Postgres container running (Testcontainers not yet introduced).
- Postgres container stops when Docker Desktop restarts; run the compose `up -d` command again.
