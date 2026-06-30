# Verification Gate: cancer-clinic GLM provider-cache-aware

- Target: prove the current GLM-5.2 real-case run preserves provider/model attribution and does not enter provider-cache-aware measure_only because provider/model is unknown.
- Source of truth: per-run `run.json` fields `providerModelIdentity`, `usage.providerId`, `usage.model`, `usage.billingMode`, `sessionCompactionMode`, `sessionCompactionEnabled`, `sessionCompactionReason`, and `executionOutcome`.
- Trigger precondition: Claude Code is configured through the Claude settings env as BigModel/Zhipu GLM-5.2; `DUAL_SESSION_COMPACTION_EXPERIMENT=provider_cache_aware`; the CLI jar was rebuilt after the latest adapter changes.
- How precondition is checked before run: read `~/.claude/settings.json` env without printing secrets; `stat dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar`; run must not use `--skip-build` unless the jar timestamp is explicitly checked.
- PASS: Claude run metadata records provider `zhipu`, model `glm-5.2`, billing mode `subscription_token_only`; provider-cache-aware reason does not contain `provider/model pricing unavailable`; reconnect signals block success; typed execution outcome exists; no more than one semantic repair loop; final quality is not degraded.
- FAIL: provider/model is unknown after a Claude run, provider-cache-aware reason contains `provider/model pricing unavailable`, reconnect is treated as success, typed outcome is missing, a single agent run stalls unexpectedly, or semantic repair loop count exceeds 1.
- INVALID: old jar/server/worker is used, `sessionCompactionMode`/provider identity fields are missing, GLM is not the configured Claude model, or the run cannot prove the target because it stops before any Claude run metadata is written.
- Stop-loss: stop if GLM provider tokens exceed roughly 3M before acceptance, if more than one semantic repair loop appears, if a single agent run is unexpectedly long, if `WAITING_FOR_RECOVERY` occurs, or as soon as the target fields show INVALID.
- Run artifact freshness: `mvn -pl dual-cli -am package` must pass and jar timestamp must be later than the relevant code changes before starting.
