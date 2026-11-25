"""Assemble inspectable evidence references and archive this review's fresh tests.

Does not execute providers or alter any historical input. Classification is a
human adjudication; source observations and its limitations remain explicit.
"""
from pathlib import Path
from collections import Counter
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
D = json.loads((HERE / 'inventory.json').read_text())
MANIFEST = json.loads((HERE / 'source-sha256.json').read_text())
CASES = [c for c in D['cases'] if c['population'] == 'OPERATIONAL_RETAINED']
RUNS = D['runs']
EVIDENCE = []


def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def run(rid):
    return Path(next(r['path'] for r in RUNS if r['run'] == 'run-' + rid))


def case(suffix):
    return Path(next(c['root'] for c in CASES if c['workstream'].endswith('-' + suffix)))


def ref(path, needle, label):
    path = Path(path)
    lines = path.read_text().splitlines()
    hits = [i for i, line in enumerate(lines, 1) if needle in line]
    if not hits:
        raise ValueError(f'{path}: missing {needle}')
    MANIFEST[str(path)] = digest(path)
    return {'label': label, 'path': str(path), 'line': hits[0], 'matchingLines': hits,
            'needle': needle, 'sha256': digest(path)}


def add(eid, title, finding, limit, refs):
    EVIDENCE.append(dict(id=eid, title=title, finding=finding, limitation=limit, references=refs))


knjm = run('1780398017039')
add('E01', 'KNJM：reconnect 与正文 end_turn 并存',
    '失败原因与正文完成标记不一致，属于待核验的执行完成/信号优先级边界。',
    '正文标记不能证明属于本次原生请求的完成事实；不能判为已证实的假失败，更不能直接续传。', [
    ref(knjm, 'RECOVERABLE_AGENT_RECONNECT', 'run 失败原因'),
    ref(knjm.parent/'outputs/intake-claude-brainstorm.md', '[done] end_turn', '正文末尾'),
    ref(knjm.parent/'outputs/intake-claude-brainstorm.stderr.log', 'agent needs reconnect', 'stderr')])

prf = run('1782180664429')
add('E02', 'PRF7：进程被中断，顶层却是结果协议失败',
    'stderr 记录 harness 主动发出 interrupt_process=true；顶层 primaryReason 为 terminal_control_missing_or_invalid。',
    '证明历史分类压缩，不能证明已发生的文件/远程副作用可安全重做。', [
    ref(prf, 'terminal_control_missing_or_invalid', '顶层原因'),
    ref(prf.parent/'outputs/plan-claude.stderr.log', 'interrupt_process=true', '主动停止证据')])

ayu = run('1783479932615')
add('E03', 'AYU5 / FUMK：证据可见性准入失败',
    'required review_evidence_packet 缺少 prompt visibility claim，进程 NOT_STARTED；同类七次分布在五个案例。',
    '这是正确的启动前拒绝；源工件存在不能证明内容已经进入最终提示。当前版本同类生产缺口是否仍可触发未证。', [
    ref(ayu, 'REQUIRED_EVIDENCE_NOT_PROMPT_VISIBLE', '准入理由'),
    ref(ayu, 'NOT_STARTED', '未调用进程'),
    ref(run('1783517570025'), 'REQUIRED_EVIDENCE_NOT_PROMPT_VISIBLE', 'FUMK 再次同类拒绝')])

scope = run('1782746818017')
add('E04', '9S3P：机器路径字段混入自然语言',
    'scope/evidence 字段未满足文件和证据契约；应审查生产者和发现入口，保留验证器。',
    '任务是否必须允许该文件，需要结合获批 scope，不能从拒绝本身推导扩大权限。', [
    ref(scope, 'scope.included is not a supported workspace path', '契约失败原文')])

hhzl = run('1788476621271')
add('E05', 'HHZL：必须按 occurrence 顺序解释纠正',
    '先发生 INVALID_JSON，唯一纠正后仍有非法枚举；不能把最终枚举异常归因于先前提示丢失诊断。',
    '精确枚举诊断是局部改进；对原两次输出序列并未证明可以成功。', [
    ref(hhzl, 'maxCorrectionAttempts', '单次纠正预算'),
    ref(hhzl, 'REVISE_PROPOSAL', '最终枚举异常'),
    ref(hhzl.parent/'prompts/intake-codex-product-review.contract-correction.md', 'INVALID_JSON', '初次错误对应的纠正输入')])

