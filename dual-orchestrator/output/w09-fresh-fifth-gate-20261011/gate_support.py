"""One-case operator observer; never writes authoritative ledger facts."""
import hashlib
import json
import re
import tomllib
from pathlib import Path

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
RELEASE = ROOT / 'output/w09-current-transcript-forward-fix-20261011/release-identity.json'
sha = lambda p: hashlib.sha256(Path(p).read_bytes()).hexdigest()

def read(p):
    return json.loads(Path(p).read_text())

def write(p, value):
    Path(p).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')

def same_config_with_fixture_registration(text, baseline_sha, target):
    """Only a redundant trust entry for the authorized fresh fixture may differ.

    Every other byte must equal the original; an unknown or stronger trust entry
    is rejected. This is observer identity, never a native permission grant.
    """
    if hashlib.sha256(text.encode()).hexdigest() == baseline_sha:
        return True
    header = '[projects."' + str(target) + '"]'
    if text.count(header) != 1:
        return False
    start = text.index(header)
    match = re.search(r'\n\[', text[start + len(header):])
    end = start + len(header) + match.start() + 1 if match else len(text)
    original = text[:start] + text[end:]
    if hashlib.sha256(original.encode()).hexdigest() != baseline_sha:
        return False
    old = tomllib.loads(original).get('projects', {})
    now = tomllib.loads(text).get('projects', {})
    ancestors = [p for p in target.parents if str(p) in old]
    return bool(ancestors and now.get(str(target)) == old[str(ancestors[0])]
                and now[str(target)] == {'trust_level': 'trusted'})

def frozen_check():
    d = read(RELEASE)
    assert all(sha(ROOT / p) == h for p, h in d['source_inputs'].items()), 'Frozen source drift'
    assert all(sha(j['path']) == j['sha256'] for j in d['application_jars'].values()), 'JAR drift'
    a = d['selected_acpx']
    assert all(sha(a[n]) == a[n + '_sha256'] for n in ['wrapper', 'dist']), 'acpx drift'
    for c in d['metadata_config_source_files'].values():
        assert Path(c['path']).exists() == c['exists'] and (not c['exists'] or sha(c['path']) == c['sha256']), 'Config drift'
    if (OUT / 'launch-identity.json').exists():
        i = read(OUT / 'launch-identity.json')
        for p, h in i['config_and_installed_sources'].items():
            if sha(p) == h:
                continue
            loc = read(OUT / 'instance-location.json')
            assert p == str(Path.home() / '.codex/config.toml') and same_config_with_fixture_registration(
                Path(p).read_text(), h, Path(loc['target'])), 'Selected client/settings drift'
        assert all(sha(OUT / p) == h for p, h in i['observer_inputs'].items()), 'Observer/Gate drift'
    return d

