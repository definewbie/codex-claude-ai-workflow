import difflib
import hashlib
import json
from pathlib import Path

root = Path.cwd()
b = Path(__file__).parent
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def write(name, value): (b/name).write_text(value)
t = json.loads((b/'test-results.json').read_text())
r = json.loads((b/'release-identity.json').read_text())
assert t['single_clean_reactor'] and t['failures'] == t['errors'] == t['skipped'] == 0
assert r['source_input_count'] == 659
assert all(sha(Path(k)) == v for k,v in r['source_inputs'].items())
assert all(sha(Path(v['frozen_path'])) == v['sha256'] for v in r['application_jars'].values())
proof = f"{t['tests']} 项 / {t['suites']} suites / 0 failure / 0 error / 0 skipped"
write('assessment.md', f'''# W09 身份减负与初次 GOAL 前目标级返修：实施、回归与发行候选

状态：**IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SIXTH_NOT_RUN**。本报告是主 agent 自查，不能替代独立 CC 结论，不关闭整体 W01/W07/W08/W09，不授权真实运行。

## 1. 授权目标与本轮结果

用户批准两个既有 owner 内的有界接线、设计 LOW、回归、重冻和合并 CC 复审；明确暂不启动第六例。对应 P0 合法主流程：去掉冗余 SHA 转录负担，并让首次 GOAL 前的真实目标/禁区返修回到当前复评和人门。没有 token 节省验收，也没有新增安全沙箱任务。

| 项 | 实际变化与强制消费者 | 证明及边界 |
|---|---|---|
| 身份减负 | 模型只输出确切 base_proposal_id；adapter 同一次基准解析供提示词与既有 R01；完整 ref/SHA/义务/允许形态由程序固定。dispatch、publisher/merger、reference/lineage/compile 强制消费 | 合法无 SHA 正向、旧 SHA 键严格拒绝、基准字节漂移/缺 R01/伪派生拒绝；未运行真实客户端，不承诺消除所有 ID 转录错误 |
| 目标级返修 | 首 GOAL 前可信 NEEDS_REVISION 或人 revise 可用已有 full proposal + base ID；真正 objective/excluded 实变、有限 delta/当前义务仍检查；新 subject 必须复评→当前人门→GOAL | 四变体 × inline/worker 合法链及未批 EXECUTION 拒绝；已授权/late/pending 人批不可借用，历史追加不反向撤销旧合法提案；不新增跨 GOAL 在线迁移 |
| LOW-1 | resolveFromRefs 比完整 reference，相同 ID 不能抹平不同完整内容身份 | 两个独立有效引用、顺序互换拒绝；同一完整引用重复合法；删除守卫反证 |
| LOW-2 | derivation 注释说明 patch_source_output_ref 为历史字段名，full goal_reproposal 同样指作者原始输出 | 不迁移字段，不洗旧历史，不新增兼容分支 |

增量相对开工前 dirty 工作区，见 [changed-files](changed-files.json)、[incremental.patch](incremental.patch)、[before](before/) 与输入指纹；不是 HEAD 全量 diff。本轮 29 个源码/测试文件（15 生产、14 测试）及 2 份当前规划文档；生产变更仅 runtime 契约/执行准备/消费者及 adapter 作者接线。新增一个局部 ContractRevisionBinding，复用 R01；没有新节点、artifact type、锁、服务、存储、DSL 或第二套审批权威。

## 2. 完整链、失败切面与不能绕过

[消费者与 transition 表](consumer-and-transition-map.md)、[覆盖索引](verification-coverage.md)和[开工边界](implementation-boundaries.md)记录 SoT、owner、写序、下一消费者、恢复和兄弟。链为：原返修决定→一次 capture→R01/L claim→dispatch/R02→raw/严格语法→唯一 outcome→发布/完整基准 merger→derivation→R03/L03→新复评→当前人批→原 GOAL/EXECUTION。

新增常驻序列实际覆盖 R01 已固定后的 L bind 和 R03 已固定后的 L03 三个关键前缀（before_manifest / after_replace / before_pending_delete），两形态与双入口；真实冷子 JVM 消费固定完成而不重发模型。无 R03 不授完成，orphan/pending 不授新派发，缺固定材料不伪造成功。原共享协议测试在本轮全回归中复跑，但不宣称全部旧 failpoint 已分别穷举所有新形态。

初始、普通 patch、新 full、人工 revise、late clarification、四实际工作流装配和 inline/worker 均入检查。contract 冷 JVM 在后来 GOAL/EXECUTION 批准后重读初始/full/patch 三份历史，证明正常历史追加不制造假漂移。原权限 C/X、scope、退出码/证据、独立验收、交付批准语义未变。

## 3. 验证证据分层（不能相加）

| 层 | 凭证 | 能证明什么 / 不外推什么 |
|---|---|---|
| 旧生产先红 | red-wire.log：2 项预期失败 | 旧生产拒绝合法 ID-only，并接受现已撤销的 SHA 键；不等于真实模型行为 |
| 常驻新增及迁移 | RevisionIdentityWireTest、ProposalReferenceIdentityTest、HumanRequirementContinuationSequenceTest 与原契约/编译/lineage 族 | 真正本地 prepared/ledger/Git/冷 JVM，模型输出由离线 transport 产生；合法与拒绝成对 |
| 删除守卫 | [counterproof-results](counterproof-results.json)：3 个隔离变体均命中预期断言，源码恢复 | 完整 reference、merger 完整 SHA、full 真实目标变化三守卫；不计入回归总数 |
| 唯一最终回归 | [test-results](test-results.json)、reactor-final.log、reactor-reports/：**{proof}**，8 模块，exit 0 | 单轮 clean；659 输入运行前/后及打包后一致；不拼接定点绿灯或历史 1790 |
| 发行核验 | [release-identity](release-identity.json)、[artifact-verification](artifact-verification.json) | CLI/server 冻结副本及内嵌项目模块/自身 class/resource 字节一致；14 资源、6 个只读 CLI 检查；打包跳测试不冒充第二轮回归 |
| 未运行 | 原生客户端/provider、第六例、真实完整主流程、token 节省 | 全部 NOT_RUN；本地帮助/定义校验不证明这些能力 |

中间编译/夹具/错误断言、运行中源码变化、主动中断、attempt-02 一项旧拒绝时点断言失败均保留日志，并在[自查复盘](review-and-reflection.md)分立；均非当前全绿凭证。attempt-02 的迁移仅把断言移到新的更早原拒绝入口，补 reference 不可用/零输出/零新调用，不改生产 guard。counterproof 的初始分类标记大小写错误单列为检查器错误，原日志未改。

## 4. 六维自查

| 维度 | 本轮落点 |
|---|---|
| Declared by | 用户有界实施授权、受审 D10/D03 补记、设计接收 LOW；原批准/证据义务不撤销 |
| Proven by | 上述常驻完整合法/拒绝链、真实本地写前缀/冷 JVM、隔离反证、唯一单轮回归；真实客户端仍空 |
| Consumed by | capture→同 R01→qualifyDispatch→ArtifactContractValidator/merger→reference/lineage→compile；projection 仍消费原 read model |
| Enforced before | 基准/资格在 dispatch 前；ID/完整内容/形态/义务在 sidecar/reference/复评推进前；当前人批在 GOAL/EXECUTION 权威产生前；不是 OS 写前隔离 |
| Failure route | 原 typed failure/outcome、D06 固定字节、D07 外部 UNKNOWN；无 outbox/自动重发/旧批准借权/第二控制原因 |
| Cannot-bypass test | 同 ID 异 fullref、旧 wire、变基准、只删 R01、伪 kind/initial、无实际目标变化、未批写资格；关键守卫删除后正反对照失败 |

## 5. 历史、发行与复杂度边界

[historical-preservation](historical-preservation.json)：设计包 15、第五例 326、A 修复包 398 条提交材料逐条 SHA 不变；第五例仍 FAIL，旧 raw 不补 SHA、不恢复。旧 release-identity 记录与事故材料保持；原记录指向的 target 构建目录按本次授权重建，**不声称旧 target JAR 仍保留**。本次 CLI/server 冻结副本实际存于本包 release/。

[选定 native 字节核对](selected-native-bytes-recheck.json)只证明 wrapper/dist/配置文件未改变，未运行 installer/native 或测 fresh effective config。未来批准的 Gate 必须重核实际装配、配置、当前 subject、出口、observer、预算与进程拓扑。当前运行与请求预算均为零；不继承第五例余额。

收益限定为消除已见 64 位 SHA 冗余复制和接通被字段形态限制的首 GOAL 前合法返修。其余模型身份字段、真实后半链与 token 成本未声明完成。实际增量没有越出受审 owner 或引入平台；未触发需要用户新取舍的成本收益偏差。

## 6. 下一步动作

提交[合并 CC 只读复审提示词](cc-review-prompt.txt)，独立判断实施/离线证据、发行候选与残余缺口。通过后再准备并由用户分别批准 fresh 第六例当次 Gate/预算/真实运行；本轮不创建或启动第六例、不提交代码。
''')

