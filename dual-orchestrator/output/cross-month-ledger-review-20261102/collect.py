"""Read-only cross-month inventory; workflow copies are never counted as new cases.

Run from repository root. Prior audit labels are not used as inputs.
Classification below describes recorded failure surfaces, not causal verdicts.
"""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib
import json
import re
import subprocess

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
SEARCH = Path('/Users/kriswang/work/projects/ai')
manifest = {}
errors = []


def load(path):
    if not path.exists():
        return {}
    data = path.read_bytes()
    manifest[str(path)] = hashlib.sha256(data).hexdigest()
    try:
        return json.loads(data)
    except (ValueError, UnicodeDecodeError) as exc:
        errors.append({'path': str(path), 'error': str(exc)})
        return {}


def line_of(path, needle):
    return next((i for i, s in enumerate(path.read_text().splitlines(), 1) if needle in s), None)


def surface(reason, status):
    if status == 'completed' and reason in ('', 'completed', 'completed_with_adapter_or_process_warnings'):
        return 'EXECUTION_ACCEPTED'
    if status == 'prepared':
        return 'PREPARED_ONLY'
    if reason in ('process_interrupted', 'process_timed_out', 'process_stalled', 'process_output_incomplete'):
        return 'EXECUTION_COMPLETION'
    if reason in ('session_bootstrap_failed', 'adapter_incompatible'):
        return 'BOOTSTRAP_COMPATIBILITY'
    if reason in ('rate_limited', 'agent_reconnect_required', 'provider_connection_failed', 'authentication_failed'):
        return 'PROVIDER_SESSION'
    if reason in ('agent_result_missing_or_invalid', 'terminal_control_missing_or_invalid'):
        return 'RESULT_PROTOCOL'
    if reason == 'required_artifact_contract_failed':
        return 'ARTIFACT_CONTRACT'
    if reason == 'node_input_incomplete':
        return 'INPUT_READINESS'
    if reason in ('permission_blocked', 'exploration_policy_violation'):
        return 'CAPABILITY_SCOPE'
    return 'LEGACY_OR_UNCLASSIFIED'


