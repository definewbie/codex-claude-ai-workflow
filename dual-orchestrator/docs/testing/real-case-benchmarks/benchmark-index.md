# Real-case Benchmark Index

> Public cleanup baseline: 2026-06-08
> Source: local ignored `docs/testing/real-case-reports/<timestamp>/summary.json`

This file is intentionally sanitized for the public branch. It should only list
public or otherwise shareable benchmark runs.

## Latest Public Runs

| Date | Case | Grade | Reason | Variant | Status | Final acceptance | Confidence | Artifacts |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 20260608T033049Z | `public-issue-flow-review-state` | PASS-WARN | accepted_with_notes | full | COMPLETED | ACCEPT_WITH_NOTES | MEDIUM | ok |

## How To Update

After running a public real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Before committing the generated index, check it does not include private repo
names, absolute local paths, raw prompts, or internal project details.

`PASS-WARN` means the lifecycle completed but there are attention items to read,
usually from Codex `ACCEPT_WITH_NOTES` or medium/low verification confidence.
