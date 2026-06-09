# Design Evolution Notes

This note is not the current architecture spec. It records the design path and
tradeoffs behind dual-orchestrator so the story can later be turned into an
article.

## Starting Point

The first pain was mundane: a human sat between Claude Code and Codex, copying
requirements, summaries, review feedback, test notes, and handoff prompts back
and forth.

The first instinct was to automate the copy/paste loop:

```text
Claude writes -> Codex reviews -> Claude fixes -> Codex accepts
```

That was useful, but too small. Once the loop became longer, the real problem
was not message passing. The real problem was lifecycle control:

- What is the agreed task?
- Which Agent is allowed to change code?
- What evidence proves self-test happened?
- What does the verifier actually reject?
- Where does the process resume after quota, context overflow, or model drift?
- How does a human review without reading every raw log?

The project moved from "dual Agent runner" to "engineering harness."

## First Lesson: Worktree And Ledger Come Before Intelligence

The Bash prototype proved two boring things mattered most:

1. run in an isolated Git worktree by default;
2. write every prompt, output, event, diff, and result into a ledger.

Without these two, Agent automation is hard to trust. A good model can still
change the wrong file. A good review can still be impossible to replay. The
ledger became the system memory, not an afterthought.

## Second Lesson: Reviewer And Developer Need Different Jobs

Claude is treated as the developer/architect. Codex is treated as the external
reviewer/verifier. This separation is less about model brand and more about
responsibility:

- the developer can modify code;
- the verifier should challenge scope, evidence, architecture, and user impact;
- the human keeps ownership at gates.

The useful shape is not "two Agents debate forever." It is a bounded lifecycle
where one role builds and another role verifies against the same contract.

## Third Lesson: Natural Language Is Good For People, Bad For Routing

An early design tried to compile TaskContract from Markdown. It seemed cheap,
but real runs exposed the flaw: Markdown contains examples, copied context,
quoted reviews, and old summaries. A parser can easily mistake those for current
scope.

The fix was not "write a better regex." The fix was to move machine facts into
typed artifacts:

```text
Markdown report for humans
typed artifact for the orchestrator
```

That single boundary changed the project direction. The model still reasons in
language, but the harness routes on structured state and validated evidence.

## Fourth Lesson: Human Gates Are Product Features

At first, human gates looked like friction. Later they became the shape of the
product.

For long tasks, the human should not watch every Agent call. But the human does
need a clear pause when:

- requirements are still ambiguous;
- a contract is about to become canonical;
- a plan is about to turn into code;
- acceptance has notes or rejection;
- the next stage should be started.

The review packet became the key artifact. It lets the UI and CLI ask a better
question: "Is there enough evidence to approve this gate?"

## Fifth Lesson: Failure Needs A Vocabulary

Verifier rejection is not one thing. It can mean:

- code is wrong;
- task contract is incomplete;
- context was missing;
- the verifier overreached;
- tooling failed;
- evidence was too weak.

Treating all of those as "Claude, try again" leads to loops. The failure arbiter
introduced a vocabulary so the workflow can route failures instead of blindly
retrying them.

## Sixth Lesson: Calibration Should Be Conservative

It is tempting to let the system learn from every failure automatically. That is
dangerous too early.

The current approach is conservative:

1. classify failure;
2. generate a calibration candidate only for reusable patterns;
3. ask a human to accept, reject, or defer;
4. generate an apply plan;
5. promote only narrow project-level principles.

This keeps the knowledge loop real without turning one noisy run into permanent
global behavior.

## Seventh Lesson: UI Should Follow Read Models

The operator panel became attractive once real runs got complex. But a UI that
parses raw ledger files directly would become another source of confusion.

The better path was:

```text
ledger -> timeline / metrics / review packet / recovery summary -> CLI and UI
```

That is why `dualctl trace` and the operator panel now answer the same question:
where the workstream is, why it stopped, and what to do next.

## Current Shape

The current design can be summarized as:

```text
safe workspace
  + workflow state machine
  + typed artifacts
  + context packages
  + verifier acceptance
  + failure routing
  + human gates
  + ledger read models
```

The project is still pre-1.0, but the main direction is clear: do not make the
model smaller; make the surrounding harness more trustworthy.

## Article Angle

A good article should not lead with "we built a dual Agent tool." That sounds
like tooling.

The stronger angle is:

> AI coding does not only need smarter models. It needs an engineering harness
> that turns model output into auditable, reviewable, recoverable software work.

The human story is also important: the goal is not removing engineers from the
loop. The goal is removing copy/paste supervision while keeping human judgment
at the right gates.
