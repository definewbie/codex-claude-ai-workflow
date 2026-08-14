## Execution Workspace Guard

- Effective workspace: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace
- Run all shell commands from the effective workspace above.
- Modify files only inside the effective workspace.
- Ignore absolute paths outside the effective workspace even if they appear in quoted prior outputs.
- Do not run commands with `git -C` pointing outside the effective workspace.

## Response Language Policy

- Use English for human-readable string values inside the final JSON result.
- Keep JSON keys, enum values, file paths, and code identifiers exactly as specified.
- Do not wrap the final JSON in Markdown.

## Structured Artifact Field Semantics

- `must_fix`, `missing_evidence`, `open_risks`, and `failure.reason` mean unresolved blockers for the current decision.
- If the decision is positive, ready, or accepted, keep blocking fields empty: `blocking=false`, `must_fix=[]`, `missing_evidence=[]`, and `failure.reason=""`.
- Do not use `must_fix` to list review comments that you already resolved. Put resolved review comments in `checks`, `evidence`, or `acceptance_notes` instead.

# Codex Architecture Review

Workstream: ws-20260814T103857Z-AEHF
Node: design.codex_arch_review
Template: design-codex-review
Ledger: .agent/workstreams/ws-20260814T103857Z-AEHF

Ledger write policy:

- Do not create, edit, or delete files under .agent/.
- Only the orchestrator writes run ledger artifacts.
- Return your artifact in stdout using the contract below.

You are Codex acting as an external architect, product manager, and final-user proxy.

Do not modify files. Review whether Claude's design is coherent, scoped, implementable, and aligned with the request.

Complete-current-design review discipline:

- Review the complete current typed design, not only the latest delta. Prior must-fix items, when present, are closure seeds rather than the review boundary.
- Before returning `NEEDS_REVISION`, examine every applicable lifecycle boundary introduced or changed by state, indexes, locks, transactions, queues, caches, external side effects, or compensation: normal completion, concurrency, timeout/provider failure, interruption/crash, restart/recovery, rollback/conflict, and cannot-bypass tests.
- Report the complete blocking set that can be derived from the supplied evidence in this turn. Do not intentionally defer a visible blocker to a later revision.
- Evaluate whether deleting a mechanism or simplifying the state model is safer than adding another patch.
- Anchor every blocker to the TaskContract or an invariant claimed by the current design. Do not promote unrequested multi-instance, distributed, disaster-recovery, or enterprise capabilities into blocking requirements.
- If evidence is insufficient, use `missing_evidence`; do not pretend that cumulative verdict memory or prior design text was provided.

## User Request

# Captcha Auth App Requirement

## Goal

Create a new Java web application that provides user registration and login by
mobile phone number or email address.

## Technology Baseline

- Java 21
- Spring Boot 3
- Maven
- Thymeleaf server-rendered pages
- PostgreSQL
- Flyway database migrations

## Registration

- A user chooses either a mobile phone number or an email address as the account identifier.
- Registration requires a one-time verification code delivered through the selected channel.
- The user sets a password only after the OTP is successfully verified.
- Mobile numbers and email addresses must be normalized and unique.
- OTPs must expire, be single-use, have bounded verification attempts, and have resend throttling.
- Email delivery must be configurable through SMTP.
- SMS delivery must use a provider interface with configuration isolated from domain logic.
- Automated tests may use deterministic fake delivery adapters, but production pages must never reveal the OTP.

## Login

- A registered user can log in with either the registered mobile number or email address plus password.
- Every login attempt must first pass a server-generated image Captcha.
- Captcha answers must expire, be single-use, and be bound to the current browser session.
- Passwords must be stored with a modern one-way password hash and never logged or persisted as plaintext.
- Successful login establishes an authenticated session; logout invalidates it.
- Invalid Captcha, unknown account, and invalid password must produce safe user-facing errors without exposing account existence unnecessarily.

## User Experience

- Provide polished, responsive registration and login pages.
- Registration supports switching between phone and email without mixing verification state.
- Show validation errors, OTP resend countdown/status, Captcha refresh, loading states, and successful completion.
- Forms must be keyboard accessible and have explicit labels.

## Security And Reliability

- Enable CSRF protection for state-changing form submissions.
- Validate and normalize all server inputs.
- Do not expose credentials, OTP values, password hashes, or provider secrets in responses or logs.
- Apply bounded rate limits to Captcha validation, OTP sending/verification, registration, and login attempts.
- Keep provider credentials in external configuration.

