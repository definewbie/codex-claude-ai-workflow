from pathlib import Path
import copy,hashlib,json

out=Path(__file__).resolve().parent
root=out.parents[2]
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
checks=[]
def checked(name,condition):
    assert condition,name
    checks.append(name)

index=json.loads((out/'source-index.json').read_text())
checked('all frozen source bytes unchanged',all(sha(root/p)==h for p,h in index.items()))
access=json.loads((out/'access-inventory.json').read_text())
checked('142 unique candidates, original 116 retained',len(access['entries'])==len({e['path'] for e in access['entries']})==142 and access['originalCandidates']==116)
checked('every candidate has disposition and identity',all(e['category'] and e['decision'] and index[e['path']]==e['sha256'] for e in access['entries']))
pred=json.loads((out/'precondition-map.json').read_text())
checked('all predicates bound to actual source',all(e['predicateSource'] in (root/e['path']).read_text() and e['ruleVersion']=='frozen-source-sha256:'+index[e['path']] for e in pred['checks']))

def references(value):
    if isinstance(value,dict):
        if 'ref' in value and 'sha256' in value:
            assert (out/value['ref']).is_file(),value['ref']
            assert sha(out/value['ref'])==value['sha256'],value['ref']
        for child in value.values():references(child)
    elif isinstance(value,list):
        for child in value:references(child)

for f in (out/'examples').glob('*.json'):references(json.loads(f.read_text()))
checks.append('all design fixture ref/SHA pairs resolve')
goal=json.loads((out/'examples/goal-contract.json').read_text())
execution=json.loads((out/'examples/execution-contract.json').read_text())
checked('GOAL explicitly has no write scope',goal['phase']=='GOAL' and goal['scope']['included']==[])
checked('EXECUTION retains goal clauses and exact file operations',execution['phase']=='EXECUTION' and execution['acceptance_criteria']==goal['acceptance_criteria'] and execution['test_requirements']==goal['test_requirements'] and {e['operation'] for e in execution['scope']['included']}=={'CREATE'})
checked('all required criteria have verification bindings',{e['criterion_id'] for e in execution['verification_bindings']}=={e['id'] for e in goal['acceptance_criteria']})
approval=json.loads((out/'examples/execution-approval.json').read_text())
checked('approval references contract without circular approval field',approval['subject']['sha256']==sha(out/'examples/execution-contract.json') and 'execution_approval' not in execution)
for stage in ['design','plan']:
    candidate=json.loads((out/f'examples/{stage}-decision-facts.json').read_text())['reviewed_candidate']
    review=json.loads((out/candidate['review_artifact']['ref']).read_text())
    checked(stage+' reviewed candidate binds exact author',review['review_subject']==candidate['author_artifact'] and candidate['review_decision']==review['decision']=='READY' and candidate['stage']==stage)

# Counterexamples exercise material checks only, not product validators.
bad=copy.deepcopy(approval);bad['subject']['sha256']='0'*64
try:references(bad)
except AssertionError:checks.append('tampered fixture hash rejected by material check')
else:raise AssertionError('hash counterexample')
operator=json.loads((out/'examples/operator-manifest.json').read_text())
checked('operator declaration not represented as program execution','declaredExecution' in operator and 'programExecution' not in operator)
for sample in json.loads((out/'real-producer-samples.json').read_text()):
    checked('actual producer sample '+sample['kind'],sha(Path(sample['ref']))==sample['sha256'])
storage=json.loads((out/'storage-probe.json').read_text())
checked('local storage primitive probe recorded successful exit',storage['exitCode']==0 and json.loads(storage['stdout'])['status']=='PASS_STORAGE_PRIMITIVES')
result={'status':'PASS_MATERIAL_CHECKS_ONLY','checks':checks,'count':len(checks),'productValidatorsExecuted':False,'providerCalls':0,'independentReview':False,'limitation':'No production schema/ledger/adapter implementation is validated by these checks.'}
(out/'material-checks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'status':result['status'],'count':len(checks)}))
