# Phase A：人工发起的一次执行网络授权

状态：IMPLEMENTED / INDEPENDENT DETERMINISTIC REVIEW PASS。目标属于 P0 执行恢复；不是自动捕获或完整网络安全工程。
用户已授权实施、独立 agent 评审、通过后进入 Phase B；未经验证不恢复 SPM9。

## 最小设计

- ledger 拥有 operator grant；request 是用户输入的 canonical 域名、note、目标 Claude implement-profile 节点定义。
- 仅在单 active 的 WAITING_FOR_HUMAN gate 或 WAITING_FOR_RECOVERY FAILED/BLOCKED agent 接受授权。授权命令不改变 workflow 状态、不代替业务决定。
- gate 按既有 approve/revise 声明边继续；失败节点按既有 retry 继续。grant 的引用沿实际 inputTransition 传递，不重开历史节点，不另造回跳边。
- 目标必须在该 gate 到下一个 gate 之前可达；失败节点只能授权自身。中途遇到另一 gate 或目标已经执行，引用不再向后传播。
- 独立 grant 文件必要性：目标 execution 尚未存在，源 gate 与目标 node 不是同一对象；已有 WebSearch 的 node.result 单次授权不能跨 context/implement 传递。仅新增此领域记录，不做权限平台。
- adapter 在 provider 前对目标 execution claim 一次；原子写入 claimed node/run 后才生成 policy。claim 后启动失败也消耗授权，不自动重用。prepared-only 不消费且不应用授权。
- 新 policy 使用既有 allowed_domains/SDK managed 通道；不改 schema、无全局授权、无 wildcard、无 WebFetch 自动批准、无 unsandboxed escape。
- 自测可解析已批准依赖，不得据此升级或更改依赖声明；这是正常测试准备职责，不新建安装阶段。filesystem 拒绝仍停止，不旁路宿主权限。

## 写入与失败切面

| 前态/action | 持久化顺序 | 下一消费者与失败处置 |
|---|---|---|
| 等待态 authorize | 全部前置校验 → 原子写 grant → 源 node inputTransition 写 ref | 中断仅可能留下未路由 grant；没有 ref 不消费；不自动继续 |
| gate decide / failed retry | 既有路由与写入；ref 按实际边传递 | 仍以 current/node/gate 为准，partial state 不猜测修复 |
| 目标运行 claim | 校验 ref/source/target/definition/current → 原子 claim → policy 写入与 SHA 绑定 → provider | claim 前失败零放权；claim 后失败烧掉该次授权，新的人工授权才可重试 |
| decline/cancel | 保持原 workflow 等待；取消记录原子更新 | 之后 ref 不放权；不将业务 gate approve 当作网络批准 |
| target 完成/失败/abort | claim 已消费；后继不传 ref，终态不可 claim | 不跨新 run 继承；进程内 correction 属同一次 execution |

旧无 grant 的 workstream 按既有拒绝策略运行；损坏 ref/grant fail-closed，不兼容猜测。记录与 node 的写间崩溃允许停止，不建设 R1 自动恢复。

## 验证与通用性

生命周期测试：gate→context→implement→指定 self-test；FAILED→retry；grant 只改变目标执行 policy；冷读、重复 claim、wrong target、cancel、terminal、missing/tamper、跨 gate 与 profile sibling 拒绝。
CLI/API/console 共同消费 ledger view；scheduler 不创建授权；通知仅投影既有等待，不宣称自动发现网络阻断。
adapter 假 transport 检查实际 policy、相同 execution 的 correction、后续 retry 不继承；fork 现有域名输入通道保持不变。
通用性：域名/目标来自用户与 definition，没有 OKX/Maven/产品路径特判；仅 Claude implement capability，Codex 保持不受影响。
完整 invariant：Declared=operator grant；Proven=grant+claim+policy SHA；Consumed=adapter producer/fork；Enforced=provider 前；Failure=停止/无放权；Cannot-bypass=上述完整序列测试。
独立评审 PASS 前不进入 Phase B。Phase B 只核实运行时可信请求入口，不借机重开网络平台。

## 独立评审后的必要修订

