# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

Phase 1's first data slice is implemented and peer-review hardening is complete: OKX public funding-rate history is validated into typed observations and atomically persisted as append-only facts with a successful execution. Logical `funding_sync_request` and immutable `analysis_run` executions are separate. A minimal `sync-market` CLI is implemented (single-page `latest`, cursor `backfill` over `[from,to)` with persisted page ledger/checkpoint/resume and honest coverage states, read-only `stale-list`, explicitly confirmed `stale-recover` with expected-run-id fencing; stale age is judged by MySQL `CURRENT_TIMESTAMP(6)`, never the JVM clock). Scheduling, cross-interval integrity checks, account fee data, and private/read-only account APIs are not implemented. `docs/` remains the authoritative product specification; read it before extending the code:

- `docs/README.md` — goals, principles, tech stack, V1 tool plan, phased rollout
- `docs/01-Income-Source.md` — income source definitions, lifecycle, backtest requirements
- `docs/02-Portfolio.md` — portfolio allocation rules, Incremental Net Benefit formula, scaling gates
- `docs/03-AI-Advisor.md` — AI advisor/researcher scope and boundaries

Documentation is written in Chinese; keep it that way.

## What This Project Is

A local, read-only personal tool for managing ~200,000 USDT of crypto income via existing OKX products (funding-rate arbitrage, simple earn, spot grid). Target: 10–20% long-term annualized net return. It is **not** a trading platform: no automated trading, AI never touches trade/withdrawal permissions, and the user executes all actions manually on OKX.

Core priorities, in order: **赚钱第一 (real net return), 工程第二 (engineering only where it moves return), AI 第三 (AI advises, never decides)**. The system's most frequent recommendation should be "hold" (保持).

## Implemented Tech Stack

- JDK 25 + Spring Boot 4.1.0 + Maven, single monolith (`app.cryptoincome` base package)
- MySQL 8.4 LTS via official Docker image (`compose.yaml`); app connects with the business user, not root; connections use UTC
- MyBatis-Plus 3.5.17 via `mybatis-plus-spring-boot4-starter` and `mybatis-plus-bom` — do NOT separately add MyBatis / `mybatis-spring-boot-starter` / MyBatis-Spring (dependency conflicts)
- Flyway is the only schema-migration path; no auto DDL
- JDK `HttpClient` + Jackson for OKX public and read-only account APIs
- JUnit 5 + Testcontainers MySQL for tests against real MySQL behavior
- Money: `BigDecimal`. Time: UTC `Instant`. Funding intervals follow actual `fundingTime`, never assumed 8h
- Deliberately excluded for V1: JPA, Spring Batch, message queues, microservices, real-time dashboards. Spring AI is added last, in an isolated adapter

## Architecture Rules (from the design docs — these are constraints, not suggestions)