bx = case('BXWV')
add('E06', 'BXWV：缺外部证据仍回实现',
    '验收 must_fix=[] 且 EVIDENCE_COLLECTION；自测说明缺外网/浏览器，随后仍路由回 implement。',
    '修复路由最多避免无依据改代码；外部证据依然必须真实取得，不能据此保证交付。', [
    ref(bx/'runs/run-1789003988271/outputs/acceptance-codex-review.json', '"must_fix"', '验收代码缺陷列表为空'),
    ref(bx/'runs/run-1789003988271/outputs/acceptance-codex-review.json', 'EVIDENCE_COLLECTION', '修复动作类型'),
    ref(bx/'events.jsonl', '"event_id":"domain-node-acceptance-classify_failure-092"', '验收分类（具体序列见 ledger 316–331 行）'),
    ref(bx/'events.jsonl', '"event_id":"evt-000096"', '自测仍回实现'),
    ref(bx/'events.jsonl', '"event_id":"evt-000097"', '实际缺少外部证据的原因')])

mb = run('1791728580831')
mbnode = next(p for p in (mb.parents[2]/'nodes').glob('*.json') if 'REVISION_BASE_PROPOSAL_SHA256_MISMATCH' in p.read_text())
add('E07', 'MBC2：程序已知 SHA 被模型重新转写',
    'revision 基线哈希转写错误，验证器正确拒绝；当前 ContractRevisionBinding 已改由程序绑定执行身份。',
    '保留拒绝错误基线的测试；不要用兼容旧错误哈希挽救单例。', [
    ref(mbnode, 'REVISION_BASE_PROPOSAL_SHA256_MISMATCH', '节点拒绝'),
    ref(mb.parent/'prompts/intake-claude-brainstorm.md', 'f239f', '输入基线 SHA')])

v9 = run('1783188244002')
add('E08', 'V9W8：探索范围拒绝是必要保护',
    '读取 backend/tests/test_safety.py 越过既有探索 scope，且无范围扩展申请。',
    'exit 0 不代表节点应该成功；允许范围是否过窄需要另证。', [
    ref(v9, 'READ_OUTSIDE_EXPLORATION_SCOPE', '越界'),
    ref(v9, 'SCOPE_EXPANSION_REQUEST_MISSING', '缺少合法扩权动作')])

sn = case('7SNA')
add('E09', '7SNA：固定 method 被模型加写细节',
    '计划把已登记 method 与逐项细节混合；当前验证器要求 method 完全相等。',
    '该 run 已 completed，ledger 最新节点仍 READY。这里只能认定后续资格风险，不能宣称 ledger 已因此失败；新 validator 处理旧输入也是反事实验证。', [
    ref(sn/'runs/run-1793048679201/outputs/plan-claude.plan.json', '"method"', '计划 method'),
    ref(sn/'artifacts/verification-readiness/okx-ac-availability-20261027-01.json', '"method"', '登记的固定 method'),
    ref(sn/'nodes/node-plan-codex_plan_review-025.json', 'READY', '实际后续节点尚未执行')])

yy = case('9YYF')
add('E10', '9YYF：真实交付反例',
    'ledger 存在 delivery_committed，目标仓库 HEAD 与交付 SHA 一致，本次只读复查已确认。',
    '证明至少一个新版本案例能走到提交，不等于异构任务总体成功率或外部 grader 质量通过。', [
    ref(yy/'events.jsonl', 'delivery_committed', '交付事件')])

three = case('3B2P')
add('E11', '3B2P：revision 耗尽不必然是仪式过度',
    '人工 revise 指向 SQLite 事务等实质缺陷；有真实产品原因，不能默认放宽上限。',
    '上限是否过紧，需要按阻塞类型、每轮增量与预算分别评价。', [
    ref(three/'events.jsonl', 'revision_limit_reached', '修订上限事件'),
    ref(three/'gates/gate-node-gate-human_design_approval-038.json', 'SQLite', '实质事务问题的修订要求')])

