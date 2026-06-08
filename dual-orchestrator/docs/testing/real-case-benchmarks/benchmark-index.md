# Real-case Benchmark Index

> Generated: 2026-06-08T03:41:53Z
> Source: docs/testing/real-case-reports

## Aggregate

| Metric | Value |
| --- | --- |
| Runs | 19 |
| PASS | 5 |
| PASS-WARN | 11 |
| FAIL | 3 |
| Completed | 16 |
| Original repo clean | 19 |
| Acceptance rejects | 1 |
| Repair loops | 1 |
| Gate rounds | 100 |
| Agent runs | 185 |

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
| 20260605T090949Z | `cancer-clinic-small-ui` | PASS-WARN | accepted_with_notes | p6 | COMPLETED | `ws-20260605T090950Z-2HEU` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260605T090949Z/report.md](../real-case-reports/20260605T090949Z/report.md) |
| 20260605T144312Z | `dual-cancer-clinic-evidence-contract-case` | FAIL | status=WAITING_FOR_RECOVERY | full | WAITING_FOR_RECOVERY | `ws-20260605T144313Z-GLWX` | 4 | 7 | none | 0 | UNKNOWN | true | missing: final_report,handoff_json,next_stage_prompt,delivery_pr_body | [docs/testing/real-case-reports/20260605T144312Z/report.md](../real-case-reports/20260605T144312Z/report.md) |
| 20260606T024307Z | `dual-real-case-medium` | PASS | ok | full | COMPLETED | `ws-20260606T024308Z-YE24` | 6 | 12 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260606T024307Z/report.md](../real-case-reports/20260606T024307Z/report.md) |
| 20260606T051014Z | `dual-real-case-contract-boundary` | PASS-WARN | accepted_with_notes | full | COMPLETED | `ws-20260606T051015Z-AUQX` | 6 | 12 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260606T051014Z/report.md](../real-case-reports/20260606T051014Z/report.md) |
| 20260606T074728Z | `public-issue-flow-review-state` | FAIL | status=WAITING_FOR_RECOVERY | full | WAITING_FOR_RECOVERY | `ws-20260606T074728Z-42WC` | 1 | 2 | none | 0 | UNKNOWN | true | missing: final_report,handoff_json,next_stage_prompt,delivery_pr_body | [docs/testing/real-case-reports/20260606T074728Z/report.md](../real-case-reports/20260606T074728Z/report.md) |
| 20260606T075054Z | `public-issue-flow-review-state` | PASS-WARN | accepted_with_notes | full | COMPLETED | `ws-20260606T075055Z-7YRE` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260606T075054Z/report.md](../real-case-reports/20260606T075054Z/report.md) |
| 20260606T082257Z | `public-issue-flow-review-state` | PASS | ok | full | COMPLETED | `ws-20260606T082258Z-MRD7` | 6 | 10 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260606T082257Z/report.md](../real-case-reports/20260606T082257Z/report.md) |
| 20260606T085603Z | `public-issue-flow-review-state` | PASS | ok | full | COMPLETED | `ws-20260606T085604Z-ZBNA` | 6 | 10 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260606T085603Z/report.md](../real-case-reports/20260606T085603Z/report.md) |
| 20260606T091013Z | `public-issue-flow-review-state` | PASS | ok | full | COMPLETED | `ws-20260606T091014Z-SPA5` | 6 | 10 | ACCEPT | 0 | HIGH | true | ok | [docs/testing/real-case-reports/20260606T091013Z/report.md](../real-case-reports/20260606T091013Z/report.md) |
| 20260608T033049Z | `public-issue-flow-review-state` | PASS-WARN | accepted_with_notes | full | COMPLETED | `ws-20260608T033050Z-ETSK` | 6 | 10 | ACCEPT_WITH_NOTES | 0 | MEDIUM | true | ok | [docs/testing/real-case-reports/20260608T033049Z/report.md](../real-case-reports/20260608T033049Z/report.md) |

## How To Update

After running a real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Use this index to compare lifecycle health over time. It is not a replacement for reading the detailed report when a case fails or returns `PASS-WARN`.
