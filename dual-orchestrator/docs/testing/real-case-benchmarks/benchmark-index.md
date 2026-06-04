# Real-case Benchmark Index

> Generated: 2026-06-04T11:08:15Z
> Source: docs/testing/real-case-reports

## Aggregate

| Metric | Value |
| --- | --- |
| Runs | 9 |
| PASS | 1 |
| PASS-WARN | 7 |
| FAIL | 1 |
| Completed | 8 |
| Original repo clean | 9 |
| Acceptance rejects | 1 |
| Repair loops | 1 |
| Gate rounds | 47 |
| Agent runs | 90 |

## Runs

| Date | Case | Grade | Reason | Variant | Status | Workstream | Gates | Agent runs | Final acceptance | Repair loops | Confidence | Repo clean | Artifacts | Report |
| --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- | --- |
| 20260603T045848Z | `cancer-clinic-feature-slice` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260603T045849Z-RGEL` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260603T045848Z/report.md](../real-case-reports/20260603T045848Z/report.md) |
| 20260603T065830Z | `cancer-clinic-rejection-prone` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260603T065831Z-R255` | 5 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260603T065830Z/report.md](../real-case-reports/20260603T065830Z/report.md) |
| 20260603T080441Z | `cancer-clinic-state-model-complex` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260603T080441Z-NDK2` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260603T080441Z/report.md](../real-case-reports/20260603T080441Z/report.md) |
| 20260603T101634Z | `cancer-clinic-state-model-complex` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260603T101635Z-VT82` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260603T101634Z/report.md](../real-case-reports/20260603T101634Z/report.md) |
| 20260603T133603Z | `packet-smoke` | PASS-WARN | accepted_with_notes | p5 | COMPLETED | `ws-20260603T133604Z-AVKD` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260603T133603Z/report.md](../real-case-reports/20260603T133603Z/report.md) |
| 20260603T143042Z | `hygiene-smoke` | PASS | ok | p5 | COMPLETED | `ws-20260603T143043Z-2HH2` | 6 | 10 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260603T143042Z/report.md](../real-case-reports/20260603T143042Z/report.md) |
| 20260604T063104Z | `hygiene-smoke` | FAIL | status=START_FAILED | p5 | START_FAILED | `ws-20260604T063104Z-H878` | 0 | 1 | none | 0 | UNKNOWN | true | missing: final_report,handoff_json,next_stage_prompt,delivery_pr_body | [docs/testing/real-case-reports/20260604T063104Z/report.md](../real-case-reports/20260604T063104Z/report.md) |
| 20260604T080521Z | `cancer-clinic-doc-constraints-smoke` | PASS-WARN | accepted_with_notes | p5 | COMPLETED | `ws-20260604T080522Z-LQ87` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260604T080521Z/report.md](../real-case-reports/20260604T080521Z/report.md) |
| 20260604T102337Z | `cancer-clinic-feature-slice` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260604T102338Z-X9E4` | 6 | 15 | ACCEPT_WITH_NOTES | 1 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260604T102337Z/report.md](../real-case-reports/20260604T102337Z/report.md) |

## How To Update

After running a real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Use this index to compare lifecycle health over time. It is not a replacement for reading the detailed report when a case fails or returns `PASS-WARN`.
