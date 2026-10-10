# Design Decisions

## 001: Monorepo at AIDocument root
Backend and AI service share API contracts, so one repo allows atomic changes. Trade-off: single history for two languages.

## 002: Modular monolith + one Python service
Avoid microservice overhead until there is a clear reason.

## 003: pgvector in the main PostgreSQL
One database for metadata and vectors keeps filtering by contract/user simple. Revisit if scale demands a dedicated vector store.

## 005: Flyway owns the schema, Hibernate only validates
`ddl-auto: validate` plus versioned SQL migrations. Trade-off: more typing than auto-DDL, but schema changes are reviewable and reproducible, and we need raw SQL for pgvector anyway.

## 006: Spring Boot 4.1.1 (Initializr default stable) on Java 21
Chosen because it was the current stable release at project start.

## 007: UUID primary keys
Not guessable, unlike sequential IDs. Trade-off: larger indexes. Authorization checks are still required.

## 008: owner_id without a foreign key until Phase 11
Ownership checks are needed from day one; the users table comes later. A FK will be added by a later migration.

## 009: Status as VARCHAR + CHECK constraint, @Enumerated(STRING)
Safe against enum reordering, and the database rejects invalid values even if application code has a bug.

## 010: No Lombok @Data on JPA entities
@Data generates equals/hashCode/toString over all fields, which interacts badly with Hibernate proxies and lazy loading. Use @Getter/@Setter only.

## 011: Package by layer
Packages under `com.contractai.backend`: `entity`, `enums`, `repository`, `service`, `controller`, `dto`. Chosen for readability and familiarity. Trade-off: related classes for one feature are spread across packages; package-by-feature scales better for large codebases. (`enums` because `enum` is a reserved word.)

## 004: Maven via wrapper (mvnw)
No global Maven install; the wrapper pins the version per project.
