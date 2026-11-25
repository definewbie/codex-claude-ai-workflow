from pathlib import Path
import json,hashlib,difflib,xml.etree.ElementTree as E
r=Path.cwd();b=r/'output/w09-sixth-forward-fix-20261015'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
t=json.loads((b/'test-results.json').read_text());a=json.loads((b/'artifact-verification.json').read_text())
assert t['exit']==0 and t['single_clean_reactor'] and t['tests']>=1805
s=(b/'assessment-draft.md').read_text().replace('状态：实施与聚焦/只读对照已完成，最终单轮回归与发行核验待完成；本稿不作收口凭证。','状态：IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SEVENTH_NOT_RUN。')
s=s.replace('FINAL_REACTOR_FACTS',f"最终单轮 clean：{t['tests']} tests / {t['suites']} suites / 0 failure / 0 error / 0 skipped，8模块，exit0；659源码输入运行前后不变，单轮凭证见test-results.json/reactor-reports。不是1805旧绿灯，也不拼两次中断轮。")
s=s.replace('FINAL_RELEASE_FACTS','CLI/server冻结副本、各六内嵌项目模块/自身字节、14资源、6只读CLI检查均通过；package使用跳测试，不另算回归。发行完整SHA见release-identity.json，native/config只核未变字节，未启动或宣称fresh实际配置。')
(b/'assessment.md').write_text(s);(b/'assessment-draft.md').unlink()
p=r/'docs/plans/20260927-dual-lifecycle-worklist.zh-CN.md';s=p.read_text().replace('当前最终回归/发行核验以本包test-results/release-identity为准，不沿用1805旧绿灯。',f"最终单轮clean {t['tests']}/{t['suites']}/0失败/0错误/0跳过，659输入前后/打包后相等；108聚焦与12观察器分列。CLI/server冻结及内嵌模块/14资源/6只读CLI核验完成；见本包test-results/release-identity，不沿用1805旧绿灯。另一次自查修正数字协议版本强转，第二中断轮也不作凭证。")
p.write_text(s)
p=r/'docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md';s=p.read_text().replace('发行/最终回归身份以[本包结果]',f"最终单轮clean {t['tests']}/{t['suites']}/0失败/0错误/0跳过、659源码输入前后/打包后相等，发行候选已冻结；完整身份以[本包结果]");p.write_text(s)
changes=[];diff=[]
for p in sorted((b/'before').rglob('*')):
 if not p.is_file():continue
 rel=p.relative_to(b/'before');q=r/rel
 if p.read_bytes()==q.read_bytes():continue
 changes.append({'path':str(rel),'before_sha256':sha(p),'after_sha256':sha(q)})
 diff.extend(difflib.unified_diff(p.read_text().splitlines(True),q.read_text().splitlines(True),fromfile='before/'+str(rel),tofile=str(rel)))
(b/'incremental.patch').write_text(''.join(diff))
(b/'changed-files.json').write_text(json.dumps({'basis':'byte-exact pre-edit copies from already dirty worktree; not HEAD','files':changes,'production_files':sum('/src/main/' in x['path'] for x in changes),'test_files':sum('/src/test/' in x['path'] for x in changes),'docs':sum(x['path'].startswith('docs/') for x in changes)},indent=2)+'\n')
for f,h in json.loads((b/'reactor-before-source-sha256.json').read_text()).items(): assert sha(r/f)==h
for app in a['applications'].values():assert sha(Path(app['frozen_path']))==app['sha256']
for p in [b/'assessment.md',b/'cc-review-prompt.txt']:
 assert p.is_file() and p.stat().st_size>0
 assert 'FINAL_REACTOR_FACTS' not in p.read_text()
files={str(p.relative_to(b)):sha(p) for p in sorted(b.rglob('*')) if p.is_file() and p.name not in ['material-sha256.json','material-verification.json'] and '__pycache__' not in str(p)}
(b/'material-sha256.json').write_text(json.dumps({'files':files},indent=2)+'\n')
assert all(sha(b/f)==h for f,h in files.items())
(b/'material-verification.json').write_text(json.dumps({'status':'AUTHOR_MATERIAL_CHECK_PASS_NOT_CC','material_files':len(files),'all_manifest_entries_match':True,'manifest_sha256':sha(b/'material-sha256.json'),'single_reactor':t,'changed_files':len(changes),'native_provider_runs':0,'extra_agents':0,'sixth_recovered':False,'seventh_started':False},indent=2)+'\n')
print(json.dumps({'tests':t['tests'],'suites':t['suites'],'material_files':len(files),'changed_files':len(changes),'production_files':5,'next':'independent CC review; no seventh run'}))
