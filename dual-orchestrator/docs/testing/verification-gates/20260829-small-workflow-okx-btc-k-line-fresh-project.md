# Verification Gate: Fresh OKX BTC Minute K-Line Web Application

Date: 2026-08-29  
Status: `USER AUTHORIZED / NON-PROVIDER PREFLIGHT READY / PROVIDER NOT STARTED`

## Verification Gate

- Target: Use exactly one fresh external small-workflow workstream to implement `REQUIREMENT.md` in the clean,
  intentionally unimplemented `okx-btc-k-line-demo` repository. The same natural run must complete a useful Java 25 /
  Spring Boot 4.1.0 / MyBatis-Plus 3.5.17 / SQLite product and prove the D1 accepted-delivery and D2
  completion/delivery projection boundaries end to end.
- Source of truth: Frozen Dual/workflow/target/requirement/workspace-check identities; persisted workflow definition,
  current, nodes, events, gates, runs, typed sidecars/references, completion evidence resolution and audit; final human
  acceptance GateDocument; immutable `DeliveryCandidateDocument` and referenced patch/tree identities;
  `DeliveryApplicationService.View`; target Git index/commit/tree; deterministic Maven tests; local HTTP contract fixtures;
  SQLite schema/data; backend API responses; opt-in direct-OKX live smoke. Markdown, console text, model claims, legacy
  `delivery.json`, raw merge-plan interpretation, mtime/latest scans and browser presentation are not lifecycle authority.
- Trigger precondition: Dual production baseline contains `fdd8a49b` and current docs baseline is `b27dc213`; the small
  workflow, target HEAD/tree, requirement and workspace-check hashes match Frozen Baseline; both repositories are clean;
  Java 25 and Maven are available; the target workspace check reaches the official public OKX candle endpoint and validates
  a BTC-USDT 1m response. Fresh package, external workflow validation, Claude/Codex minimal probes and execution-workspace
  capture/verify must pass. No previous workstream, worktree, session, output or target implementation may be reused.
- How precondition is checked before run: After user authorization, mechanical Gate validation and a docs-only clean commit,
  recompute all frozen identities, build a fresh Dual JAR, validate the external workflow definition, and run the target-owned workspace
  check from the frozen source checkout. Minimal probes use fresh named sessions and are closed. Create the workstream with
  fresh JAR `start` without `--run`; resolve the effective workspace only from persisted workstream/workspace documents;
  capture workspace evidence and verify it immediately before the first business run. Only typed
  `status=passed + verificationStatus=passed` permits provider execution.
- PASS: Boundary PASS and Product PASS must both hold.
  1. The workflow reaches `COMPLETED`; completion audit is ready with no gaps; terminal acceptance is `ACCEPT`, or
     `ACCEPT_WITH_NOTES` with `blocking=false`, empty `must_fix`, and empty `missing_evidence`. Contract, repository-fact
     binding, scope, lineage, self-test and acceptance artifacts remain valid through terminal completion.
  2. Before completion, CLI/API/UI delivery projection visibly reports `BLOCKED / WORKFLOW_NOT_DELIVERABLE`, preserves the
     reason, and exposes no merge/finalize command. No legacy/raw fallback may change that result.
  3. The final human acceptance gate is bound to exactly one canonical delivery-candidate ref. Candidate ref/SHA, base
     commit/tree, accepted/excluded paths, patch SHA and expected result tree are independently recomputable after approval.
     Post-approval source mutation must be rejected rather than silently recaptured.
  4. At workflow completion, CLI status/summary, server status/overview, console and management UI all project canonical
     workflow `COMPLETED` and delivery `READY` from workflow current plus `DeliveryApplicationService.View`; command exposure
     is `canMerge=true`, `canFinalize=false`. Final report and server completion projection resolve the same routed self-test
     and final goal evidence without runs-directory or mtime inference.
  5. Operator applies the approved candidate through the shared delivery service, inspects the staged tree, creates one
     manual commit, and finalizes it. Applied tree and final commit tree must equal candidate `expectedResultTree`; parent must
     equal candidate base commit; final view is `COMMITTED` with the exact commit SHA across CLI/API/UI. No direct Git or
     legacy delivery command may substitute for this chain.
  6. The product uses Java 25, Spring Boot 4.1.0 and
     `com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17`; SQLite is the only application database; no JPA/JdbcTemplate
     repository path or runtime CDN is introduced.
  7. The product retrieves official public `BTC-USDT` spot `1m` candles, initially backfills up to 300 rows, schedules a
     configurable 15-second poll, validates the full envelope, and transactionally upserts by
     `(instrument, bar, open_time)`. Newest-first, repeated, stale and incomplete-candle responses cannot create duplicates,
     delete newer data or corrupt a completed candle.
  8. Deterministic tests use a local contract-compatible HTTP stub and temporary SQLite database only to reproduce normal
     and failure responses, and pass without public OKX access. This stub is test-only and must not replace the production
     client or live verification. `mvn test` passes on Java 25. The opt-in live smoke directly performs one read-only
     official OKX request and validates at least one structural candle without credentials.
  9. The first screen is a usable responsive candlestick/volume view with local chart assets, backend-only OKX access,
     10-second browser refresh, latest close/completed time/sync health, and honest loading/empty/degraded/error states.
     Desktop and mobile browser verification show a nonblank chart with no overlap; backend or OKX failure preserves the
     last stored chart rather than presenting simulated data.