首轮独立评审 FAIL（1 P1、3 P2），未进入 Phase B。
P1：原 policy 可写授权和 source node，不能保证消费事实不被执行者回滚。
所有 E1 Claude execution 现在保护本 workstream 的 nodes 与 execution-authority 两目录：
producer 输出 sandbox deny_write，fork 将同一 denyWrite 投影为 managed Edit 绝对路径及子树 deny，
覆盖 Bash 与内置文件编辑工具；不禁止 artifacts/report 等正常交付路径。
这是保护本授权链的必要修订，不新增 env/proxy/权限平台。SDK 上级 admin 丢弃 parent settings、
任意外部工具修改 ledger 不在既有受支持 E1 环境承诺中，不把 deterministic 投影冒称跨环境安全证明。
真实控制文件保护须单独最小 slice：Bash/Write/Edit 拒绝、普通 artifact 可写；不恢复产品样本。

P2：fork 原 secret-like 启发式误拒 task-api.example.org，现严格对齐 exact DNS 校验，
仅对已校验的 allowed_domains 豁免该启发式；其他字段守卫不变。
fork 生产增量仅现有 policy loader，测试和身份同步随行；新 patch SHA
`2313fe2d53e573dc0227a453fd1c4cbe6107373472a2027bb7ed3e2952038b63`。
其余 P2：历史 claim 由记录的 SHA 验证，不再依赖当前 source 指针；消费仍要求当前指针。
网络 view 对 partial open-gate、缺失/损坏/null-state 返回 INVALID 并禁用动作，不拖垮 workflow 查询。
新增 replacement 冷读与 gate 写间失败测试；取消、重试和业务 gate 不放宽。

依据：[Anthropic sandbox 文档](https://code.claude.com/docs/en/sandboxing)、
[permissions 文档](https://code.claude.com/docs/en/permissions)，2026-09-07 核对；
SDK 0.3.232 sdk.d.ts 的 managedSettings/permissions.deny 为静态契约依据，非运行效果证明。

## 收口记录

- 独立 agent 首轮 FAIL → 修订 → delta 与最后测试/展示增量复审 PASS，无剩余实现阻塞项。
- 最终 `mvn -q package` exit 0；164 份 surefire XML 聚合 `1413/0/0/0`。
- fork Node 22.21.0（受支持版本）`pnpm test` 完整 build + tests：`988/0/0/0`。
- 初次沙箱内进程/端口测试受宿主权限阻断，改为所需权限后复跑；身份更新暴露的两个旧 fake build-info
  已改为引用 ReviewedAcpxBuildIdentity，未改冻结历史证据、未放宽真实 identity 校验。
- patch reverse-apply（排除生成 stamp）通过，manifest/Java/configure/generated/binary 自报一致；
  新 JAR Doctor 的 E1 identity 校验通过。JS 语法、shell 语法与 diff-check 通过。
- JAR SHA：`cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70`。
- prepare-only→真执行→retry 序列已证：准备不消费、不放权、不调 provider；指定执行消费，后续 run 不继承。

命令入口为 `recovery network --workdir <workspace> <workstream>`，只读返回目标与能力；
授权另加 `--target <view 中的节点> --domains <逗号分隔域名> --note <理由>`，取消用 `--cancel`。
这些命令不运行 workflow，之后仍按既有 gate/retry 语义继续。

成熟度：operator grant 路由/单次 claim/策略绑定 DETERMINISTIC ENFORCED；
新控制文件真实 sandbox slice、授权域名实际访问与产品恢复 E2E NOT PROVEN。
Phase B 仅静态入口核实，独立复核同意停止生产自动捕获：当前没有已证明的 typed 沙箱域名拒绝入口。
未调用 provider，未恢复 SPM9，未自动提交混有既有 deadline/progress 改动的工作树。

## 真实效果补证（覆盖上面的阶段状态）

2026-09-07 用户授权先验证 A：唯一真实 slice 已执行并通过独立证据复核。
真实 grant→gate/context→claim→policy→transport/fork/SDK；授权域 200，未授权域明确
403 blocked-by-allowlist；双控制目录 Bash/Write/Edit 拒绝，普通 artifact 三类工具成功；
11 控制文件摘要不变，cold claim 校验与新 run 不可复用，单 prompt、正常 session close。
详见 `../testing/capability-proofs/20260907-phase-a-network-grant-effect-result/README.zh-CN.md`。
授权域实际效果与本次控制目录保护提升为冻结环境 RUNTIME SLICE PROVEN。
没有新 production 修复、没有恢复 SPM9；产品依赖完整解析/自测/E2E 仍 NOT PROVEN。
B 保留为自动请求入口尚未验证，不声明理论上不可获取；不阻塞 A 的产品使用验证。