short = run('1788347435520')
long = run('1788347257826')
add('E12', '9BZG：用量归属待核销',
    '201ms run 与另一 run 均记录 totalTokens=404593 且 LATEST_PROMPT_IN_SESSION。',
    '相同用量本身不能证明重复收费；须 provider request/prompt ID 证明唯一归属。总账也存在缺失，不能宣称统一上界或下界。', [
    ref(short, 'LATEST_PROMPT_IN_SESSION', '短 run 归属'),
    ref(short, '404593', '短 run 用量'),
    ref(long, '404593', '另一 run 用量')])

code = 'dual-orchestrator/'
add('C01', '当前 typed retry 策略与消费入口',
    '所查失败分支产出 MANUAL_ONLY，WorkflowRuntime 仅对 AUTO_ALLOWED 执行 typed 自动重试。',
    '不能据此把所有失败改成 AUTO_ALLOWED；必须先证明授权、停止、副作用与预算。', [
    ref(REPO/(code+'dual-core/src/main/java/dev/dual/core/execution/AgentExecutionOutcomeResolver.java'), 'AgentExecutionRetryPolicy.MANUAL_ONLY', '分类策略'),
    ref(REPO/(code+'dual-runtime/src/main/java/dev/dual/runtime/WorkflowRuntime.java'), 'if (!"AUTO_ALLOWED".equals(retryPolicy))', '唯一策略消费者')])

for eid, filename, needle, title in [
    ('C02','ReviewDecisionHandler.java','SELF_TEST_INCOMPLETE','当前自测 continuation 分类'),
    ('C03','EvidenceCollection.java','guard.verify','当前证据收集副作用检查'),
    ('C04','AgentRunCommitProtocol.java','beginExternalCall','当前外部调用提交协议'),
    ('C05','VerificationReadiness.java','request.method().equals(binding.method())','当前 readiness 精确绑定'),
    ('C06','ContractRevisionBinding.java','verifyDispatch','当前 revision 程序身份绑定')]:
    p=REPO/(code+'dual-runtime/src/main/java/dev/dual/runtime/handler')/filename
    add(eid,title,'当前工作区源码，独立于历史执行身份。','通过定向测试不能推广为历史 binary 行为或整体 E2E 通过。',[ref(p,needle,title)])

adapter=REPO/'dual-orchestrator/dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx'
lifecycle=REPO/'dual-orchestrator/dual-runtime/src/main/resources/dev/dual/runtime/lifecycle'
add('C07','当前取证：禁止污染发布，不等于阻止源码写入',
    '三个内置 sibling 的 self_test_report 均使用 implement profile；handler 没有根据 collection 切换权限。E1 allow_write 含工作树；guard 在执行后拒绝，现有测试明确保留污染文件。',
    '证明当前源码和真实 runtime 消费序列中的权限/发布边界差异；未启动真实 provider，也不主张历史所有取证节点均发生过源码污染。',[
    ref(adapter/'AcpxAgentTaskHandler.java','var effectivePermissionProfile = permissionProfile(context);','权限由定义解析'),
    ref(adapter/'AcpxAgentTaskHandler.java','var collectionInstruction =','取证标记只添加提示'),
    ref(adapter/'AcpxPermissionProfile.java','static AcpxPermissionProfile implement()','implement 自动允许编辑/执行'),
    ref(adapter/'ClaudeExecutionPolicy.java','filesystem.put("allow_write"','E1 工作树可写'),
    ref(REPO/'dual-orchestrator/dual-runtime/src/main/java/dev/dual/runtime/handler/EvidenceCollection.java','result = execution.run();','先执行再 verify'),
    ref(REPO/'dual-orchestrator/dual-runtime/src/test/java/dev/dual/runtime/handler/EvidenceCollectionSequenceTest.java','never silently revert source','测试证明污染仍留磁盘'),
    *[ref(lifecycle/('agent-lifecycle.'+variant+'.json'),'"id": "implement.claude_self_test_report"',variant+' 节点及随后 config')
      for variant in ('full','contract','terminal-delivery')]])

