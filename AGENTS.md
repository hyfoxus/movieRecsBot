# AGENTS.md

## Project Overview

This repository contains an AI-powered Telegram bot for movie recommendations.

High-level flow:

1. A user sends a message to the Telegram bot.
2. The bot detects and stores the user's language.
3. The user message is translated to English before being sent to the main LLM.
4. A single LLM call classifies intent (RECOMMENDATION vs INFORMATION) and extracts structured
   preferences (actors, genres, runtime, etc.) at the same time — `UserIntentParser` used to do this
   as two separate calls; they were merged since the schemas fully overlapped. A short list of
   obviously generic phrases ("дай рекомендации", "surprise me", "more", …) skips this LLM call
   entirely via a fast path.
5. Depending on intent, the bot calls the MCP server to:
    - get movie recommendations based on user request and stored preferences
    - retrieve information about movies available in the database
6. The final response is sent back to the user through Telegram, with inline buttons attached to a
   recommendation batch: 👍/👎 per movie (writes back into the user's liked/blocked genres) and a
   "🔁 Ещё похожие" ("more like these") button that re-runs the same original request while excluding
   already-shown titles. See "Recommendation session state" below.

The project is organized as a monorepo. Current main modules:

- `apps/movieRecBot` — Telegram bot application
- `apps/movie-mcp-server` — MCP server used by the bot
- `apps/imdb-vec` — movie database / vector indexing service
- `apps/normalizer` — normalization-related module

## Main Architectural Principles

When making changes, preserve these architectural boundaries:

- `movieRecBot` owns:
    - Telegram update handling
    - user language capture
    - request preprocessing
    - translation to English before LLM processing
    - orchestration of recommendation and info-retrieval flows
    - conversation-history retention, including rolling summarization of older turns
      (`UserContextService.compactIfNeeded` — see "Conversation history compaction" below)
    - recommendation session state for pagination/feedback buttons (`RecommendationSessionStore`)
    - sending replies back to Telegram

- `movie-mcp-server` owns:
    - MCP endpoints/tools/contracts
    - querying the movie data layer
    - returning structured recommendation/search results to the bot
    - a short-TTL in-process cache in front of the embedding + pgvector search
      (`MovieSearchService` — no external cache dependency, just a bounded LRU)

- `imdb-vec` owns:
    - movie catalog ingestion
    - vector/embedding-related search support
    - persistence and enrichment of movie records

Agents must avoid collapsing these responsibilities into one module unless explicitly requested.

## What Agents Should Optimize For

Priorities, in order:

1. Preserve working behavior
2. Keep module boundaries clean
3. Prefer small, reviewable changes
4. Add tests for changed behavior
5. Keep docs and configuration consistent with the code
6. Avoid speculative refactors unless they directly support the task

## Code Style and Design Rules

### General

- Keep controllers and Telegram adapters thin.
- Put business logic into services.
- Prefer explicit names over clever abstractions.
- Keep DTOs separate from persistence entities where practical.
- Avoid introducing unnecessary frameworks or infrastructure.
- Do not silently change public API behavior, Telegram commands, or MCP contracts.

### Spring / Java

- Prefer constructor injection.
- Use `@Service`, `@Component`, and `@Repository` consistently.
- Validate external input at the edge.
- Keep transactional boundaries explicit.
- Prefer small focused classes over god objects.
- Prefer composition over inheritance.

### LLM / AI Flow

- Preserve the current pipeline where user input is normalized and translated before main LLM processing.
- Keep prompt-building logic centralized and testable.
- When changing recommendation behavior, separate:
    - intent parsing
    - prompt construction
    - MCP interaction
    - response formatting
- Do not hardcode provider-specific assumptions unless already part of the module configuration.

### MCP Integration

- Treat the MCP server contract as a stable interface.
- If changing request/response payloads, update both sides consistently.
- Prefer typed request/response models over loosely structured maps.
- Add tests for any contract change.

### Database / Persistence

- Prefer migrations over ad hoc schema drift.
- Keep schema, entities, and seed/bootstrap logic aligned.
- Do not rename columns or tables without updating migrations, code, and docs.
- Be careful with data-destructive changes.

### Telegram Callback Buttons

- Callback routing in `UpdateRouter` is a manual `if`/`startsWith` chain on `callbackQuery.getData()` —
  there is no shared interface or dispatch table. A new callback handler is a plain `@Component` with
  a `handle(CallbackQuery)` method, wired into `UpdateRouter` with its own prefix check placed before
  the `MiniMenuCallbackHandler` catch-all.
- Existing prefixes: `"menu:<action>"` (profile menu, exact-match `switch`), `"report:start"`
  (complaint flow), `"rate:<idx>:up|down"` (per-movie feedback, `idx` is a position in
  `RecommendationSessionStore`'s last shown batch — not a stable movie id), `"more:go"` (pagination).
- Always return an `AnswerCallbackQuery` (or a `SendMessage` that a decorator can still attach a
  keyboard to) so Telegram's loading spinner on the tapped button is dismissed.
- `RecommendationMovie` has no stable id (no tconst) and no actor field. Per-movie state is keyed by
  position in the last shown batch, and feedback is genre-only — extending it to actors would require
  changing the LLM JSON contract (`prompts.yml`'s `json-response-template`) first.

### Conversation History Compaction

- `UserContextService` keeps a rolling window of the last 30 raw turns per chat plus any accumulated
  `"Summary: …"` rows (always included, regardless of age, in `historyAsOneString`). Once raw turns
  exceed 45, `compactIfNeeded` summarizes everything beyond the most recent 30 into one new summary
  row via `HistorySummarizer` and deletes the summarized raw rows. This is best-effort: any failure
  (including the summarization LLM call itself) just skips compaction for that turn, non-fatally.
- Summaries are not recursively re-consolidated — a very long-lived chat accumulates one small summary
  row per compaction pass rather than one ever-growing summary. Acceptable for now; revisit if a chat's
  summary-row count itself becomes a problem.

## Testing Expectations

For any non-trivial change, agents should add or update tests.

Prefer:

- unit tests for services and helpers
- integration tests for Spring wiring and repository behavior
- contract-style tests for MCP interactions
- focused tests for translation / language handling / prompt orchestration

When changing recommendation logic, test at least:

- intent classification path
- recommendation path
- movie info retrieval path
- multilingual input handling
- fallback/error behavior

## Commands Agents Can Use

From repository root:

### Build everything
```bash
./mvnw clean verify