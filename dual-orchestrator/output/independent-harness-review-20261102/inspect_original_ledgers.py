from pathlib import Path
import json, collections
HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
index = json.load(open(HERE.parent / 'cross-month-ledger-review-20261102/inventory.json'))
rows, errors = [], []
for c in index['cases']:
    p = Path(c['eventPath']); root = p.parent
    try:
        es = [json.loads(s) for s in p.read_text().splitlines() if s.strip()]
        ws = root / 'workstream.json'; w = json.load(open(ws)) if ws.exists() else {}
        df = root / 'workflow/definition.json'; de = json.load(open(df)) if df.exists() else {}
        runs = []
        for f in root.glob('runs/*/run.json'):
            r = json.load(open(f)); outcome = r.get('executionOutcome') or {}
            runs.append({'path':str(f), 'schemaVersion':r.get('schemaVersion'), 'status':r.get('status'), 'reason':outcome.get('primaryReason',r.get('failureReason'))})
        current = root / 'workflow/current.json'
        rows.append({'root':str(root), 'events':len(es), 'start':es[0].get('ts'), 'workflowId':de.get('workflowId'), 'definitionVersion':de.get('version'), 'frozenRuntimeIdentityPresent':bool(w.get('frozenRuntimeIdentity')), 'eventTypes':dict(collections.Counter(e.get('event_type') for e in es)), 'lastEventType':es[-1].get('event_type'), 'literalWorkflowCurrent':json.load(open(current)).get('status') if current.exists() else None, 'runs':runs})
    except Exception as e:
        errors.append({'path':str(p),'error':str(e)})
alltypes = collections.Counter()
for r in rows: alltypes.update(r['eventTypes'])
result = {'selection':'Paths taken from a prior inventory for discovery only. Original event/current/definition/run parsed afresh; ledger entries are not independent experimental trials.', 'ledgerEntries':len(rows), 'eventRows':sum(r['events'] for r in rows), 'runMetadataFiles':sum(len(r['runs']) for r in rows), 'months':dict(collections.Counter(str(r['start'])[:7] for r in rows)), 'identityPresent':dict(collections.Counter(r['frozenRuntimeIdentityPresent'] for r in rows)), 'runSchemaVersions':dict(collections.Counter(run['schemaVersion'] for row in rows for run in row['runs'])), 'deliveryEventTypes':{k:v for k,v in alltypes.items() if 'delivery' in k}, 'parseErrors':errors, 'rows':rows}
(HERE / 'raw-census.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print({k:v for k,v in result.items() if k != 'rows'})
