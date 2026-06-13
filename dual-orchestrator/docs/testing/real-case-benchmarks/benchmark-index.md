# Real-case Benchmark Index

> Generated: 2026-06-13T04:11:50Z
> Source: docs/testing/real-case-reports

## Aggregate

| Metric | Value |
| --- | --- |
| Runs | 1 |
| PASS | 1 |
| PASS-WARN | 0 |
| FAIL | 0 |
| Completed | 1 |
| Original repo clean | 1 |
| Acceptance rejects | 0 |
| Repair loops | 0 |
| Gate rounds | 6 |
| Agent runs | 10 |

## Runs

| Date | Case | Grade | Reason | Variant | Status | Workstream | Gates | Agent runs | Final acceptance | Repair loops | Confidence | Repo clean | Artifacts | Report |
| --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- | --- |
| 20260613T040013Z | `public-issue-flow-review-state` | PASS | ok | full | COMPLETED | `ws-20260613T040014Z-EXN7` | 6 | 10 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260613T040013Z/report.md](../real-case-reports/20260613T040013Z/report.md) |

## How To Update

After running a real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Use this index to compare lifecycle health over time. It is not a replacement for reading the detailed report when a case fails or returns `PASS-WARN`.
