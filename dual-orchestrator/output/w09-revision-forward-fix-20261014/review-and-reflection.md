# 主 agent 自查与边界复盘（不是独立 CC 评审）

Analyze → Modify → Review → Reflect → Analyze 在本批重复执行。目标 P0：让首 GOAL 前合法返修能回到独立复评与当前人工门，去掉模型 SHA 转录负担；不测 token 节省、不改变后半链验收。Definition/registry/route JSON 不变，Instance 的 iteration/gate/history 仍归 ledger，Execution 完整基准由既有 R01 固定。

## 自查发现与纠正

- publisher 校验不能替代 merger 的内容核验：自查新增 merger 读精确 ref 并比完整 SHA，不仅比模型 ID；常驻 `exactIdCannotHideChangedProgramBaseBytes` 与删除守卫反证验证。这是受审“四处核验”中的消费义务，不新增权威来源。
- 当前审批之后的事件不能撤销此前合法 re-proposal。资格按作者 occurrence 的历史 route 截点检查；真实冷子 JVM 在后来 GOAL/EXECUTION 批准后重读初始/full/patch 三份历史，不比较 events.jsonl 整体 SHA。
- 缺 R01、固定材料改变属于本地身份不可核，不强行期待 WAITING_FOR_RECOVERY。dispatch 前拒绝与 publication/L03 拒绝分别断言零调用/零发布；测试作者最初混淆了这些失败时点，已纠正预期，生产 guard 未放宽。
- 旧离线夹具没有 R01 或 source 是 `{}`，不足以证明新的 revision lineage。共享夹具改走真实 capture→prepare；compile 正向改为真实作者→复评→当前人门→compile，初始语义夹具使用真实 wire 内容。没有生产 fallback 来接受缺 R01 的旧 revision。
- 新 fault tests 必须实际命中新字段所属的 R01 bind / R03→L03 前缀；以 prepared/completion 已落盘作为选择条件，复用既有 test-only failpoint，未加生产 hook。

## 中间运行口径

`red-wire.log` 是旧生产不能接受新 wire 的两项先红；compile-01、focused-02/03/05/06/07/08/09/10/11 的编译/夹具/错误断言问题均不包装为生产缺陷证明。reactor-01 为旧夹具失败且运行中源码有变，不能作为当前版本全回归凭证。counterproof-run 首个分类标记大小写错误是检查器错误，原日志实际已捕获预期的 reference 断言失败；结果有显式说明，不重写日志、不改生产结论。

## ROI 与边界

生产变更仍在 runtime 契约/准备/消费者及 adapter 作者接线；仅增加一个局部 `ContractRevisionBinding`，装在既有 R01，不增加节点/artifact type/锁/存储/服务/DSL/跨 GOAL 在线迁移。测试使用离线 transport、真实临时 Git 和冷 JVM，证明领域接线，不冒充客户端遵守新提示词的真实能力。其余身份字段、完整生命周期真实成功、后半链与 token 改善均不随批成立。

- 全模块运行期间进一步发现 missing-R01 负例重序列化了 sidecar，可能混入其他 SHA 拒绝。主动终止该次回归（exit 130、归档为 reactor-interrupted），保持原 sidecar 字节只删除 R01，再以新稳定输入重跑；该次不作发行凭证。

- reactor-final-attempt-02：runtime 812 项中一项旧展示断言失败，其余已执行测试未失败，仍不能作全模块绿灯。篡改 root 后新的 R01 输入核验已让当前 candidate reference 不可用，真实 context 先在 required subject 入口拒绝，未到旧的展示投影错误码。只迁移该测试到精确的既有更早拒绝分支，同时显式检查 reference 不可用、零 context/ref 输出、包字节不变与零新模型调用；不改生产规则，也不拼接定点绿灯。

- attempt-03：adapter 的两个提示词正向夹具 raw 为 `{}`，新来源校验正确拒绝；不是模型或生产失败。终止该轮（exit 130），补成真实 typed initial 原始输出，保留提示词内容/重定向及篡改负例。生产代码未变；定点验证后仍重新执行单轮全模块，不拼接。

- 剩余模块兄弟搜索首次使用两个不存在的旧模块名，命令 exit 2，属案头检查错误；更正为真实 dual-adapter-git/dual-flow-liteflow 后核对，CLI/server 未发现同型 proposal 夹具，server 的空 raw 属非 proposal 样例，不用此次接线泛化改写。focused-15 的 AgentPromptRendererTest 39 项通过，仍不是全模块凭证。
