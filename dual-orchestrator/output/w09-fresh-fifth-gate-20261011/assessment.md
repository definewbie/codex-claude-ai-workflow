# W09 fresh 第五例停止 · 受影响评审材料

2026-10-11；**Gate FAIL / 领域 WAITING_FOR_RECOVERY / 新派发禁止**。本报告为主 agent 自查与真实材料，不冒称独立评审。用户批准170万token/18请求/60分钟、完整主流程，先核启动前提；授权未转换为业务人门或第六例许可。

## 已确认事实

启动前：656源码输入、CLI/server、选定wrapper/dist/实际build-info/config、原398提交材料一致；9观察器与13预算对照通过；fresh空target/worktree、原start的实际runtime identity/liteflow/600s核齐，宿主Pythonstdlib可用。原生握手由实际业务请求确认；工具能力仅以实际观察为限，不概括全部工具已证。零预探针。配置实际SHA冻结，第四例同级fixture trust是唯一历史差异，经已有算法证明不扩权；运行中客户端配置由原算法逐次核验；没有主动修改客户端参数。

控制链：context→Claude需求提案合法采纳→Codex独立复评 NEEDS_REVISION（CO-1候选路径提前锁定；CO-2退出码口径缺漏）→intake.decision原路返修→Claude合法envelope与task_contract_revision_patch→原merger拒绝基础SHA不一致→唯一outcome CONTRACT_FAILED/required_artifact_contract_failed→R03/L03→FAILED节点/WAITING_FOR_RECOVERY。无新sidecar、无后继、无GOAL/EXECUTION/交付批准、零产品修改。诊断为 TASK_CONTRACT_REVISION_PATCH_INVALID / REVISION_BASE_PROPOSAL_SHA256_MISMATCH，不称缺JSON、接口不兼容或预算失败。

直接拒绝原因已证：原sidecar实际SHA、context值、提示词指令与示例一致；返回64位值只有zero-based位置43（第44字符）由9变7。id相同，其余SHA字符相同。证据见revision-base-identity-findings.json、原prompt/result和stopped-ledger-snapshot。消费者严格匹配是正确拒绝；没有手改字符、重新合并、重发或救活当前样本。

用量：3次真实顶层请求20,493 + 27,791 + 13,852 = **62,136**，格式修正0。三个occurrence的fixed usage、R03/acquisition、原生来源逐项EXACT_REQUEST_MATCH且observer核齐；两Claude走原严格共享回退、Codex走当前transcript。A案当前来源和保留回退本例有正向；本例未发生跨连接编号重用，不外推该真实场景。SDK金额不作订阅实付，不合并跨provider总金额。runner主动248.304s与native prompt duration合计226720ms分列；人工等待无。本例未耗尽预算，剩余额度不自动授予恢复或第六例。

## 尚需独立判断

1. **机器身份复制负担**：本次确为模型一字符差异，不是提示词错身份。是否只视为一次模型失误，或由现有执行context生产绑定以免模型逐字抄写，是ROI/信任边界判断，不能据单例宣称必须建机制。任何前向方案必须保留基础提案id/ref/SHA与当前返修谱系的强制检查；不接受宽松SHA、取最近产物或修当前账本。
2. **返修表达能力与复评义务错配（待判）**：真实CO-1要求“同步消除objective、scope.excluded与NG-2对三个路径提前锁定”；渲染禁止objective变化，merger复制previous.objective与previous.scope.excluded，只允许条款ADD/REPLACE、scope/evidence ADD。作者用C-4重新解释旧目标/排除项，不能据此称原字段已修改。源码事实成立；但本例首先在SHA守卫返回，未到独立复评，**不能称此项为本例已触发的第二拒绝或已证明死端**。需核对该义务合法有限的续作出口及是否可复用原schema/入口；未对修正SHA的假想补丁重放。

## 停止与现场

发现失败后写operator stop-required，零新dispatch/自动retry；原session close/show核本例3具名会话closed、12已知runner/native PID不在，仅限具名对象，不宣称任意子树全停。180非lock权威文件静止快照，live与snapshot逐项相等；target/worktree tracked/status均空。冻结发行保持，旧四例116/153/487/738和A包398条目重核一致；旧manifest中外部主清单按后续批次有意更新，具名排除且保留旧SHA，不把可变清单混作冻结现场。代码、权限、验收、客户端参数、预算判据均未改，无追加agent。

runner第6步在节点FAILED后断言退出，合乎原“未完成先评估”停止规则，断言不是Gate根因。只读材料核对中假定文件名及旧manifest外部清单口径错误已登记operator-errors，非模型/Gate故障。

## 六维

- Declared by：本次run-authorization、已审Gate、原definition与返修契约。
- Proven by：3真实result、严格hash拒绝、R03/L03、fixed usage、close/show和180文件静止快照；完整主链未证。
- Consumed by：原revision context→merger→唯一outcome→ledger/recovery；operator observer只读固定事实。
- Enforced before：身份/phase在prompt前；baseSHA不符在合并/sidecar/后继前；停止后零新dispatch。非OS写前保证。
- Failure route：CONTRACT_FAILED/MANUAL_ONLY/WAITING_FOR_RECOVERY，保留原材料；余量或通用retry显示不是新资格。
- Cannot-bypass test：本例真实错SHA拒绝且零发布；原1790基线不称本例修复回归。合法返修完整成功本例未证，需审其出口。

## 下一步动作

交CC一次合并只读复审：核FAIL分类、身份错字与潜在表达缺口、合法返修路径及最低成本取舍。不再调模型、不修代码、不恢复第五例。取得裁决后给具体有界修复/不修选择与验证方案，不自动扩大平台、抬预算或开启第六例。W08/W09主流程尚未闭合。