def measure(workspace, ledger, sessions=Path.home() / '.acpx/sessions'):
    rows, keys, transcripts = [], set(), set()
    for rp in sorted((ledger / 'runs').glob('*/run.json')):
        r = read(rp)
        assert r.get('finishedAt'), 'Unfinished prior run, no dispatch'
        u = r.get('usage') or {}
        assert u.get('measurementType') == 'PROVIDER_RUN_USAGE' and u.get('cumulative') is False, 'Incomplete run usage'
        occurrences = (r.get('agentResultAcquisition') or {}).get('occurrences', [])
        assert occurrences and len(occurrences) <= 2, 'Missing acquisition or correction bound exceeded'
        completions = list((rp.parent / 'outputs').glob('*-completion.json'))
        assert len(completions) == 1, 'Missing or ambiguous fixed completion'
        completion = read(completions[0])
        observed = completion.get('run_observations') or {}
        assert observed.get('runId') == r['runId'] and observed.get('usage') == u and observed.get('agentResultAcquisition') == r.get('agentResultAcquisition'), 'Run differs from fixed completion'
        items = []
        for o in occurrences:
            fixed = o.get('usage') or {}
            assert fixed.get('measurementType') == 'PROVIDER_RUN_USAGE' and fixed.get('attribution') == 'EXACT_REQUEST_MATCH' and fixed.get('cumulative') is False, 'Unattributable occurrence'
            text = (workspace / o['stderrPath']).read_text()
            match = re.search(r'\(([0-9a-f-]{36})\)', text)
            assert match, 'Missing native record identity'
            record = match.group(1)
            meta = read(sessions / (record + '.json'))
            assert meta['name'] == o['sessionName'] and Path(meta['cwd']).resolve() == workspace.resolve(), 'Wrong native session'
            sid, req = meta['acp_session_id'], str(o['requestId'])
            key = (r['runId'], o['occurrence'])
            assert key not in keys, 'Duplicate occurrence attribution'
            keys.add(key)
            transcript = (workspace / o['transcriptPath']).resolve()
            assert transcript not in transcripts, 'Transcript reused by another occurrence'
            transcripts.add(transcript)
            for ref in [o['transcriptPath'], o['stderrPath']]:
                assert completion.get('output_hashes', {}).get(ref) == sha(workspace / ref), 'Fixed invocation log drift'
            # The execution-owned stdout is scoped to this invocation. Shared
            # streams legitimately reuse request IDs after reinitialization.
            process = o.get('processResult') or {}
            assert process.get('stdoutPath') == o['transcriptPath'] and process.get('stderrPath') == o['stderrPath'], 'Wrong invocation logs'
            data = [json.loads(line) for line in transcript.read_text().splitlines() if line.lstrip().startswith('{')]
            prompts = [x for x in data if x.get('method') == 'session/prompt']
            assert all(str(x.get('id')) == req and x.get('params', {}).get('sessionId') == sid for x in prompts), 'Wrong current prompt'
            assert not any(str(x.get('id')) == req and 'error' in x for x in data), 'Current prompt error'
            results = [x for x in data if isinstance(x.get('result'), dict)
                       and (str(x.get('id')) == req or 'stopReason' in x['result'] or 'usage' in x['result'])]
            source = transcript
            if not results:
                # Same existing delayed-delivery exit as the production reader;
                # strict uniqueness only, never latest/first/last disambiguation.
                source = sessions / (record + '.stream.ndjson')
                history = [json.loads(line) for line in source.read_text().splitlines() if line.strip()]
                requests = [x for x in history if x.get('method') == 'session/prompt'
                            and str(x.get('id')) == req and x.get('params', {}).get('sessionId') == sid]
                results = [x for x in history if str(x.get('id')) == req
                           and isinstance(x.get('result'), dict) and 'stopReason' in x['result']]
                assert len(requests) == 1, 'Ambiguous delayed request'
            assert len(results) == 1, 'Missing or ambiguous current result' 
            result = results[0]
            assert str(result.get('id')) == req and result['result'].get('stopReason') == o['stopReason'], 'Wrong current completion'
            assert not result['result'].get('sessionId') or result['result']['sessionId'] == sid, 'Wrong current session'
            native = result['result'].get('usage') or {}
            for k in ['inputTokens', 'outputTokens', 'cachedReadTokens', 'cachedWriteTokens', 'totalTokens']:
                assert type(fixed.get(k)) is int and fixed[k] >= 0, 'Partial measured tokens'
                value = native.get(k, 0) if k.startswith('cached') else native.get(k)
                assert type(value) is int and value >= 0, 'Partial native tokens'
                assert value == fixed[k], 'Native/fixed tokens differ'
            items.append({'session_id': sid, 'record_id': record, 'request_id': req,
                          'occurrence': o['occurrence'], 'fixed_usage': fixed,
                          'native_usage': native, 'transcript_ref': str(transcript),
                          'transcript_sha256': sha(transcript),
                          'usage_source_ref': str(source), 'usage_source_sha256': sha(source)})
        for k in ['inputTokens', 'outputTokens', 'cachedReadTokens', 'cachedWriteTokens', 'totalTokens']:
            assert type(u.get(k)) is int and sum(x['fixed_usage'][k] for x in items) == u[k], 'Partial run aggregate' 
        rows.append({'run_id': r['runId'], 'node_id': r['nodeId'], 'agent': r['agent'],
                     'outcome': r.get('executionOutcome'), 'usage': u, 'occurrences': items})
    return {'completed_runs': rows, 'actual_tokens': sum(r['usage']['totalTokens'] for r in rows),
            'completed_calls': len(keys), 'measurement_complete': True,
            'cost_note': 'SDK amounts retained; not actual subscription payment. No invented mixed-provider price.'}

def planning(expected, measured, active):
    budget = read(OUT / 'budget.json')
    stages = [s['node'] for s in budget['stages']]
    assert expected in stages, 'Unknown model stage'
    seen = {r['node_id'] for r in measured['completed_runs']}
    pos = stages.index(expected)
    # A repeated earlier stage may invalidate the already-completed tail. Keep
    # that entire tail reserved; do not invent cheap skip paths.
    remaining = [n for n in stages if n not in seen]
    extra = [n for n in stages[pos:] if n in seen]
    return {'actual_tokens': measured['actual_tokens'], 'completed_calls': measured['completed_calls'],
            'active_seconds': active, 'remaining_mainline': remaining, 'new_extra_calls': extra,
            'measurement_complete': True, 'external_pending': False}