def main():
    registry = load(Path.home()/'.dual/operator-workspaces.json')
    missing = []
    paths = set()
    registered_bases = {}
    for p in registry['projects']:
        base = Path(p['agentRunsDir'])
        registered_bases[str(base)] = p['name']
        found = list(base.glob('*/.agent/workstreams/*/events.jsonl'))
        paths.update(found)
        if not found:
            missing.append({'project': p['name'], 'base': str(base), 'registeredWorkstreams': len(p.get('workstreams', []))})
    discovered = subprocess.run(['rtk', 'proxy', 'rg', '--files', '--hidden', '--no-ignore',
            '-g', 'events.jsonl', '-g', '!**/node_modules/**', '-g', '!**/target/**',
            '-g', '!**/.git/**', str(SEARCH)], capture_output=True, text=True, check=True)
    paths.update(Path(p) for p in discovered.stdout.splitlines())
    copies = defaultdict(list)
    streams = []
    for path in sorted(paths):
        data = path.read_bytes()
        manifest[str(path)] = hashlib.sha256(data).hexdigest()
        events = []
        for n, line in enumerate(data.splitlines(), 1):
            if not line.strip():
                continue
            try:
                e = json.loads(line)
                events.append({'line': n, 'event': e})
            except ValueError as exc:
                errors.append({'path': str(path), 'line': n, 'error': str(exc)})
        if not any(r['event'].get('event_type') == 'workflow_started' for r in events):
            streams.append({'path': str(path), 'kind': 'NON_WORKFLOW_EVENT_STREAM', 'rows': len(events)})
            continue
        root = path.parent
        state = load(root/'workflow/current.json')
        workstream = load(root/'workstream.json')
        wid = state.get('workstreamId') or workstream.get('workstreamId')
        if not wid:
            match = re.search(r'ws-\d{8}T\d{6}Z-[A-Z0-9]+', str(root))
            wid = match.group() if match else 'UNIDENTIFIED:'+str(root)
        cohort = next((name for base, name in registered_bases.items() if str(root).startswith(base+'/')), 'retained-project-output')
        copies[wid].append({'root': str(root), 'path': str(path), 'state': state, 'workstream': workstream,
                'cohort': cohort, 'events': events})
    # A capture without metadata can be identified only by an exact event prefix,
    # never by a report's label or a coincident timestamp.
    for wid in list(copies):
        if not wid.startswith('UNIDENTIFIED:'):
            continue
        variants = copies[wid]
        sequence = [r['event'] for r in variants[0]['events']]
        matches = [other for other, vs in copies.items() if other != wid and any(
                sequence == [r['event'] for r in v['events']][:len(sequence)] for v in vs)]
        if len(matches) == 1:
            copies[matches[0]].extend(copies.pop(wid))
    # Controlled specimens reuse names (e.g. ws-readiness). They are separate
    # experiments, not alternative histories of a unique real execution.
    for wid in list(copies):
        if re.fullmatch(r'ws-\d{8}T\d{6}Z-[A-Z0-9]+', wid) or wid.startswith('UNIDENTIFIED:'):
            continue
        for variant in copies.pop(wid):
            copies[wid+'@'+hashlib.sha256(variant['root'].encode()).hexdigest()[:10]] = [variant]
    cases, runs, observations, anomalies = [], [], [], []
    for wid, variants in sorted(copies.items()):
        # Prefer a longest event stream. Record rather than hide every non-prefix branch.
        chosen = max(variants, key=lambda v: (len(v['events']), v['state'].get('commitSequence', 0),
                '/.agent/workstreams/' in v['root']))
        seq = [r['event'] for r in chosen['events']]
        for v in variants:
            other = [r['event'] for r in v['events']]
            if other != seq[:len(other)]:
                anomalies.append({'workstream': wid, 'kind': 'DIVERGENT_EVENT_COPY', 'chosen': chosen['path'], 'other': v['path']})
        root = Path(chosen['root'])
        events = chosen['events']
        ids = Counter(r['event'].get('event_id') for r in events)
        for eid, count in ids.items():
            if eid is not None and count > 1:
                anomalies.append({'workstream': wid, 'kind': 'REPEATED_EVENT_ID', 'eventId': eid, 'count': count})
        byrun = defaultdict(list)
        for v in variants:
            for path in (Path(v['root'])/'runs').glob('*/run.json'):
                d = load(path)
                if d:
                    byrun[d.get('runId', path.parent.name)].append((path, d))
        case_runs = []
        for rid, versions in sorted(byrun.items()):
            own = [x for x in versions if x[0].is_relative_to(root)]
            path, d = max(own or versions, key=lambda x: (x[1].get('finishedAt') or '', x[0].stat().st_size))
            if len({manifest[str(p)] for p, _ in versions}) > 1:
                anomalies.append({'workstream': wid, 'run': rid, 'kind': 'RUN_COPY_DIFFERS', 'chosen': str(path), 'copies': [str(p) for p,_ in versions]})
            outcome = d.get('executionOutcome') or {}
            contract = d.get('agentResultContract') or d.get('terminalControlResult') or {}
            acq = d.get('agentResultAcquisition') or {}
            usage = d.get('usage') or {}
            reason = outcome.get('primaryReason') or ''
            row = {'workstream': wid, 'run': rid, 'cohort': chosen['cohort'], 'path': str(path),
                    'node': d.get('nodeId'), 'stage': d.get('stage'), 'start': d.get('startedAt'),
                    'finish': d.get('finishedAt'), 'durationMs': d.get('durationMs'), 'status': d.get('status'),
                    'reason': reason, 'surface': surface(reason, d.get('status')),
                    'failureReason': d.get('failureReason'), 'outcome': outcome, 'contract': contract,
                    'artifactContracts': d.get('artifactContractResults') or [], 'signals': d.get('adapterSignals') or [],
                    'process': d.get('processResult') or {}, 'usage': usage,
                    'occurrences': acq.get('occurrences') or [], 'correctionAttempted': acq.get('correctionAttempted'),
                    'acquisition': {k:v for k,v in acq.items() if k!='occurrences'},
                    'workdir': d.get('workdir'), 'outputPath': d.get('outputPath'),
                    'transcriptPath': d.get('transcriptPath'), 'stderrPath': d.get('stderrPath'),
                    'promptPath': d.get('promptPath'), 'sessionScope': d.get('sessionScope'),
                    'runtimeIdentityPresent': bool(d.get('frozenRuntimeIdentity')),
                    'sourceLine': line_of(path, '"primaryReason"') or line_of(path, '"failureReason"')}
            runs.append(row)
            case_runs.append(row)
        failed_nodes = []
        node_counts = Counter()
        for npath in (root/'nodes').glob('*.json'):
            n = load(npath)
            node_counts[n.get('status','UNKNOWN')] += 1
            if n.get('status') in ('FAILED','BLOCKED'):
                failed_nodes.append({'path':str(npath),'id':n.get('nodeInstanceId'),'definition':n.get('nodeDefinitionId'),
                        'status':n.get('status'),'result':n.get('result') or {},'inputRefs':n.get('inputRefs') or []})
        active = []
        for nid in chosen['state'].get('activeNodeInstanceIds', []):
            npath = root/'nodes'/(nid+'.json')
            n = load(npath)
            active.append({'path': str(npath), 'id': nid, 'definition': n.get('nodeDefinitionId'),
                    'status': n.get('status'), 'result': n.get('result') or {}})
        gate_paths = list((root/'gates').glob('*.json'))
        gates = [{'path':str(p), 'data':load(p)} for p in gate_paths]
        special = ('failed','retry','revision','iteration','human','gate','classified','completed','delivery','recovery')
        for r in events:
            e = r['event'];kind=e.get('event_type','')
            if any(t in kind for t in special):
                observations.append({'workstream':wid,'path':chosen['path'],'line':r['line'],**e})
        dates=[r['event'].get('ts','') for r in events if r['event'].get('ts')]
        state=chosen['state']; ws=chosen['workstream']
        case={'workstream':wid,'cohort':chosen['cohort'],'root':str(root),'eventPath':chosen['path'],
                'population':'OPERATIONAL_RETAINED' if case_runs or chosen['cohort']!='retained-project-output' else 'CONTROLLED_LEDGER_SPECIMEN',
                'copies':[{'root':v['root'],'rows':len(v['events'])} for v in variants],
                'title':ws.get('title'),'repository':ws.get('repository'),
                'status':state.get('status'),'definitionVersion':state.get('definitionVersion'),'workflowId':state.get('workflowId'),
                'frozenIdentity':ws.get('frozenRuntimeIdentity') or {}, 'start':min(dates) if dates else '',
                'last':max(dates) if dates else '', 'lastEvent':events[-1] if events else {},'active':active,
                'eventCounts':dict(Counter(r['event'].get('event_type') for r in events)),
                'runCount':len(case_runs),'runSurfaces':dict(Counter(r['surface'] for r in case_runs)),
                'gates':gates,'failedNodes':failed_nodes,'nodeCounts':dict(node_counts),'hasProviderRunMetadata':bool(case_runs)}
        cases.append(case)
    summary={'discoveredEventFiles':len(paths),'workflowEventCopies':sum(len(v) for v in copies.values()),
            'nonWorkflowStreams':len(streams),'uniqueWorkstreams':len(cases),'uniqueRuns':len(runs),
            'eventRowsInSelectedCopies':sum(sum(c['eventCounts'].values()) for c in cases),
            'start':min(c['start'] for c in cases),'last':max(c['last'] for c in cases),
            'states':dict(Counter(str(c['status']) for c in cases)),
            'runSurfaces':dict(Counter(r['surface'] for r in runs)),
            'runReasons':dict(Counter(r['reason'] or 'LEGACY_NO_TYPED_REASON' for r in runs)),
            'cohorts':{name:{'cases':len(cs),'runs':sum(c['runCount'] for c in cs),
                'states':dict(Counter(str(c['status']) for c in cs)),
                'definitionVersions':dict(Counter(str(c['definitionVersion']) for c in cs))}
                for name in sorted({c['cohort'] for c in cases}) if (cs:=[c for c in cases if c['cohort']==name])},
            'missingRegisteredProjects':len(missing),'parseErrors':len(errors),'anomalies':dict(Counter(a['kind'] for a in anomalies))}
    real = [c for c in cases if c['population']=='OPERATIONAL_RETAINED']
    summary['executionPopulation']={'cases':len(real),'runs':sum(c['runCount'] for c in real),
            'states':dict(Counter(c['status'] for c in real)), 'withoutRunMetadata':sum(not c['runCount'] for c in real),
            'eventRows':sum(sum(c['eventCounts'].values()) for c in real),
            'start':min(c['start'] for c in real),'last':max(c['last'] for c in real)}
    for filename,data in [('inventory.json',{'summary':summary,'cases':cases,'runs':runs,'observations':observations,
            'anomalies':anomalies,'excludedStreams':streams,'unavailableRegistry':missing,'parseErrors':errors}),('source-sha256.json',manifest)]:
        (HERE/filename).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(summary,ensure_ascii=False,indent=2))


if __name__=='__main__':
    main()
