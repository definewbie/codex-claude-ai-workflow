"""Replay captured operands and grade delivered code, with no provider calls.

Run after the targeted Maven command recorded in report.md. The Java replay uses
current compiled production validators, historical strings, and fresh synthetic
identities. It does NOT resume or authorize a historical case.
"""
from pathlib import Path
import hashlib
import importlib.util
import itertools
import json
import subprocess
import tempfile
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUTPUT = HERE.parent


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def oracle(text):
    result = []
    skip_lf = False
    for char in text:
        if char == '\n' and skip_lf:
            skip_lf = False
            continue
        skip_lf = char == '\r'
        result.append('\n' if skip_lf else char)
    return ''.join(result)


def main():
    prior = OUTPUT/'w09-readiness-method-forward-fix-20261028'
    recorded = json.loads((prior/'historical-readonly-result.json').read_text())
    inputs = [Path(p) for p in recorded['original_inputs']]
    before = {str(p): sha(p) for p in inputs}
    assert before == recorded['original_inputs'], 'Historical operands changed'
    cp = (prior/'runtime-test-classpath.txt').read_text().strip()
    # Explicitly prefer current reactor classes over any historical classpath entries.
    current = [str(p) for p in (REPO/'dual-orchestrator').glob('*/target/classes')]
    current.append(str(REPO/'dual-orchestrator/dual-runtime/target/test-classes'))
    cp = ':'.join(current+[cp])
    with tempfile.TemporaryDirectory(prefix='readonly-replay-', dir=HERE) as tmp:
        command = ['rtk','proxy','javac','-cp',cp,'-d',tmp,str(prior/'ReadOnlyMethodProof.java')]
        subprocess.run(command, check=True, capture_output=True, text=True)
        command = ['rtk','proxy','java','-cp',tmp+':'+cp,'dev.dual.runtime.handler.ReadOnlyMethodProof','historical',*[str(p) for p in inputs],str(Path(tmp)/'fixture')]
        process = subprocess.run(command, check=True, capture_output=True, text=True)
        replay = json.loads(process.stdout.strip().splitlines()[-1])
    replay['input_bytes_unchanged'] = before == {str(p):sha(p) for p in inputs}
    assert replay['input_bytes_unchanged']
    (HERE/'historical-replay.json').write_text(json.dumps(replay,ensure_ascii=False,indent=2)+'\n')

    target = OUTPUT/'w09-fixtures/eighth-20261023/target'
    source = target/'src/normalize_text.py'
    # The inspected file contains only the pure normalization function.
    spec = importlib.util.spec_from_file_location('delivered_normalizer', source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    alphabet = ['a', '\r', '\n', '\t', '中', '\u2028']
    cases = [''.join(chars) for n in range(6) for chars in itertools.product(alphabet,repeat=n)]
    def grade(fn):
        failed = 0
        for text in cases:
            actual = fn(text)
            failed += actual != oracle(text) or fn(actual) != actual
        return {'cases': len(cases), 'failed': failed}
    grade_result = {
        'scope':'Harness-external deterministic function grader; authored in this audit, not a third-party blind benchmark; no full OKX/browser claim',
        'source':str(source),'source_sha256':sha(source),
        'delivered': grade(module.normalize_newlines),
        'noop_mutant':grade(lambda x:x),
        'cr_first_mutant':grade(lambda x:x.replace('\r','\n').replace('\r\n','\n')),
    }
    assert grade_result['delivered']['failed'] == 0
    assert grade_result['noop_mutant']['failed'] > 0 and grade_result['cr_first_mutant']['failed'] > 0
    git = subprocess.run(['rtk','proxy','git','rev-parse','HEAD'],cwd=target,check=True,capture_output=True,text=True)
    grade_result['target_head'] = git.stdout.strip()
    (HERE/'external-grader.json').write_text(json.dumps(grade_result,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'replay_rejections':replay['original_method_variants_rejected'],'exact_variant_accepted':replay['isolated_exact_method_variant_accepted'],'grader':grade_result},ensure_ascii=False))


if __name__ == '__main__':
    main()
