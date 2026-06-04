# Real-case Benchmark Index

> Generated: 2026-06-04T04:52:22Z
> Source: docs/testing/real-case-reports

## Aggregate

| Metric | Value |
| --- | --- |
| Runs | 6 |
| Completed | 6 |
| Original repo clean | 6 |
| Acceptance rejects | 0 |
| Gate rounds | 35 |
| Agent runs | 64 |

## Runs

| Date | Variant | Status | Workstream | Gates | Agent runs | Acceptance decisions | Rejects | Repo clean | Artifacts | Report |
| --- | --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- |
| 20260603T045848Z | p6 | COMPLETED | `ws-20260603T045849Z-RGEL` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | true | ok | [docs/testing/real-case-reports/20260603T045848Z/report.md](../real-case-reports/20260603T045848Z/report.md) |
| 20260603T065830Z | p6 | COMPLETED | `ws-20260603T065831Z-R255` | 5 | 10 | ACCEPT_WITH_NOTES | 0 | true | ok | [docs/testing/real-case-reports/20260603T065830Z/report.md](../real-case-reports/20260603T065830Z/report.md) |
| 20260603T080441Z | p6 | COMPLETED | `ws-20260603T080441Z-NDK2` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | true | ok | [docs/testing/real-case-reports/20260603T080441Z/report.md](../real-case-reports/20260603T080441Z/report.md) |
| 20260603T101634Z | p6 | COMPLETED | `ws-20260603T101635Z-VT82` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | true | ok | [docs/testing/real-case-reports/20260603T101634Z/report.md](../real-case-reports/20260603T101634Z/report.md) |
| 20260603T133603Z | p5 | COMPLETED | `ws-20260603T133604Z-AVKD` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | true | ok | [docs/testing/real-case-reports/20260603T133603Z/report.md](../real-case-reports/20260603T133603Z/report.md) |
| 20260603T143042Z | p5 | COMPLETED | `ws-20260603T143043Z-2HH2` | 6 | 10 | ACCEPT | 0 | true | ok | [docs/testing/real-case-reports/20260603T143042Z/report.md](../real-case-reports/20260603T143042Z/report.md) |

## How To Update

After running a real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Use this index to compare lifecycle health over time. It is not a replacement for reading the detailed report when a case fails or returns `PASS-WARN`.
