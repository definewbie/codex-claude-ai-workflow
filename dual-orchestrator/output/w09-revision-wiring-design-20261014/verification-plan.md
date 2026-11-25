# 合并验证设计：身份减负 / 初次 GOAL 前目标级返修

状态：**DESIGNED / ALL PRODUCT CASES NOT RUN**。本文件是未来授权实施后的验证义务，不是测试执行记录；examples.json 的人工样例不能证明生产 parser、compiler或任何原生客户端能力。

## 1. 必须可达的合法序列

| ID | 序列 / authoritative facts | 必须成立 / owner与拒绝时点 |
|---|---|---|
| V01 | 初始fullproposal→复评NEEDS_REVISION→patch（仅base ID，无模型SHA）→新复评READY→当前需求门→GOAL | R01固定fullref/SHA；merger/publisher接受合法无SHA；复评与compile读同派生；GOAL included=[] |
| V02 | 初始fullproposal→CO-1目标/禁区+CO-2条款→带base ID的fullproposal→复评→人批→新GOAL | objective/excluded实变、条款覆盖齐；旧review不当新READY/批准；全链到设计/计划/EXECUTION未批零实现 |
| V03 | 需求人门revise→新iteration→目标级fullproposal→复评→新的需求门 | 不复用已完成门；同型链不冒充该入口已证；覆盖human note绑定 |
| V04 | V02的新提案再NEEDS_REVISION→下一patch→READY→人批 | 下一base是新提案而非原最早基准；义务仍当前来源、原max_revisions不增加 |
| V05 | 普通patch / 初始fullproposal正向 / 程序基准未变的冷读 | 新契约不误拒原合法流程；raw输出保持原字节，derivation的fullSHA来自程序 |

## 2. 拒绝与不能绕过

| ID | 成对反例 / 移除式反证 | guard owner / 时点 / 失败出口 |
|---|---|---|
| V06 | 错/空base ID、同前缀不同完整base、跨workstream/iteration/claim；对照exact当前ID | 固定基准reader/merger，发布前拒；不从ID查latest、不程序改ID；原typed合同失败 |
| V07 | prompt准备→R01后替换base bytes/review/义务/route；缺R01/断L绑定/错preparedSHA | protocol派发前及publisher采纳前双核；移去R01对照/完整SHA守卫必须被抓；无新dispatch/sidecar |
| V08 | 旧model SHA键（含第五例错字）、unknown fields/duplicate JSON；对照新合法结果 | 唯一strict parser拒；有界correction保持ID/语义形态，禁止自动删键/加盖坏历史输出 |
| V09 | 伪造goal_reproposal kind、缺derivation、fullsource伪装initial、错source output、与R01不一致 | publication/reference/lineage/compile全部强制消费；坏候选零review推进/零GOAL采纳 |
| V10 | full proposal无goal字段实变；调序excluded；只改scope删除；clause DELETE/移section/未声明改动；漏/重复义务 | resolver allowed_forms + bounded delta；仅真正objective/excluded变更可走新形态；没有宽松全文重写出口 |
| V11 | 已compiled GOAL或EXECUTION/lateRoot；删除或破坏旧合同后伪装“没有qualified” | 已验证历史资格拒绝新full；不把absence/null当尚未批准，不自动转fresh/借旧grant |
| V12 | 旧READY/旧人门/模型approved/内部自评、未经当前GOAL或EXECUTION、关闭claim/终态 | 原ledger/qualification/compile；移去当前subject对照必须失败；零实现/交付副作用 |
| V13 | 缺产品证据、.agent/.git refs、畸形scope、弱化既有退出码/证据要求未批 | 原validator/readiness/approval；前半链修订不降验收，原拒绝序列仍成立 |

excluded用集合语义归一化只用于“目标字段是否实变”判定；含重复或畸形值仍交原validator拒绝，不借去重洗白。同SHA前缀不同base为合成碰撞/身份模型反证，不宣称实际攻击或真实碰撞。

## 3. 写入前缀与重启（共享协议，不另建failpoint平台）

| 注入段 | 期望 / 必须阻止 |
|---|---|
| 基准解析/渲染/R01写前、R01后L绑定前/中/后 | 解析失败/orphan/pending分立；零provider；D06只补既定字节；重启不选择新base |
| requalification后/R02前、R02后/真实回复前 | 前者不自动派发；后者UNKNOWN、先D07；不从R01存在推已调用或新调用资格 |
| raw结果固定、唯一outcome、publication plan、sidecar、ref各写点前后 | 原write prefixes；同字节补齐，缺R03不推进，R写失败不夸大完成；R03核base/derivation |
| R03后/L03各写点、review完成/decision、L04/compile各写点 | 真实冷子JVM消费固定完成；不重跑provider、不复用旧review或批准、不增加epoch两次 |

正常、拒绝、恢复三列分别记录每个prefix的实际状态、唯一消费者、blocked副作用。至少对两种revision形态执行既有关键前缀族与冷JVM序列；无需复制全套writer到新类。不能以“共享已有绿灯”代替新binding/derivation在该前缀的实际冷读证明。

## 4. 兄弟与registry一致性

- full / contract / terminal-delivery 三份JSON及small派生variant，inline/worker：至少V01/V02全链到当前门+授权后续合法消费者，未批零实现反例；以真实factory/loader装配，不只四个标签。
- 初始作者、NEEDS_REVISION作者、人revise、普通patch、correction：渲染不再要求模型SHA；ordinary patch示例不暗示改objective；eligible re-proposal有base ID/义务/人工门语义；late/scope-rework明确不开放full。初始无base ID不误拒。
- Registry/strict subtypes/validator/projector/sidecar reader/reference/resolver/compile同时检查：本次不增加artifact type/edge；不能保留某兄弟仅patch、另一兄弟full无派生的旁路。
- CLI/API/attention/report显示同一当前新subject/失败；历史第五例错误SHA与原FAIL保持。不得由view的显示模式给予运行/重批资格。
- old runtime / old R01 / old model SHA形态拒绝作为新发布；历史只读仍保留事故材料。不存在隐式migration/backward recovery。

## 5. 实施验收与验证预算

获实施授权后：先红（旧producer/consumer不能满足新合法输出）→上述正反/共享写点/冷JVM→兄弟与删除守卫反证→一次源码稳定的单轮全模块→发行字节/装配冻结→合并受影响CC评审。中间红必须区分生产缺陷、测试作者错误、输入变化导致INVALID；不拼接旧绿灯。

本阶段真实请求预算=0，native/provider/额外agent=0；无测token节省任务。离线测试优先复用既有族，不以固定测试数量造质量指标。需要额外服务、广泛重构或跨GOAL迁移才可满足时，停交用户。新生产测试全部NOT RUN；产品收口与真实第六例必须另获授权和当次Gate，旧170万/18次/60min不自动继承。