- **Layering**: domain services depend only on project-defined Repository interfaces (for example `AnalysisRunRepository`), never on `BaseMapper` directly. MyBatis-Plus handles simple single-table CRUD; cash-flow attribution, income aggregation, backtest queries, and `analysis_run` state transitions use reviewable Mapper XML / explicit SQL. Business rules live in domain services, not in the ORM layer.
- **Three portfolio types must be kept separate**: Backtest / Shadow / Live, distinguished by `mode` + `run_id` with DB constraints forbidding cross-mode references. Live Portfolio can only be updated from OKX account snapshots and human-confirmed cash flows — never derived from backtest, shadow, or AI output.
- **Facts are append-only**: corrections insert new records; never silently overwrite history.
- Funding facts use a restricted mapper that exposes insertion and reads only; do not replace it with `BaseMapper`, because generic update/delete methods would bypass the append-only boundary.
- `AnalysisRunMapper` is also deliberately restricted and must never inherit `BaseMapper`; state changes use explicit conditional SQL only.
- **`analysis_run` state machine**: only executions that produce external or persistent side effects (currently funding sync/backfill) must be persisted as `analysis_run`, with states `CREATED → RUNNING → SUCCEEDED/FAILED`, domain-service transitions, state-conditioned updates, DB constraints, and idempotency keys. Pure read-only evaluate/report operations must not be forced into `analysis_run`; if a future evaluation needs durable audit evidence, prove that requirement and design its owner explicitly. Pages, reports, and AI remain read-only here.
- **Funding sync ownership**: `funding_sync_request` owns the logical single-page request; each `analysis_run` is one immutable attempt. Failure or stale recovery creates a new attempt and never reopens a terminal run. Facts + observations + both success transitions commit in one transaction. Stale recovery is explicit, not automatic, and old executions are fenced from committing.
- **Backfill ownership**: `funding_backfill_request` owns a logical backfill (window `[from,to)`, cursor, coverage state IDLE/RUNNING/COMPLETE + IN_PROGRESS/COMPLETE/BUDGET_EXHAUSTED/SOURCE_EXHAUSTED); `funding_backfill_page` is the append-only page ledger. Each page commits facts + observations + checkpoint in its own short transaction; the OKX HTTP call is never inside a transaction. `analysis_run.sync_request_id` XOR `backfill_request_id` identifies the owning store (DB CHECK enforced); generic `AnalysisRunService` must reject both. Coverage COMPLETE means BOUNDARY_REACHED only — the page cursor reached `from`; interval integrity (gap detection) is NOT checked and must never be claimed. Cursor must move strictly backward; a non-progressing page fails as CURSOR_STALLED. OKX `after` = older data; intervals are never assumed to be 8h.
- **Backfill statistics semantics (never conflate)**: `new_global_facts` = rows truly added to `funding_rate_fact`; `new_observation_links` = new run→fact links; `duplicate_obsation_links` = links already present for the current run (detected via `useAffectedRows=true`, where INSERT..ON DUPLICATE returns 0 for unchanged rows). Page receipts split `received` into `in_window_received`/`out_of_window_received` strictly by `[from,to)`; out-of-window records are still valid facts and are kept, but must not be counted as in-window. V4-era ledger rows keep the new columns NULL (UNKNOWN) — never backfill them with guesses.
- **AI boundary**: AI reads verified facts and generates Chinese explanations / research drafts only. It cannot modify authoritative numbers, portfolios, positions, or execution state. If AI and rule conclusions conflict, show the conflict — never let AI silently override. The system must remain fully functional with AI disabled.
- **Single decision formula**: all adjustments use Incremental Net Benefit over a common holding period H (new net income − current net income − switch cost − additional risk discount). Only act when clearly positive beyond estimation error; otherwise hold.
- Current scope includes public funding facts, the first-phase simple-earn vs smart-arbitrage assessment, fixed-grid backtest/Shadow, and their read-only workbench. These capabilities remain separate evidence modes; scenario forecasting and portfolio optimization are design/prototype only. New income sources are still added one at a time and must be proven before entering a real recommendation.

## Commands

Minimal CLI commands (implemented subset marked):

```text
sync-market latest --instrument BTC-USDT-SWAP --limit 100 --request-key <key>   # 已实现: 单页手动同步
sync-market backfill --instrument BTC-USDT-SWAP --from <UTC> --to <UTC> --limit 400 --max-pages 30 --request-key <key>  # 已实现: 历史游标回补
sync-market stale-list --older-than PT15M                                       # 已实现: 只读发现超时 RUNNING(含回补)
sync-market stale-recover --request-key <key> --expected-run-id <id> --older-than PT15M  # 已实现: 人工确认恢复(按 key 前缀路由同步/回补)
snapshot-account  保存只读账户与持仓快照
backtest          回放指定 Income Source 和参数
evaluate          评估当前收益来源与 Portfolio
report            生成中文周报和实验报告
```

CLI exit codes: 0 success/replay/backfill-progress, 1 internal, 2 bad args (no execution created), 3 in-progress, 4 source failure (incl. CURSOR_STALLED), 5 recovery refused. The CLI must stay a thin adapter over `FundingRateSyncService`/`FundingRateBackfillService` and their stores: no mapper access, no outer `@Transactional`, and no DB transaction spanning the OKX HTTP call. Hardening policy (enforced in the service layer, rejected before any state change): stale `--older-than` must be PT1M–P30D with ≥1µs precision and exact microsecond conversion; `--request-key` is lowercased and restricted to `[a-z0-9._-]` (no control chars/whitespace/colons); `stale-recover` request keys must be printable ASCII; backfill windows must satisfy `from < to` at ≥1ms precision.

Build/test commands will follow standard Maven (`mvn verify`, `mvn test -Dtest=ClassName`); integration tests require Docker for Testcontainers MySQL, started via `docker compose up -d`.