design = root/'docs/plans/20260928-dual-lifecycle-detailed-design.zh-CN.md'
s = design.read_text()
assert '### 10.3 W09' not in s
block=f'''### 10.3 W09 两项接线有界实施与发行候选（2026-10-14）

用户明确批准有界实施、两项 LOW、回归与重冻，并暂不启动第六例。当前 IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SIXTH_NOT_RUN，入口[本轮 assessment](../../output/w09-revision-forward-fix-20261014/assessment.md)。§10.1/§10.2 原状态与“未改代码”保留为提交时历史，不再代表当前实现。

模型只引用精确 base ID，完整 ref/SHA、义务及允许形态由同一次 capture 固定至既有 R01；dispatch、publisher/merger、reference/lineage/compile 强制消费。首次 GOAL 前可信返修可用已有 full proposal：真实目标/禁区变化、bounded delta、当前义务、新复评/当前人门不省略；已授权/late/pending 人批不借权，不新增跨 GOAL 在线重写。LOW-1 完整 reference 去歧义及成对/移除反证落码；LOW-2 历史字段语义注释落码，不迁移历史。

唯一单轮 clean {proof}，8 模块、659 输入运行前后/打包后相等；两形态/双入口关键 R01/L03 写前缀、真实冷 JVM、四变体合法链与拒绝对照分列，未穷举所有旧故障点 × 新形态。CLI/server 冻结副本/内嵌模块/14 资源/6 只读命令核验完成，见[发行身份](../../output/w09-revision-forward-fix-20261014/release-identity.json)。这证明离线接线与发行字节，不证真实客户端遵守新契约、完整后半链或 token 节省；未外推整体 W 项完成。

设计/第五例/A 提交材料逐条不变，不加盖旧 raw、不救第五例；原 target JAR 是授权重建目录，不声称旧 JAR 保持字节。本轮新冻结副本有独立 SHA。下一步合并受影响 CC 复审；第六例当次 Gate 与真实运行另获用户授权。

'''
s=s.replace('## 11.',block+'## 11.',1)
lines=s.splitlines()
for i,l in enumerate(lines):
 if l.startswith('| D03 |'):
  lines[i]='| D03 | 附录 §4 + examples；首 GOAL 前目标级返修 §10.1–§10.3：现有 full 形态、固定基准、当前复评/人门 | 原设计冻结；受影响新接线已实施并离线验证，发行候选待合并 CC；不新增 late/跨 GOAL 在线目标重写，真实主链未证 |'
 if l.startswith('| D10 |'):
  lines[i]='| D10 | 附录 §10 与 §10.1–§10.3：模型精确 ID 引用、完整基准 R01 固定及多层强制消费 | 受影响接线实施/离线验证/重冻已完成，合并 CC 待审；其余字段义务保留，真实模型减负和 token 收益未证，不关闭 W07 |'
