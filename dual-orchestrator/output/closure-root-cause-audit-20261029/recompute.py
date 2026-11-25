"""Read-only evidence census. Writes derived JSON only beside this script.

No model calls; no state mutations; no inference that COMPLETED means delivered.
Run from repository root with: rtk proxy python3 <this file>
"""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib
import json
import subprocess

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]


def read(p):
    return json.loads(p.read_text())


def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def discover():
    registry = read(Path.home() / '.dual/operator-workspaces.json')
    roots = []
    missing = []
    for project in registry['projects']:
        base = Path(project['agentRunsDir'])
        found = list(base.glob('*/.agent/workstreams/*/workflow/current.json'))
        roots += [(p.parent.parent, project['name']) for p in found]
        if not found:
            missing.append({'project': project['name'], 'registered': len(project.get('workstreams', []))})
    for p in (REPO / 'dual-orchestrator/output').glob('w09*/**/workflow/current.json'):
        if list((p.parent.parent / 'runs').glob('*/run.json')):
            roots.append((p.parent.parent, 'W09'))
    return roots, missing


def main():
    roots, missing = discover()
    cases = defaultdict(list)
    manifest = {}
    for root, cohort in roots:
        p = root / 'workflow/current.json'
        state = read(p)
        cases[state['workstreamId']].append((root, cohort, state))
    result, runs, conflicts, event_rows = [], [], [], []
    for wid, copies in sorted(cases.items()):
        root, cohort, state = max(copies, key=lambda x: (x[2].get('updatedAt', ''), x[2].get('commitSequence', 0), len(list((x[0]/'runs').glob('*/run.json')))))
        node_results = {}
        for p in (root / 'nodes').glob('*.json'):
            n = read(p)
            manifest[str(p)] = digest(p)
            rr = n.get('result') or {}
            if rr.get('run_id'):
                node_results[rr['run_id']] = {'nodeStatus': n.get('status'), 'parseError': rr.get('parse_error'), 'nodePath': str(p)}
        byrun = defaultdict(list)
        for r, _, _ in copies:
            manifest[str(r/'workflow/current.json')] = digest(r/'workflow/current.json')
            for p in (r/'runs').glob('*/run.json'):
                d = read(p)
                manifest[str(p)] = digest(p)
                byrun[d.get('runId', p.parent.name)].append((p, d))
        case_runs = []
        for rid, variants in sorted(byrun.items()):
            p, d = max(variants, key=lambda x: (x[1].get('finishedAt') or '', len(x[0].read_bytes())))
            usage_variants = {json.dumps(x[1].get('usage'), sort_keys=True) for x in variants}
            if len(usage_variants) > 1:
                conflicts.append({'workstreamId': wid, 'runId': rid, 'kind': 'usage_snapshot_conflict', 'paths': [str(x[0]) for x in variants]})
            u = d.get('usage') or {}
            outcome = d.get('executionOutcome') or {}
            row = {'workstreamId': wid, 'cohort': cohort, 'runId': rid, 'path': str(p), 'node': d.get('nodeId'), 'stage': d.get('stage'), 'iteration': d.get('semanticIteration'), 'agent': d.get('agent'), 'status': d.get('status'), 'exitCode': d.get('exitCode'), 'startedAt': d.get('startedAt'), 'durationMs': d.get('durationMs'), 'outcome': outcome, 'usage': u, 'contract': d.get('agentResultContract'), 'artifactContracts': d.get('artifactContractResults'), 'occurrenceCount': len((d.get('agentResultAcquisition') or {}).get('occurrences', [])), **node_results.get(rid, {})}
            row['measured'] = u.get('measurementType') == 'PROVIDER_RUN_USAGE' and not u.get('cumulative', False)
            # Retain declared total without assuming input includes/excludes cache.
            row['usageSumDelta'] = u.get('totalTokens', 0) - sum(u.get(k, 0) or 0 for k in ['inputTokens', 'outputTokens', 'cachedReadTokens', 'cachedWriteTokens'])
            runs.append(row)
            case_runs.append(row)
            manifest[str(p)] = digest(p)
        ep = root / 'events.jsonl'
        events = [json.loads(l) for l in ep.read_text().splitlines() if l.strip()] if ep.exists() else []
        if ep.exists():
            manifest[str(ep)] = digest(ep)
        manifest[str(root/'workflow/current.json')] = digest(root/'workflow/current.json')
        seen = set()
        for line, e in enumerate(events, 1):
            eid = e.get('event_id')
            if eid in seen:
                conflicts.append({'workstreamId': wid, 'kind': 'duplicate_event_id', 'eventId': eid})
            seen.add(eid)
            if e.get('event_type') in ['node_failed', 'iteration_started', 'workflow_completed', 'delivery_committed', 'delivery_application_observed', 'recovery_resolved']:
                event_rows.append({'workstreamId': wid, 'path': str(ep), 'line': line, **e})
        total = sum((r['usage'].get('totalTokens') or 0) for r in case_runs if r['measured'])
        result.append({'workstreamId': wid, 'cohort': cohort, 'root': str(root), 'copies': len(copies), 'state': state, 'runCount': len(case_runs), 'measuredRuns': sum(r['measured'] for r in case_runs), 'measuredTokens': total, 'lastNode': case_runs[-1]['node'] if case_runs else None, 'outcomes': dict(Counter(r['outcome'].get('primaryReason', 'MISSING') for r in case_runs)), 'eventTypes': dict(Counter(e.get('event_type') for e in events)), 'deliveryCommitted': any(e.get('event_type') == 'delivery_committed' for e in events), 'nodeFailures': [e for e in events if e.get('event_type') == 'node_failed'], 'workflowCompletedEvent': any(e.get('event_type') == 'workflow_completed' for e in events)})
    report = {'cases': result, 'runs': runs, 'events': event_rows, 'conflicts': conflicts, 'missingRegistryProjects': missing}
    (HERE/'census.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    (HERE/'source-sha256.json').write_text(json.dumps(manifest, indent=2)+'\n')
    def tokens(rows, key='totalTokens'):
        return sum(r['usage'].get(key, 0) or 0 for r in rows if r['measured'])
    economics = {}
    for cohort in ['W09', 'okx-btc-k-line-demo']:
        rows = [r for r in runs if r['cohort'] == cohort]
        economics[cohort] = {
            'tokens': {k: tokens(rows, k) for k in ['inputTokens', 'outputTokens', 'cachedReadTokens', 'totalTokens']},
            'byNode': {n: {'runs': sum(r['node'] == n for r in rows), 'tokens': tokens([r for r in rows if r['node'] == n])} for n in sorted({r['node'] for r in rows})},
            'failureMeasuredTokens': tokens([r for r in rows if r['outcome'].get('nodeMaySucceed') is False]),
            'unpricedIdentityTokens': tokens([r for r in rows if not r['usage'].get('providerId') or not r['usage'].get('billingMode')]),
            'reach': {s: len({r['workstreamId'] for r in rows if r['node'].startswith(s+'.')}) for s in ['intake', 'design', 'plan', 'implement', 'acceptance']},
        }
    rows = [r for r in runs if r['cohort'] == 'W09' and not r['workstreamId'].endswith('7SNA')]
    economics['newline_campaign'] = {
        'cases': len({r['workstreamId'] for r in rows}), 'runs': len(rows),
        'measuredRuns': sum(r['measured'] for r in rows),
        'recordedTokenCostPerObservedDelivery': tokens(rows),
        'successCaseTokens': tokens([r for r in rows if r['workstreamId'].endswith('9YYF')]),
        'extraReadinessPlanAndReviewTokens': tokens([r for r in rows if r['runId'] in ['run-1792724160451', 'run-1792724491616']]),
        'productDesignRepairAndReviewTokens': tokens([r for r in rows if r['runId'] in ['run-1792718732583', 'run-1792718961414']]),
    }
    economics['series_illustration'] = {str(p): p**37 for p in [.99, .98]}
    (HERE/'economics.json').write_text(json.dumps(economics, ensure_ascii=False, indent=2)+'\n')
    history = {}
    for file in ['AGENTS.md', 'dual-orchestrator/docs/engineering-checklist.md']:
        commits = subprocess.check_output(['rtk', 'proxy', 'git', 'log', '--format=%H', '--', file], cwd=REPO, text=True).splitlines()
        rows = []
        for commit in reversed(commits):
            data = subprocess.check_output(['rtk', 'proxy', 'git', 'show', commit+':'+file], cwd=REPO)
            date = subprocess.check_output(['rtk', 'proxy', 'git', 'show', '-s', '--format=%aI', commit], cwd=REPO, text=True).strip()
            rows.append({'commit': commit, 'date': date, 'bytes': len(data), 'lines': len(data.splitlines()), 'sha256': hashlib.sha256(data).hexdigest()})
        data = (REPO/file).read_bytes()
        rows.append({'commit': 'WORKTREE_UNCOMMITTED', 'bytes': len(data), 'lines': len(data.splitlines()), 'sha256': hashlib.sha256(data).hexdigest()})
        history[file] = rows
    (HERE/'discipline-history.json').write_text(json.dumps(history, indent=2)+'\n')
    for cohort in sorted({c['cohort'] for c in result}):
        cs = [c for c in result if c['cohort'] == cohort]
        print(cohort, json.dumps({'cases':len(cs),'runs':sum(c['runCount'] for c in cs),'measuredRuns':sum(c['measuredRuns'] for c in cs),'measuredTokens':sum(c['measuredTokens'] for c in cs),'states':dict(Counter(c['state']['status'] for c in cs)),'deliveryCommitted':sum(c['deliveryCommitted'] for c in cs)},ensure_ascii=False))
    print('conflicts',len(conflicts),'sources',len(manifest),'missing_registry_projects',len(missing))


if __name__ == '__main__':
    main()
