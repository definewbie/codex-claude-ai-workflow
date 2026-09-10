# 补证据源码基准

目标：P0 续作正确性。仅开放人工 collect_evidence，不新增权限或网络平台，不执行真实样本。

## 最小链路

批准前以已批准合同解析器确定精确路径，捕获 Git tracked、非 ignored untracked 与合同路径的原始内容。
包括删除、类型和执行位；忽略 .git/.agent 内部状态及未纳入合同的 ignored 构建输出。
基准存于 resolved human gate node result，下一实例的 machine-owned transition 引用其身份。
CLI/API 使用同一 runtime approval service；普通 ledger resolve 不得裸执行 collect_evidence。
补采仍用既有 self-test 节点；目的地由 JSON 声明，禁止自动授予写实现任务。

## Transition

| 时点 | 行为/失败结果 |
|---|---|
| waiting gate | 锁内检查 current gate、声明动作、非空 note，qualified contract 必须存在 |
| 批准前 | 捕获 bytes 与合同 SHA；失败零 gate resolution |
| 批准 | 沿既有 gate resolved -> node completed -> successor/current 写入；基准随 node 持久化 |
| 部分写入失败 | fail-closed，禁止推测旧基准或重新批准已 resolved gate；不新增 crash rescue |
| 执行前 | 从 gate 重读基准，重新捕获比较；不符不得调用 handler/provider |
| 执行后 | 包括异常路径，比较源码与批准事实；不符返回 typed failure，不发布可用 sidecar |
| 下游 | 同步与 worker 共用检查；正常路径及再次补证据的 loop 保留原基准，转入产品修复的 loop 才清除；后续 human gate 的新裁定重新确定续作语义 |
| retry/cold | 同 occurrence 重新读取原批准基准，禁止重新 capture 为新的 authority |

真实 SoT 为人工裁定的 node result；比较工具不是权限 owner。检查只承诺结果准入，
不宣称 OS 禁写、不检测 edit-restore，不自动回滚产品。无法证明的符号链接/子模块失败关闭。

必要测试：原样通过、内容/增删/执行位改变拒绝、ignored 合同路径不可漏、冷读仍旧基准、
批准不完整拒绝、handler 前零调用、handler 返回与异常后 drift 均失败、worker 同规、四 workflow 同源。

## 实施与完整链自查

- Declared by：四个 workflow 的 implementation repair gate 显式声明 collect_evidence，唯一目的地为既有 self-test；scope violation gate 不提供此动作。
- Proven by：批准时将合同 ref/SHA、HEAD、index SHA、路径集合与原始内容摘要写入 gate node result；transition 只引用该 node 身份，不复制基准。
- Consumed by：CLI/API 共用 EvidenceCollection.resolveGate；inline/worker 共用 EvidenceCollection.execute，ArtifactContractValidator 在发布前再次检查。
- Enforced before：执行前变化阻止 handler/provider；执行后变化阻止可用输出发布及后续验收路由。正常后继与再次补采继续携带同一批准身份。
- Failure route：EVIDENCE_COLLECTION_SOURCE_INVALID / FAILED，进入既有 recovery；不重新批准、不回滚源码、不以新的 capture 覆盖旧基准。adapter 原始运行记录可能成功，workflow node 的 typed failure 才是本边界判定。
- Cannot-bypass：真实 runtime inline/worker 正反序列、异常后变化、冷 retry、标记删除、批准文件篡改/删除、直接 publication、批准写入中断、补采/产品修复两类 loop 及四 workflow 声明测试。

未新增 profile、permission/sandbox、网络授权或通用执行平台。prompt 只作任务提醒，检查由 runtime 与 publication 消费，不能靠模型自述通过。

范围与限制：基准覆盖 tracked、非 ignored untracked、合同指定路径及其删除/执行位；不覆盖合同外 ignored 输出，不检测先改后恢复的过程，不是操作系统写隔离。符号链接/子模块不支持并拒绝。部分 gate 写入失败只保证停止，不承诺自动恢复。人工上传 submit_evidence 不属本增量。

本轮自查由主 agent 完成，不冒充独立评审；未启动 provider 或 real-case。最终回归结果见下方收口记录。

## 收口记录

- 全 reactor：`mvn -o test -q` 退出 0；176 份 surefire XML 聚合 1465 tests / 0 failures / 0 errors / 0 skipped。日志：`/private/tmp/dual-evidence-final-reactor.log`。
- 其中新源码捕获测试 3 项、生命周期序列测试 7 项，另有 CLI/API 拒绝测试与 console 声明动作投影测试。序列覆盖 inline 与 worker，未依赖真实 provider。
- `node --check dual-server/src/main/resources/static/console.js` 通过；`git diff --check` 通过。
- 既有进程树测试需要本机进程枚举，因此最终离线 reactor 在获准的本机执行权限下运行；未跳过该测试。
- 成熟度：人工补证据源码基准与准入检查 ENFORCED，离线序列证明通过；NATURAL REAL-CASE NOT PROVEN。人工材料 submit_evidence 仍未实现。本轮未提交 git。
