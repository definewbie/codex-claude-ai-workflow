# E2 Typed Design Scope Checkpoint

状态：TYPED DESIGN SCOPE ENFORCED / REAL-CASE NOT PROVEN / INDEPENDENT DIFF REVIEW PASS。
基线：`306a555d`。独立评审已允许选择性提交；不调用 provider、不恢复任何 real-case。

## 目标与实现

将设计的拟变更文件从自然语言变为 `design_result.design.planned_files`，在有效 sidecar 发布前，按 E1 唯一 qualified TaskContract 校验精确路径与 operation。设计不拥有授权，不把 planned files 混入实际 `changed_files`。

- `DesignResult` 使用既有 `TaskContract.ScopeOperation`，不增加 failure、revision responses 或新审批机制。
- `DesignScopeValidator` 复用 canonical path 与 qualification resolver；非法路径、重复、缺字段、越界、operation 不符、未批准或漂移均拒绝。
- 架构 context 仅接受 routed refs 中唯一、lineage 有效的 typed design occurrence；同 ref 去重，多候选不按顺序猜测。
- sidecar、required review subject、EvidenceManifest 与 memory reader 同步；required subject 完整呈现 planned files，memory 只保留摘要与 decision。
- 四份 workflow 仅修改 design producer 的 expected artifact，不修改图、human authority 或 recovery。

## 实现者通用性判断

结论：在现有注册 design stage、仓库文件交付域内通用，不是 OKX 特判。

规则只依赖已批准 scope、规范路径与 operation；production 不识别业务名称、技术栈、目录角色或扩展名。测试把同一 publication/context 链用于文档转换与静态站点两类目标，以及一致重命名后的路径；中文含空格路径保真，CREATE 文件无需已存在，每组均附越界反例。

这证明的是业务标识替换不改变机械规则，不证明两类产品已经被实现。适用边界仍是现有 registry 的 design 节点，不外推任意改名 custom node、非文件副作用或业务正确性。空 planned list 允许通过形状校验，完整性由 reviewer 判断；正文与声明是否一致亦属语义评审，E2 不做自然语言路径推断。

## 端到端不变量

| 维度 | 事实与守卫 |
| --- | --- |
| Declared by | typed design 的 planned_files；权限来自 E1 resolved gate + compile lineage |
| Proven by | strict envelope、DesignScopeValidator 与既有 sidecar/lineage 校验 |
| Consumed by | ArtifactContractValidator 发布；architecture context 唯一 routed subject；manifest 与 memory 只投影 |
| Enforced before | 有效 design sidecar 发布与后续 architecture reviewer 调用之前 |
| Failure route | typed artifact contract violation；缺失/歧义 subject 拒绝 context；不回退 generic/Markdown |
| Cannot-bypass test | 真实 WorkflowRuntime 正反例、跨业务 publication→reader、篡改/删除/歧义，以及关闭守卫和删投影的突变测试 |

运行链测试仅以 fake handler 替代 provider；publication validator、context 与 runtime/ledger 为真实实现。缺 approval 的该 slice 检查发布守卫，不冒充 adapter provider 前资格检查。产品 implementation 写入边界仍由既有 scope enforcement 拥有。

## 验证记录

- failing-tests-first：新增 prompt 契约测试先因 generic design 输出失败。
- 聚焦 adapter handler + memory：`57/0/0/0`；memory 用真实 validator 发布的 sidecar 验证，不手写第二份有效工件。
- 关闭 design scope publication guard：8 项 TypedDesignScopeTest 中 5 项失败；恢复后继续验证。
- 删除 required subject 的 planned files 投影：8 项 scope + 58 项 context 共 66 项中 2 项失败；恢复后继续验证。
- 全模块 `mvn test`：BUILD SUCCESS；155 份 surefire XML 结构化聚合为 `1348/0/0/0`，包括 server 的 65 项。比 E1 基线 1337 净增 11 项。
- 全模块 fresh package：BUILD SUCCESS。新 JAR 对 full、contract、terminal-delivery 与 small-plan-human-review-experiment 四份 workflow validate 均 exit 0。
- `git diff --check`：clean。测试需宿主进程信息权限，完整 reactor 在获准的非沙箱环境运行；不涉及真实 provider。

回归过程中旧 generic contract 预期和四个 adapter 正例缺少批准 fixture 被新规则拒绝；仅更新测试前置，未削弱 production。运行链正例发现 approval fixture 追加事件后未同步 current cursor，修正 fixture 的账本一致性，并保留合法链路必须完成的正向断言。

## 整体边界与下一步

E2 只强化“拟变更文件不得越过已批准范围”。E3 尚需 human gate 的唯一 candidate/version 绑定、active obligations 与 responses 展示及修订连续性；E4 尚需下游 plan 消费收敛和 canonical-plan 减法。本次没有顺带实现这些机制，也不能以 E2 的局部闭环替代 E1-E4 整体 semantic convergence。

独立 diff review 已通过（P0=0/P1=0/P2=2），独立复跑 full reactor `1348/0/0/0`、四 workflow validation 与 diff-check 均一致。评审未重做需改代码的突变测试，只读核实其结构，不能记为第二次突变实跑。

评审文字勘误：TypedDesignScopeTest 实为 8 个 @Test，而非报告所写 9 个；155 份 XML / 1348 总数无误。

P2-1 纳入 E3 必交付项：human design gate 必须展示唯一 candidate 的 planned_files、ref/SHA 与 obligations/responses，不得再次悬空。
P2-2 为 projector 前置条件知会：operation 非空由发布前 shape 校验保证；本轮不新增绕过 validator 的直接发布入口，也不以 fallback 掩盖无效 operation。E3 若改变发布入口须重新检查该前置条件。

下一步选择性提交本 checkpoint，排除旧 S2 recovery Gate；随后起草 E3 计划，先自行评估整体消费链与通用性，再独立评审。真实业务效果仍为 REAL-CASE NOT PROVEN。