## Required Verification

- Unit tests for normalization, password hashing, Captcha lifecycle, OTP lifecycle, throttling, and authentication decisions.
- Integration tests for registration by email, registration by phone, successful login, invalid/expired Captcha, invalid/expired/reused OTP, duplicate account, invalid password, logout, and protected-page access.
- A reproducible PostgreSQL-backed test or documented local verification path.
- `mvn test` must pass.
- README must document setup, required environment variables, database startup, migrations, and development/test delivery adapters.

## Out Of Scope

- Social login or OAuth providers.
- Password reset and account recovery.
- Purchasing or provisioning a real SMS vendor account.
- Admin consoles, organization management, or multi-factor login after registration.
- Deployment infrastructure beyond local run instructions.



## Context Package

- .agent/workstreams/ws-20260814T103857Z-AEHF/context/node-context-design_codex_arch_review-replay.md

```text
# Context Package

- Workstream: ws-20260814T103857Z-AEHF
- Builder Node: context.design_codex_arch_review.replay
- Target Node: design.codex_arch_review
- Generated At: 2026-08-14T22:52:38.371047Z
- Profile: architecture_review
- Total: 4.3k / 5.0k tokens

## Context Governance

| Section | Level | Degraded | Original | Current |
|---|---|---|---|---|
| input_artifacts | full | no | 563 | 563 |
| review_evidence | full | no | 802 | 802 |
| human_feedback | full | no | 85 | 85 |
| iteration_history | full | no | 347 | 347 |
| recent_agent_outputs | full | no | 459 | 459 |
| ledger_pointers | pointer_only | yes | 140 | 153 |
| required_architecture_review_subject | full | no | 1.9k | 1.9k |

Degraded sections have full data at their original paths listed below.


## Context Governance

| Section | Level | Degraded | Original | Current |
|---|---|---|---|---|
| input_artifacts | full | no | 0 | 0 |
| review_evidence | full | no | 0 | 0 |
| human_feedback | full | no | 0 | 0 |
| iteration_history | full | no | 0 | 0 |
| recent_agent_outputs | full | no | 0 | 0 |
| ledger_pointers | full | no | 0 | 0 |

Degraded sections have full data at their original paths listed below.

## State & Evidence Boundary

This package separates deterministic harness facts from agent proposals.

### Trusted Harness Facts

- TaskContract: READY_FOR_REVIEW - Deliver a runnable Maven single-module Spring Boot 3 (Java 21) server-rendered web application that supports registration via phone or email with single-use expiring OTP verification followed by password setup, and login via normalized identifier plus password gated by a server-generated image captcha, with secure storage, throttling, and PostgreSQL/Flyway persistence. (/private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/artifacts/task-contract.json)
- Git Evidence: not generated yet

### Human Decisions

- Contract Resolution: APPROVE by human_gate (/private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/artifacts/contract-resolution.json)

### Agent Proposed States

- design.claude_design: decision=READY_FOR_ARCH_REVIEW, stageOutcome=READY, evidenceState={blocking=false, evidence_count=5, missing_evidence_count=0, must_fix_count=0, acceptance_note_count=3, open_risk_count=3} (/private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.contract.json)

### Pending Contract/Evidence Gaps

- No contract failure diagnostics recorded

## Repo Scope Guidance

This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.

- Stage: design
- Repo map: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/repo-map.json
- File relevance plan: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/file-relevance-plan-node-context-design_codex_arch_review-replay.json
- Stage scope policy: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/stage-scope-policy-node-context-design_codex_arch_review-replay.json
- Workstream memory: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/workstream-memory.json
- Working set: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/working-set.json
- File summaries: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/file-summaries.json
- Read reuse contract: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/read-reuse-contract.json
- Agent capability catalog: .agent/workstreams/ws-20260814T103857Z-AEHF/memory/agent-capability-catalog-node-context-design_codex_arch_review-replay.json
- Governance report: .agent/workstreams/ws-20260814T103857Z-AEHF/artifacts/context-governance/node-context-design_codex_arch_review-replay.json

- Recommended read files:
  - pom.xml
  - README.md
  - .gitignore
  - .env.example
  - docker-compose.yml
  - src/main/resources/application.yml
  - src/main/resources/application-test.yml
  - src/main/resources/db/migration/V1__create_auth_tables.sql
- Contract scope candidates:
  - pom.xml
  - README.md
  - .gitignore
  - .env.example
  - docker-compose.yml
  - src/main/resources/application.yml
  - src/main/resources/application-test.yml
  - src/main/resources/db/migration/V1__create_auth_tables.sql
  - src/main/resources/templates/register.html
  - src/main/resources/templates/login.html
  - src/main/resources/templates/index.html
  - src/main/resources/templates/error.html
- Allowed write files: (none planned)
- Forbidden scope:
  - .git/**
  - .agent/workstreams/**[outside-workspace path removed]
  - .agent/workstreams/**[outside-workspace path removed]
  - business source writes
- Already inspected files: (none planned)
- Read reuse rule: use the recommended read files first; no repeated-read risk is known yet.
Read recommendations are context only. Declared edits must match TaskContract.scope and are verified from Git evidence.

## Input Artifacts

### .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.stderr.log

(execution diagnostic omitted from context)

### .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.transcript.log

(execution transcript omitted from context; inspect raw file at /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.transcript.log)

### .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.json

- Contract: design.claude_design
- Decision: READY_FOR_ARCH_REVIEW
- Summary: Revised design enforces the one-verifiable-OTP invariant with a database partial unique index plus deterministic conflict handling, switches the LOGIN rate-limit key to a privacy-safe hash of the canonical identifier with a fixed invalid-input fallback bucket, and adds concurrent-resend and login-alias throttling tests.
- Open Risks: A losing concurrent resend receives the same user-facing message as a throttled resend by design, so legitimate double-clicks show the countdown rather than an error-specific to the race; accepted for safe-message uniformity.; The fixed invalid-identifier fallback bucket is shared per IP, so an attacker sending garbage identifiers can exhaust only that bucket; accepted as it protects rather than weakens account-scoped throttling.; Fixed-window boundary bursts and in-memory counter reset on restart remain accepted, documented limitations per prior reviews.
- Verification: Revised design enforces one verifiable OTP per identifier and channel via a partial unique index with deterministic unique-violation handling across creation, delivery, and activation, keys LOGIN throttling on a hashed canonical identifier with a fixed invalid-input fallback, and adds concurrent-resend and alias-sharing tests; ready for architecture review.
- Contract Sidecar: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.contract.json
- Raw Output: .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.json

## Review Evidence Brief

No cumulative review verdict recorded. Review the complete current subject against the contract baseline.

**Acceptance criteria:**
- Registration lets the user choose either a mobile phone number or an email address as the account identifier, request an OTP on that channel, and only after successful OTP verification set a password to create the account.
- Mobile numbers and email addresses are normalized (lowercased/trimmed emails; phone numbers normalized to a canonical E.164-style form using a configurable default country) and uniqueness is enforced both in application logic and a unique database constraint.
- OTPs expire after a configurable TTL, are single-use (a consumed or previously used OTP cannot verify again), have a bounded number of verification attempts per code, and resend requests are throttled with a visible countdown/status on the page.
- Email delivery goes through configurable SMTP settings and SMS delivery goes through a SmsProvider interface whose configuration is externalized, with domain logic independent of any concrete provider; automated tests use deterministic fake delivery adapters and production pages/responses never display the OTP value.
- Switching the registration form between phone and email mode does not mix verification state (channel switch resets or isolates OTP/verification state for the other channel).
- Login requires the registered mobile number or email plus password and a server-generated image captcha; the captcha answer expires, is single-use, and is bound to the current browser session, and the captcha image can be refreshed.
- Passwords are stored only as modern one-way hashes (BCrypt or Argon2 via Spring Security PasswordEncoder) and never appear in plaintext in storage, responses, or logs; successful login establishes an authenticated session and logout invalidates it.
- Invalid captcha, unknown account, and invalid password each produce safe generic user-facing errors that do not unnecessarily reveal account existence.
- CSRF protection is enabled for all state-changing form submissions (registration, OTP send/verify, login, logout), and all server inputs are validated and normalized before use.
- Bounded rate limits apply to captcha validation, OTP send, OTP verification, registration, and login attempts, returning safe errors when exceeded.
- Registration and login pages are polished, responsive, keyboard accessible with explicit labels, and show validation errors, OTP resend countdown/status, captcha refresh, loading states, and successful completion.
- Database schema is managed by Flyway migrations against PostgreSQL, and the application runs locally following only the README setup steps (environment variables, database startup, migrations).

Derive the complete current blocker set from the review subject and acceptance criteria; re-read a file only with a reason (edit/verify/sha-changed/stage-gap).
Evidence: memory .agent/workstreams/ws-20260814T103857Z-AEHF/memory/workstream-memory.json; diff .agent/workstreams/ws-20260814T103857Z-AEHF/artifacts/git-evidence/diff.patch; changed .agent/workstreams/ws-20260814T103857Z-AEHF/artifacts/git-evidence/changed-files.txt.

## Human Feedback

### gate.contract_review

- Action: approved
- Finished At: 2026-08-14T11:39:13.507174Z

```text
TaskContract 完整覆盖 captcha-auth 的业务范围、安全约束、测试要求与交付标准，同意进入设计阶段。
```

### gate.human_requirement_review

- Action: approved
- Finished At: 2026-08-14T11:19:54.835865Z

```text
需求、范围、非目标和验收标准符合 captcha-auth 产品要求，同意进入后续设计。
```

## Iteration History

### iter-005

- Parent: iter-004
- Trigger: NEEDS_REVISION
- Target Node: design.claude_design
- Created At: 2026-08-14T11:46:25.219266Z

```text
The prior findings are resolved, but concurrent resend can still create multiple ACTIVE OTPs and the login limit key permits normalization-alias bypass.
```

### iter-004

- Parent: iter-003
- Trigger: NEEDS_REVISION
- Target Node: design.claude_design
- Created At: 2026-08-14T11:44:36.731027Z

```text
The revision resolves most prior findings, but its OTP state predicates and rate-limit enforcement matrix remain internally inconsistent.
```

### iter-003

- Parent: iter-002
- Trigger: NEEDS_REVISION
- Target Node: design.claude_design
- Created At: 2026-08-14T11:42:35.095249Z

```text
The component design is coherent, but critical lifecycle invariants are modeled without enforceable concurrency, rate-limit, and delivery-failure boundaries.
```

### iter-002

- Parent: iter-001
- Trigger: NEEDS_REVISION
- Target Node: intake.claude_brainstorm
- Created At: 2026-08-14T10:41:06.342422Z

```text
The proposal is product-complete overall, but it omits the explicitly required integration test for an incorrect OTP.
```

### iter-001

- Parent: (none)
- Trigger: workflow_started
- Target Node: context.intake_claude_brainstorm
- Created At: 2026-08-14T10:38:57.610853Z

```text
Initial workflow iteration.
```

## Recent Agent Outputs

### /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.contract.json

- Contract: design.claude_design
- Decision: READY_FOR_ARCH_REVIEW
- Summary: Revised design enforces the one-verifiable-OTP invariant with a database partial unique index plus deterministic conflict handling, switches the LOGIN rate-limit key to a privacy-safe hash of the canonical identifier with a fixed invalid-input fallback bucket, and adds concurrent-resend and login-alias throttling tests.
- Open Risks: A losing concurrent resend receives the same user-facing message as a throttled resend by design, so legitimate double-clicks show the countdown rather than an error-specific to the race; accepted for safe-message uniformity.; The fixed invalid-identifier fallback bucket is shared per IP, so an attacker sending garbage identifiers can exhaust only that bucket; accepted as it protects rather than weakens account-scoped throttling.; Fixed-window boundary bursts and in-memory counter reset on restart remain accepted, documented limitations per prior reviews.
- Verification: Revised design enforces one verifiable OTP per identifier and channel via a partial unique index with deterministic unique-violation handling across creation, delivery, and activation, keys LOGIN throttling on a hashed canonical identifier with a fixed invalid-input fallback, and adds concurrent-resend and alias-sharing tests; ready for architecture review.
- Contract Sidecar: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.contract.json
- Raw Output: .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.json

## Ledger Pointers

- Events: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/events.jsonl
- Workflow current: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/workflow/current.json
- Node state directory: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-2026

[adaptively compressed; section degraded to stay within ~125 tokens; full source remains in ledger under /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF]

## Required Architecture Review Subject

- Contract: design.claude_design
- Decision: READY_FOR_ARCH_REVIEW
- Summary: Revised design enforces the one-verifiable-OTP invariant with a database partial unique index plus deterministic conflict handling, switches the LOGIN rate-limit key to a privacy-safe hash of the canonical identifier with a fixed invalid-input fallback bucket, and adds concurrent-resend and login-alias throttling tests.
**Checks:**
- Addressed every arch-review Must Fix: at most one current verifiable OTP per identifier and channel across the full PENDING→delivery→ACTIVE path (Must Fix 1), an explicit database mechanism with deterministic activation-conflict handling (Must Fix 2), a canonical hashed LOGIN key with a deterministic invalid-identifier fallback (Must Fix 3), a concurrent-resend test proving at most one ACTIVE verifiable code (Must Fix 4), and login-throttling alias tests proving case/format variants share one counter (Must Fix 5).
- Verified the database mechanism closes both holes the reviewer identified: the partial unique index covers PENDING and ACTIVE rows so two concurrent resends cannot both insert, and a PENDING→ACTIVE transition keeps the row inside the same partial index so activation can never produce a second current row; no identifier+channel can hold two PENDING rows at any instant.
- Verified the LOGIN key uses the same IdentifierNormalizer canonicalization boundary as authentication, so email case variants and equivalent phone representations collapse to one counter, with a fixed fallback bucket that neither leaks account existence nor allows syntactically invalid input to fan out across keys.
- Confirmed all previously accepted boundaries are retained unchanged: state-complete OTP predicates, atomic captcha compare-and-swap, persist-then-deliver compensation with timeout-after-delivery recovery via resend, the exactly-once action-to-guard matrix, normalization-before-guard ordering, swept fixed-window counters, and Docker-as-shared-prerequisite wording — all within authorized scope (the partial index lives in the already-authorized V1__create_auth_tables.sql).

**Evidence:**
- Concurrent-resend invariant (Must Fix 1 and 2): V1__create_auth_tables.sql adds `CREATE UNIQUE INDEX uq_otp_current ON otp_code (identifier, channel) WHERE state IN ('PENDING', 'ACTIVE')`, making the database the sole authority for 'at most one current verifiable OTP per normalized identifier and channel'. Because both PENDING and ACTIVE are inside the partial predicate, the invariant holds at every instant: a second concurrent resend INSERT of a PENDING row fails with a unique violation, and a later PENDING→ACTIVE activation keeps the row in the index so it cannot coexist with another current row. Deterministic conflict handling: OtpService.issue runs the invalidation statement from the prior revision, inserts the new PENDING row, and catches a unique-violation exception only for this index; the insert and invalidation run in one transaction (REQUIRES_NEW on issue), and the unique-violation path maps to the existing resend-throttle response ('a code was recently sent, wait for the countdown') — the losing request performs no delivery and leaves the winner's row untouched. Activation remains the conditional `UPDATE otp_code SET state='ACTIVE' WHERE id=:id AND state='PENDING'`; with the partial index, a generation race is impossible without any generation token, and the activation update's rowcount=0 fallback (treated as FAILED) still covers the cleanup horizon. The prior resent-invalidation statement is retained as defense-in-depth but the index, not the statement, is the authoritative mechanism.
- Canonical LOGIN key (Must Fix 3): CaptchaAuthenticationFilter builds the LOGIN limit key as `sha256(canonicalIdentifier)` where canonicalIdentifier comes from the same pure IdentifierNormalizer used by authentication (trim+lowercase for email, E.164 with configured default country for phone); the hex digest is combined with the client IP as before. Syntactically invalid identifiers (normalizer rejects) map to a single fixed fallback key bucket per IP (`invalid`), consumed before any authentication call, so malformed input cannot fan out across counters and the fallback neither confirms nor denies account existence. The canonical value is used only in memory for the counter and never logged or persisted.
- Concurrent-resend test (Must Fix 4): OtpServiceTest (Testcontainers PostgreSQL) issues N parallel resend operations for one identifier+channel and asserts at most one row exists in PENDING/ACTIVE afterward, at most one delivery was attempted by the fake adapter, exactly N-1 requests received the safe throttle response, and the single surviving code verifies successfully while a second verify attempt fails as consumed; a follow-up phase completes delivery for the surviving PENDING row and asserts it reaches ACTIVE and remains the only current row.
- Login-alias throttling tests (Must Fix 5): RateLimitServiceTest (with LoginFlowIT end-to-end confirmation) drives login attempts alternating email case variants ('User@Example.com', 'user@example.com', ' USER@Example.COM ') and equivalent phone formats ('+12125550123', '(212) 555-0123', '2125550123') against a lowered threshold and asserts all variants consume one shared LOGIN counter, the limit trips after the configured count regardless of representation, and the response is the generic 429 page with no account-existence signal; an additional case asserts repeated syntactically invalid identifiers consume the fixed invalid bucket without starving canonical keys.
- Retained boundaries from prior accepted revisions: state-complete mutating predicates requiring state='ACTIVE', consumed=false, unexpired, and attempts>0 where applicable; synchronized session compare-and-swap captcha consumption with constant-time hashed comparison; persist-then-deliver with PENDING→ACTIVE on confirmed delivery and PENDING→FAILED on timeout/failure; the exactly-once action matrix (CAPTCHA_VALIDATE in CaptchaService.validate, LOGIN in CaptchaAuthenticationFilter, OTP_SEND/OTP_VERIFY in RegistrationController handlers, REGISTRATION in RegistrationService.createAccount); fixed normalization-before-guard ordering; swept fixed-window counters; and the full baseline stack (BCrypt, uniform login errors, CSRF, Thymeleaf with inline CSS/JS, Flyway, Testcontainers, README/.env.example documenting the new behavior).

- Open Risks: A losing concurrent resend receives the same user-facing message as a throttled resend by design, so legitimate double-clicks show the countdown rather than an error-specific to the race; accepted for safe-message uniformity.; The fixed invalid-identifier fallback bucket is shared per IP, so an attacker sending garbage identifiers can exhaust only that bucket; accepted as it protects rather than weakens account-scoped throttling.; Fixed-window boundary bursts and in-memory counter reset on restart remain accepted, documented limitations per prior reviews.
- Verification: Revised design enforces one verifiable OTP per identifier and channel via a partial unique index with deterministic unique-violation handling across creation, delivery, and activation, keys LOGIN throttling on a hashed canonical identifier with a fixed invalid-input fallback, and adds concurrent-resend and alias-sharing tests; ready for architecture review.
- Contract Sidecar: /private/tmp/design-review-replay-44e7230.JjjuD9/workspace/.agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.contract.json
- Raw Output: .agent/workstreams/ws-20260814T103857Z-AEHF/runs/run-1786707985252/outputs/design-claude.json
```

