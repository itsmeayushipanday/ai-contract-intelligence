# Design Decisions

## 001: Monorepo at AIDocument root
Backend and AI service share API contracts, so one repo allows atomic changes. Trade-off: single history for two languages.

## 002: Modular monolith + one Python service
Avoid microservice overhead until there is a clear reason.

## 003: pgvector in the main PostgreSQL
One database for metadata and vectors keeps filtering by contract/user simple. Revisit if scale demands a dedicated vector store.

## 004: Maven via wrapper (mvnw)
No global Maven install; the wrapper pins the version per project.