- Profile-efficiency sub-result: Exactly 8 Agent runs/provider occurrences with zero correction and zero semantic revision
  may separately mark the small profile efficiency claim `PROVEN`. Any legitimate typed review or human revision does not
  fail the main Gate, but the efficiency sub-result remains `NOT PROVEN`; do not suppress a valid blocker to preserve a call
  count.
- FAIL: With all frozen/preflight conditions valid, any typed contract/scope/lineage/self-test/acceptance/completion failure
  that is not repaired through the workflow; provider/adapter fatal; workflow not completed; missing/ambiguous delivery
  candidate; post-approval byte drift accepted; apply/commit/finalize tree mismatch; projection disagreement or legacy/raw
  fallback affecting status/action; required technology replaced; public-page scraping; nondeterministic tests requiring
  OKX; duplicate/lost/corrupt candles; overlapping scheduler writes; unsafe unbounded retry; browser calling OKX directly;
  unusable or blank chart; or requirement/test failure.
- INVALID: Any frozen identity mismatch, dirty source baseline, Gate not mechanically complete or not committed, stale
  JAR/server, workflow validation failure, Java/Maven unavailable, source or execution workspace preflight not PASS, official
  OKX preflight/live-smoke endpoint unavailable for environmental reasons, minimal probe failure, reused workstream/session,
  autorun, `--skip-build`, runtime modification of Gate/requirement/workflow/Dual, provider/model/timeout drift, manual ledger
  edit, or second sample creation. INVALID is not partial success and must not be repaired by changing the sample in place.
- Stop-loss: Do not start business Agents when a precondition fails. After start, stop and close the active process/session on
  repository-fact sentinel failure, provider fatal, session closure failure, the same review stage returning the same blocker
  twice without new evidence, a second correction without a valid contract, unauthorized network-permission escalation, or
  120 minutes of active runtime. `WAITING_FOR_HUMAN` time is excluded. Legitimate revisions with new evidence may proceed.
- Run artifact freshness: User authorization, mechanical Gate validation, docs-only Gate commit, fresh package, and recorded source commit/JAR/
  workflow/target/requirement/workspace-check hashes and build times. Preserve non-provider preflight, probes, workspace
  capture/verify, each human packet/decision, full ledger, immutable delivery candidate, merge/finalize evidence, target diff,
  commit tree, Maven/live-smoke/browser evidence, metrics and session closure for the single sample.

## Product Boundary

The target application's configured OKX HTTP client is an intentional product side effect. It is distinct from Agent Bash
network access. E1 total subprocess network authority remains an important but non-urgent security TODO and is not a PASS
condition for this Gate. If an Agent requests WebSearch, the existing exact one-execution human authority applies; WebFetch
or general Bash network approval is not implied.

## Frozen Baseline

```text
Dual delivery/projection baseline:
  b27dc213fdf443003c1ad3b79c7fecd9ab9de7d5
D1/D2-B production checkpoint:
  fdd8a49b
workflow:
  dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/
  agent-lifecycle.small-plan-human-review-experiment.json
workflow identity:
  agent-lifecycle-small-plan-human-review-experiment@1
workflow SHA-256:
  d41b835e4d8a8347b64984e085385030246976bbc485a866f215dfafc97faaec
workflow shape:
  28 nodes / 46 edges / 8 acpx.agent_run / 6 human.review
target:
  /Users/kriswang/work/projects/ai/okx-btc-k-line-demo
target HEAD:
  c84807946493a603c789ee40c6a4c615aa846c3b
target tree:
  81520ac179f17c35c7c358c2a02102007cbe7cba
target baseline:
  clean; exactly README.md, REQUIREMENT.md, .gitignore, scripts/verify-workspace.sh;
  no pom.xml and no src/ directory
requirement:
  REQUIREMENT.md
requirement SHA-256:
  9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a
workspace check:
  scripts/verify-workspace.sh
workspace check SHA-256:
  c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23
```

## Primary References

- Spring Boot 4.1.0 GA: https://spring.io/blog/2026/06/10/spring-boot-4/
- MyBatis-Plus Boot 4 starter 3.5.17: https://baomidou.com/en/getting-started/install/
- OKX public Market Data candlesticks: https://app.okx.com/docs-v5/en

The user has authorized this unique fresh real-case. The next action is to commit this Gate, run non-provider preflight and
the standing-authorized minimal probes, then create exactly one fresh workstream. No provider execution may begin before all
preconditions are mechanically PASS.
