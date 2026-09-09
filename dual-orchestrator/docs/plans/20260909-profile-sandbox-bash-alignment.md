# Profile / Sandbox Bash Alignment

## Goal And Scope

P0 execution boundary fix for 6KVE: a non-execute profile must not acquire Bash
through sandbox auto-approval. No model layer, workflow, ledger, network-grant or
product changes. The stopped real case remains evidence; no resume in this change.

Owner: existing AcpxPermissionProfile. Its execute kind AND exact Bash approval,
with neither denyAll nor approveAll, determine sandbox Bash auto-approval.
Local binding remains a separate existing capability. The no-profile write overload
now conservatively disables Bash auto-approval (source compatibility retained).

| Profile | Auto-allow sandbox Bash | Managed deny Bash |
| --- | --- | --- |
| contract_discovery / observe / review | false | yes |
| review + WebSearch | false | yes |
| contract_correction used to create a policy | false | yes |
| implement | true | no |

The existing correction turn reuses the original occurrence policy and session.
Therefore an implement correction inherits implement sandbox authority; its ACP
denyAll is NOT a new SDK deny policy. This change does not claim correction-turn
tool isolation or change that session lifetime. Read-only corrections inherit the
new Bash deny and cannot widen it. Codex and unbound legacy sessions are unchanged.

## Transition And Failure Slices

| Before / trigger | Ordered actions | Consumer / failure |
| --- | --- | --- |
| named profile selected, fresh run | derive bool; atomic policy publication; bind SHA | transport verifies before acpx commands |
| valid binding, new session | fork checks SHA/keys/types; creates SDK options + managed deny | SDK owns actual tool enforcement |
| false auto-allow | project false and bare Bash deny together | read-only built-in shell shortcuts must not reopen Bash |
| true auto-allow | retain implement behavior and existing filesystem/network restrictions | normal build/self-test allowed |
| absent/tampered/invalid policy | existing rejection before session/prompt | no fallback to true |
| crash before/after publication | no accepted partial policy; existing binding verification | a new run gets new policy, old bound sessions are not migrated |
| correction/rebuild/cold load | same occurrence ref/SHA, existing replacement guard | cannot replace a bound policy under the old identity |

## Invariant And Verification

- Declared by: profile execute + exact Bash capability, not node/model names.
- Proven by: durable policy bool and ref/SHA; fork strict boolean validation.
- Consumed by: one fork loader projects SDK sandbox and managed permissions.
- Enforced before: SDK tool execution; transport validates policy before launch.
- Failure route: existing typed bootstrap/execution failure; no workflow rerouting.
- Cannot-bypass tests: full profile matrix including WebSearch; false/true fork
  projection; malformed boolean; actual AcpClient.createSession metadata capture;
  existing SHA/lineage/session guard tests. New Java and fork tests failed first.

Official permission contract checked 2026-09-09:
https://code.claude.com/docs/en/permissions
Bare Bash deny is a tool restriction; allowed-tools is not a visibility whitelist.
Disabling sandbox auto-allow alone is insufficient because read-only shell commands
also have built-in approvals. This documentation is not a pinned-runtime replay.

## Status

Implementation and main-agent read-only self-review complete (not independent
review). Production behavior changes are limited to profile, policy producer and
fork loader; build identity updates are mechanical. No model names or business
paths participate in the new decision.

- Red before fix: Java 2 failures / 5 tests; fork 2 new tests failed.
- Java focused: 98/0/0/0.
- Fork full suite: 991 passed, zero failed/skipped. Initial restricted-tool run
  hit local socket EPERM and was stopped; complete rerun with local sockets
  permitted passed, without changing tests or calling a provider.
- Final stamped fork focused: 69 passed; build succeeded.
- Full reactor: BUILD SUCCESS, 171 fresh XML files, 1426/0/0/0,
  2026-09-09 10:44:53 through 10:50:19 +08:00. Package succeeded afterward.
- Patch reverse-apply (excluding generated stamp), six-way identity, shell syntax
  and git diff --check passed. New patch SHA:
  `4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e`.

Logs: `/private/tmp/dual-profile-bash-reactor.log`,
`/private/tmp/dual-profile-bash-fork-test-unrestricted.log`,
`/private/tmp/dual-profile-bash-stamped-focused.log`.

Runtime rejection with the pinned embedded binary is NOT PROVEN by option-capture
tests. No provider turn, additional agent, or real-case recovery ran. The next
verification should check actual Read success + Bash rejection under the new
bound read-only profile, not rerun the stopped product sample to infer denial.

Reflection: the previous tests proved transport flags and sandbox behavior
separately, missing their conflicting composition. The regression now checks
profile -> durable policy -> actual session metadata for both deny and execute,
including the managed deny that disabling auto-approval alone would omit.
