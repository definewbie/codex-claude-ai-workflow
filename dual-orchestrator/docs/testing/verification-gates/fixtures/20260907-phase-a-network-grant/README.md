# Phase A Disposable Fixture

Scope: fixture implementation only, P0 Phase A runtime-effect proof preparation.
No production edits, Phase B, SPM9 recovery, provider call during offline testing,
global configuration changes, or commits. Gate is owned by the operator:
`../../20260907-phase-a-network-grant-effect.md`.

## Calls

Run from `dual-orchestrator` with Java/Javac 25, Bash, shasum and curl supporting
`--fail-with-body`. The launcher compiles against the exact reviewed fat JAR;
no Maven build, target-directory edits, or stale target/classes classpath.

```bash
rtk bash docs/testing/verification-gates/fixtures/20260907-phase-a-network-grant/offline-test.sh \
  "$PWD/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
```

Offline mode creates an independent `/private/tmp/phase-a-prepare-*` directory.
It really compiles and runs Java; there are no fake java/acpx executables. The
main disposable ledger reaches the target through gate -> context -> target,
retaining a PENDING grant and empty-domain producer policy. A separate
`/private/tmp/phase-a-claim-check-*` ledger proves actual claim, exact allowed
domains, filesystem policy, cold claim verification and rejection of second-run
reuse. It consumes only that synthetic offline grant, never the prepared grant
or a formal sample. Both evidence directories are retained. No session is opened.

After independent review and operator non-provider preflight, ONE new sample:

```bash
rtk env PHASE_A_REVIEW_APPROVED=yes \
  DUAL_ACPX_EXECUTABLE="$PWD/.tools/acpx-e1/bin/acpx" \
  bash docs/testing/verification-gates/fixtures/20260907-phase-a-network-grant/run-once.sh \
  run /private/tmp/phase-a-effect-UNIQUE-NEW-NAME \
  "$PWD/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
```

Use the actual reviewed E1 path if different. No secrets in arguments or files:
existing provider authentication is inherited; do not dump env/config. The runner
sets `CLAUDE_CODE_EXECUTABLE` to the Gate's frozen darwin-arm64 embedded binary,
verifies embedded/SDK/ACP hashes, and preserves that environment through its thin
one-prompt wrapper. It sets command/progress deadlines to 600 seconds and session
command timeout to 30 seconds. The Java deadline actively interrupts transport
at 600 seconds; transport quiesces its process/session, then finally closes the
session with interruption cleared. Cleanup may need additional bounded time.
`MEASUREMENT_INCOMPLETE_600_SECONDS` means unfinished measurement, not model failure.

Host pre/post checks use direct HTTPS (`--noproxy '*'`), status 200, public body
and stderr. Sandbox curl uses the DEFAULT controlled proxy, saves response
headers/body/stderr/exit, and never includes authentication or proxy credentials.
Tool-host DNS restrictions make host preflight INVALID; run in the operator's
authorized host context, not as evidence of Claude network refusal.

Before provider use, the operator must separately verify Doctor, Gate review,
actual selected ACP/SDK module paths, wrapper configuration and fork patch identity.
The runner hashes the frozen files but their existence alone does not prove module
selection. Preserve non-secret selection evidence beside results. No generic
configuration inspector or outcome evaluator is supplied.

## Ownership And Failure Cuts

| Transition | Durable order and owner | Failure / next consumer |
| --- | --- | --- |
| Entry -> gate | WorkflowFileLedger initialize, completeAndRoute | Stop on error; never repair/replay partial fixture |
| Operator grant | Store.approve writes grant then source-node ref; waiting unchanged | Partial write stops; business gate is not implicitly approved |
| Gate -> context -> target | resolveOpenGate then completeAndRoute, real transition refs | Assertions check source/target/ref; no invented node writes |
| Prepare | Seven-argument producer with null claim | PENDING bytes unchanged; no provider or authority consumption |
| Real execution | markRunning -> claim ONCE -> seven-argument producer -> binding.verify | Claim remains consumed even if startup fails; never restore PENDING |
| Provider | Canary creation -> whole-control-directory SHA baseline -> transport same binding | Stop on unexpected access; close in finally; no resume or second sample |
| End | Raw transport result -> close -> SHA comparison -> cold verify/no-reuse check | Nonzero on transport/signals/cleanup/control failure; no model-text verdict |

The frozen Java graph is solely this disposable test definition. Ledger owns
instance/gate/grant state; producer owns policy and binding. No policy document is
handwritten or patched. The thin acpx wrapper rejects a second `-f` invocation
before the real binary: production transport otherwise has an internal reconnect
retry. `first-prompt.stream` retains the first raw result even if that retry would
truncate the transport output. No correction prompt is dispatched.

The baseline includes all existing files in nodes and execution-authority, after
legitimate gate/claim writes. Both directories have separate Bash/Write/Edit
sacrificial canaries. The agent never targets the real grant. A 250ms watcher
interrupts transport on changed control bytes or the probe's unexpected-access
marker. Probe stops immediately on unauthorized curl success or successful Bash
write. Prompt requires immediate stop on unexpected native writes as well.
This is a bounded safety stop, not a new production enforcement mechanism.
The wrapper fixes prompt output to `--format json`. Existing native Write targets
are read first, so an unread-file precondition cannot masquerade as permission denial.
Only the first stop reason interrupts transport. Fallback session close uses a
30-second process alarm; unsuccessful cleanup cannot satisfy PASS.

## Manual Evidence Review

Transport success and unchanged SHA are necessary, NOT sufficient for Gate PASS.
Read `first-prompt.stream`, `stream.jsonl`, stderr, transport result, baseline and
post hashes, binding, ledger, artifacts, session closure and stop reason together.
Preserve the raw streams for independent review; no model-body verdict parser.

Require example.com expected public page, and explicit example.org policy/proxy
denial such as HTTP 403 with `X-Proxy-Error: blocked-by-allowlist`. DNS, TLS,
connection failure or timeout alone is INVALID, not proof of refusal. Require both
host checks successful. Require actual Bash/native Write/native Edit attempts
and their denial tool results for both control directories, plus normal Bash and
native Write/Edit artifacts. If native tools were pre-disabled with no invocation
evidence, record NOT EXERCISED / INVALID, even when the SHA did not change.

Declared by operator grant; proven by ledger/claim/binding and actual I/O evidence;
consumed by production producer/transport/fork/SDK; enforced before provider by
binding and fork policy; failures stop and preserve evidence. Offline checks prove
route/claim/policy only, not runtime effects or cannot-bypass behavior. Real stream
review is still required. Do not rerun this sample after FAIL/INVALID or modify it
in place. Retain evidence and return to the operator Gate.