design.write_text('\n'.join(lines)+'\n')

work = root/'docs/plans/20260927-dual-lifecycle-worklist.zh-CN.md'
s=work.read_text(); assert '### 23.23 W09' not in s
rows={
 '| W01 ':'| W01 人工决定→作者修订→复评→重新批准；失败原因→合法补足后续作 | I1/I1.a；core/ledger/revision/recovery | B03 合法续作，B05 C 单族独立 PASS；§23.23 首 GOAL 前目标级新提案已实施/离线验证 | 其余诊断族 UNKNOWN；本轮新接线/发行待合并 CC，真实完整生命周期未证，不自动第二族 | 单族 PASS；整体 W01 未关闭 |',
 '| W07 ':'| W07 机器身份与语义义务分离、有效修订与提示负担 | I1/I2 GCTW；原 prompt/context/contract/correction owner | §23.23 模型 SHA 转录撤销、精确 ID 引用、程序完整身份固定及强制消费已实施/离线验证 | 两 LOW 落码；发行候选待合并 CC；其余字段/语义义务不减，真实模型行为/token 收益未证 | 本轮离线接线完成不关闭 W07 |',
 '| W08 ':'| W08 组合回归/各投影→可审查运行 Gate | R3 §4/§5/§6；全部消费者/CLI/API/UI/report | §23.23 当前单轮全模块/稳定输入/新发行字节核验完成 | 合并受影响 CC 待审；第六例 fresh 装配/配置/出口/observer/预算仍须当次核验，browser/真实 worker 未证 | FROZEN_CANDIDATE；整体 W08 未关闭 |',
 '| W09 ':'| W09 fresh 合法任务真实研发/测试验收/批准交付 | R3 §0/§6；真实 evidence/candidate/目标 Git | 第五例原 FAIL 与材料不变；§23.23 两项前向接线完成离线验证与重冻 | 合并 CC 后第六例 Gate/运行另获授权，不恢复旧例/沿用余额；后半链真实成功未证 | 第六例未创建/未运行；W09 未完成 |'
}
lines=s.splitlines()
for i,l in enumerate(lines):
 for prefix,value in rows.items():
  if l.startswith(prefix):lines[i]=value