selected = {'AgentExecutionOutcomeResolverTest','SelfTestContinuationSequenceTest','AcceptanceRepairSequenceTest',
            'RevisionIdentityWireTest','VerificationReadinessTest','AgentRunCommitProtocolTest',
            'AcpxAgentResultAcquirerTest','ContractRegistryTest',
            'EvidenceCollectionSequenceTest','RunningStateGuardTest','AcpxCurrentTranscriptUsageTest'}
reports=[]
for module in ('dual-core','dual-runtime','dual-adapter-acpx'):
    for p in (REPO/'dual-orchestrator'/module/'target/surefire-reports').glob('TEST-*.xml'):
        if p.stem.split('.')[-1] not in selected:
            continue
        root=ET.parse(p).getroot()
        target=HERE/'tests'/module/p.name
        target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(p,target)
        reports.append({'suite':root.attrib['name'],'tests':int(root.attrib['tests']),
                        'failures':int(root.attrib['failures']),'errors':int(root.attrib['errors']),
                        'skipped':int(root.attrib.get('skipped',0)), 'path':str(target),'sha256':digest(target)})
assert len(reports)==11 and sum(x['tests'] for x in reports)==197
assert not any(x['errors'] or x['failures'] for x in reports)
source_identity={}
for module in ('dual-core','dual-runtime','dual-adapter-acpx'):
    for p in (REPO/'dual-orchestrator'/module/'src').rglob('*'):
        if p.is_file(): source_identity[str(p)]=digest(p)
    p=REPO/'dual-orchestrator'/module/'pom.xml'
    source_identity[str(p)]=digest(p)
source_identity[str(REPO/'dual-orchestrator/pom.xml')]=digest(REPO/'dual-orchestrator/pom.xml')
identity=json.dumps(source_identity,sort_keys=True).encode()
verification={'kind':'CURRENT_WORKSPACE_TARGETED_OFFLINE_TESTS','historicalReplay':False,
    'independentReview':False,'providerCalls':0,'head':subprocess.check_output(['rtk','proxy','git','rev-parse','HEAD'],text=True).strip(),
    'workspaceSourceDigest':hashlib.sha256(identity).hexdigest(),'sources':source_identity,'reports':reports}
target=REPO/'dual-orchestrator/output/w09-fixtures/eighth-20261023/target'
verification['deliveryCounterexample']={'target':str(target),'observedHead':subprocess.check_output(
    ['rtk','proxy','git','-C',str(target),'rev-parse','HEAD'],text=True).strip(),
    'expectedHead':'f8ec3d41cbe1cac554a78c3131372af434440691'}
assert verification['deliveryCounterexample']['observedHead']==verification['deliveryCounterexample']['expectedHead']
(HERE/'current-verification.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2)+'\n')
(HERE/'source-sha256.json').write_text(json.dumps(MANIFEST,ensure_ascii=False,indent=2)+'\n')
(HERE/'evidence-index.json').write_text(json.dumps(EVIDENCE,ensure_ascii=False,indent=2)+'\n')
out=['# 原始证据索引','', '本文件由 assemble_review.py 生成。行号为一基；发现项与限制必须同时阅读。','']
for item in EVIDENCE:
    out += [f'## {item["id"]} {item["title"]}','',item['finding'],'','限制：'+item['limitation'],'']
    for r in item['references']:
        out += [f'- [{r["label"]}]({r["path"]}:{r["line"]})；匹配行 {r["matchingLines"]}；SHA256 `{r["sha256"]}`。']
    out += ['']
(HERE/'evidence-index.md').write_text('\n'.join(out)+'\n')
print(json.dumps({'evidenceItems':len(EVIDENCE),'tests':sum(x['tests'] for x in reports),'failures':0,
                  'sourceFilesHashed':len(MANIFEST),'testedSourceFiles':len(source_identity)},ensure_ascii=False))
