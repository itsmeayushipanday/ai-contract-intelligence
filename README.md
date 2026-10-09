# AI Contract Intelligence Platform

Upload PDF contracts, ask questions with page-level citations (RAG), summarize, flag potential risks, and compare contracts.

**Status:** Phase 0 (setup). See `docs/progress.md`.

## Stack
Java 21 / Spring Boot, Python / FastAPI, PostgreSQL + pgvector, Claude API.

## Local setup
1. Copy `.env.example` to `.env` and adjust values.
2. Start the database:
   ```
   docker compose -f infrastructure/docker-compose.yml --env-file .env up -d
   ```