s='\n'.join(lines)+'\n'
s+=f'''
### 23.23 W09 身份减负与目标级返修实施、重冻待合并复审（2026-10-14）

用户批准有界实施/两 LOW/回归/重冻与 CC 提示，明确暂不启动第六例。入口[assessment](../../output/w09-revision-forward-fix-20261014/assessment.md)、[实际消费者/失败切面](../../output/w09-revision-forward-fix-20261014/consumer-and-transition-map.md)、[自查复盘](../../output/w09-revision-forward-fix-20261014/review-and-reflection.md)。当前 IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SIXTH_NOT_RUN；§23.21/§23.22 保留提交时历史状态，不能把自查当独立 PASS。

模型精确 base ID + 同次程序 R01 完整基准，dispatch/publisher/merger/reference/lineage/compile 强制消费；初次 GOAL 前真实目标/禁区变更 full→新复评→当前人门→原分期批准合法链落码，不开放跨 GOAL/late 在线目标重写。两 LOW 完整 reference 去歧义/注释实质落实。增量仅 runtime/adapter，复用既有 R01/L/R 协议，无新节点/锁/服务/DSL；未触发新增 ROI 取舍。

唯一单轮 clean {proof}、8 模块、659 输入前后及打包后相等；真实本地 ledger/Git/冷 JVM 与模型离线 fixture 分列，两个关键新写前缀族实际执行，不冒称全部新组合穷举。两项旧生产先红、三项隔离反证、中间作者/夹具/断言错误与中断独立记录，不拼接早期绿灯。CLI/server 独立冻结副本/内嵌模块/14 资源/6 只读 CLI 核验完成，见[发行身份](../../output/w09-revision-forward-fix-20261014/release-identity.json)。

设计15/第五326/A398提交材料 SHA 不变；第五原 FAIL/raw 不回写、不救样本。旧 target JAR 为授权构建目录，本次重建，不冒称旧 JAR 保留；新发行副本独立固定。零真实 native/provider/额外 agent，第六例未创建，无代码提交。真实客户端遵守新 wire、完整后半链及 token 收益均 NOT_RUN，不关闭整体 W01/W07/W08/W09。

**下一步动作**：交[合并 CC 只读复审提示](../../output/w09-revision-forward-fix-20261014/cc-review-prompt.txt)，判断实施/离线证据与发行候选。通过后再准备 fresh 第六例当次 Gate/预算并单独取得用户运行授权；先核实际配置/装配/出口/observer/当前 subject，不继承旧预算或授权。
'''
work.write_text(s)

before=json.loads((b/'before-source-sha256.json').read_text())
before.update(json.loads((b/'before-docs-sha256.json').read_text()))
inputs=set(before)|set(r['source_inputs'])
changed=[]; patches=[]
for name in sorted(inputs):
 p=root/name; old=b/'before'/name
 after=sha(p) if p.exists() else None
 if before.get(name)==after:continue
 changed.append({'path':name,'before_sha256':before.get(name),'after_sha256':after,
   'kind':'ADDED' if name not in before else ('MODIFIED' if p.exists() else 'DELETED')})
 oldlines=old.read_text().splitlines(keepends=True) if old.exists() else []
 newlines=p.read_text().splitlines(keepends=True) if p.exists() else []
 patches.extend(difflib.unified_diff(oldlines,newlines,fromfile='a/'+name if old.exists() else '/dev/null',tofile='b/'+name if p.exists() else '/dev/null'))
source_files=[x for x in changed if '/src/' in x['path']]
prod=[x for x in source_files if '/src/main/' in x['path']]
tests=[x for x in source_files if '/src/test/' in x['path']]
assert len(source_files)==29 and len(prod)==15 and len(tests)==14 and len(changed)==31
write('changed-files.json',json.dumps({'baseline':'measured dirty worktree, not HEAD','source_files':source_files,
 'source_count':len(source_files),'production_count':len(prod),'test_count':len(tests),
 'documentation_files':[x for x in changed if x not in source_files],'all_changed_files':changed,'total':len(changed)},indent=2)+'\n')
write('incremental.patch',''.join(patches))
print(json.dumps({'tests':t['tests'],'suites':t['suites'],'source_changed':len(source_files),'production':len(prod),'tests_changed':len(tests),'docs':2}))