Evidence use rule:

- If the package contains `Review Evidence Brief`, review that first.
- Use raw artifacts or repository reads only to spot-check high-risk claims or fill explicit evidence gaps.
- If the brief is insufficient, put concrete gaps in `missing_evidence` instead of broadening the review silently.

## Output Contract

Return exactly one JSON object as the final assistant message.
Do not use Markdown fences, XML tags, headings, commentary, or text before/after the JSON.
Preserve the exact key names and JSON primitive types shown below. Replace example values with factual values.
Every JSON object is closed. Do not add keys that are not declared for that object.
Allowed `result.decision` values: [BLOCKED, NEEDS_REVISION, READY_FOR_ARCH_REVIEW]
For this node, `result.reread_reasons` must remain exactly `[]`.

For this node, `result.contract_obligations` must remain exactly `[]`; contract obligations are produced only by intake contract review.

{
  "schema_version" : 1,
  "workstream_id" : "ws-20260814T103857Z-AEHF",
  "run_id" : "replay-design-review-44e7230",
  "node_id" : "design.codex_arch_review",
  "result" : {
    "artifact_type" : "agent_output_contract",
    "decision" : "<one allowed decision>",
    "summary" : "one short sentence",
    "lifecycle_status" : "READY",
    "blocking" : false,
    "checks" : [ "check or review performed" ],
    "evidence" : [ "specific evidence" ],
    "missing_evidence" : [ ],
    "must_fix" : [ ],
    "contract_obligations" : [ ],
    "acceptance_notes" : [ ],
    "open_risks" : [ ],
    "changed_files" : [ ],
    "verification_summary" : "short verification summary",
    "failure" : {
      "category_hint" : "",
      "verification_remediation" : "NONE",
      "contract_gaps" : [ ],
      "out_of_scope_files" : [ ],
      "missing_tests" : [ ],
      "context_gaps" : [ ],
      "reason" : ""
    },
    "reread_reasons" : [ ]
  }
}
