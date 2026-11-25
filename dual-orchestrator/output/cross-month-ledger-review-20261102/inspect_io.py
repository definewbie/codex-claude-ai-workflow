"""Inspect raw files for all failed run records; flag observations, not diagnoses."""
from pathlib import Path
from collections import Counter
import hashlib
import json

HERE=Path(__file__).resolve().parent


def main():
    inventory=json.loads((HERE/'inventory.json').read_text())
    manifest=json.loads((HERE/'source-sha256.json').read_text())
    cases={c['workstream']:c for c in inventory['cases']}
    inspections=[]
    for row in inventory['runs']:
        if row['status']!='failed':
            continue
        run=Path(row['path']).parent
        workdir=Path(row['workdir'] or '')
        sources={}
        references={}
        for field in ['promptPath','outputPath','stderrPath','transcriptPath']:
            if not row[field]:
                continue
            ref=Path(row[field])
            candidates=[ref] if ref.is_absolute() else [workdir/ref,run/'outputs'/ref.name,run/'prompts'/ref.name]
            sources[field]=next((p for p in candidates if p.is_file()),None)
            references[field]={'reference':row[field],'searched':[str(p) for p in candidates]}
        for i, occurrence in enumerate(row['occurrences'],1):
            for field in ['promptPath','outputPath','stderrPath','transcriptPath']:
                if not occurrence.get(field):
                    continue
                ref=Path(occurrence[field]); candidates=[ref] if ref.is_absolute() else [workdir/ref,run/'outputs'/ref.name,run/'prompts'/ref.name]
                sources[f'occurrence-{i}-{field}']=next((p for p in candidates if p.is_file()),None)
                references[f'occurrence-{i}-{field}']={'reference':occurrence[field],'searched':[str(p) for p in candidates]}
        files=[]
        for role,p in sources.items():
            if p is None:
                files.append({'role':role,'missing':True,**references[role]});continue
            b=p.read_bytes();manifest[str(p)]=hashlib.sha256(b).hexdigest()
            text=b.decode('utf-8',errors='replace');lines=text.splitlines()
            # Keep literal technical signals with line references. A marker is not
            # sufficient proof of native completion, process-tree stop, or safety.
            signals=[]
            for needle in ['[done] end_turn','[done] cancelled','agent needs reconnect',
                    '[dual] acpx recoverable signal','unknown option','POLICY_CONFLICT',
                    'REQUIRED_EVIDENCE_NOT_PROMPT_VISIBLE','REVISE_PROPOSAL','INVALID_JSON']:
                hits=[i for i,s in enumerate(lines,1) if needle in s]
                if hits:signals.append({'literal':needle,'lines':hits})
            entry={'role':role,'path':str(p),'sha256':manifest[str(p)],'bytes':len(b),'lines':len(lines),'signals':signals}
            if 'outputPath' in role:
                try:
                    obj=json.loads(text)
                    result=obj.get('result',{}) if isinstance(obj,dict) else {}
                    entry['jsonSyntax']='OBJECT' if isinstance(obj,dict) else 'NON_OBJECT'
                    entry['decision']=result.get('decision')
                    entry['mustFixCount']=len(result.get('must_fix') or [])
                    entry['remediation']=(result.get('failure') or {}).get('verification_remediation')
                except ValueError as exc:
                    entry['jsonSyntax']='NOT_JSON';entry['syntaxError']=str(exc)
            files.append(entry)
        inspections.append({'workstream':row['workstream'],'run':row['run'],'reason':row['reason'],
                'legacyReason':row['failureReason'],'path':row['path'],'files':files})
    result={'failedRunRecordsInspected':len(inspections),'missingRefs':sum(f.get('missing',False) for r in inspections for f in r['files']),
            'inspections':inspections}
    (HERE/'io-forensics.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    (HERE/'source-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({k:v for k,v in result.items() if k!='inspections'}))
    print('end_turn plus recorded reconnect',sum(any(s['literal']=='[done] end_turn' for f in r['files'] for s in f.get('signals',[]))
            for r in inspections if r['legacyReason']=='RECOVERABLE_AGENT_RECONNECT'))


if __name__=='__main__':main()
