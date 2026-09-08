# Agent Progress 与显式执行预算修复

## 目标与事实

SPM9 run-1788703780643 在 1207686ms 被 `--timeout 1200` 取消；53 次工具调用、35 次写操作，终止前仍有新输出。这是执行预算取消，不是已证明模型停滞。用户授权修复并继续同一样本，保留原始失败和产品文件。

## 最小边界

Execution 的现有 AgentExecutionHealth 唯一解释进展；workflow/revision/产品语义不变。总 deadline 是 operator 显式预算，不是健康判定。默认不注入 `--timeout`；`DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=0` 同义，正整数保留旧显式预算，负数/非法值拒绝。acpx 自身仍可能通过配置声明 timeout，未传 flag 不等于覆盖其配置；本次复核有效配置无 timeout 后才继续。

进展 watchdog 保持现有 600 秒无进展窗口。持续 thought/tool/output 续期；只活着的 PID 不等于模型在进展，CPU 不作为新 authority。完全无初始事件也从启动计算无进展窗口，避免撤掉默认总时限后无限静默。停滞信号只表达未观测到进展，不声称能证明远端模型已死亡。

## 生命周期与失败切面

| 前态/动作 | 执行与持久化顺序 | 后态/下一消费者 | 失败与恢复 |
|---|---|---|---|
| READY / run | 原有 run 与 prompt/binding 写入、自验、ensure、启动进程 | RUNNING；health 消费本 run 增量事件 | provider 前失败不启动；原有 typed failure |
| RUNNING / 有进展 | stdout/stream 原始事实、timeline；health 更新内存时钟 | 继续同一进程 | 不因累计时间取消；显式预算仍有效 |
| RUNNING / 无进展或明确故障 | 原有 diagnostic/timeline、terminate、cancel/close、run result | typed failure → 原有 recovery | 不自动 retry；进程重启不伪造完成 |
| RUNNING / 返回 | 原有 completion、exit、run metadata、artifact 校验 | 合法输出才路由下游 | 未完成输出不假定成功 |
| SPM9 FAILED / 本次用户授权 retry | 原有 retry CLI 写 READY、event、current，再冷读确认同 node | 新 run/session 使用新包，消费原批准输入 | 不改 ledger、不覆盖旧 run、不重建样本；部分状态停止 |

原有多文件 crash fail-closed / stale recovery 语义不变，不新增历史迁移或原子性声明。

## 不变量与验证

- Declared by：显式预算配置与现有 progress timeout。
- Proven by：进程命令、当前 execution stream/timeline、exit/completion。
- Consumed by：共享 transport（Claude/Codex/correction）与现有 result 分类。
- Enforced before：health 先判定，再停止进程；有效 artifact 前不路由业务。
- Failure route：现有 PROCESS_STALLED/provider failure/explicit TIMEOUT，人工 retry。
- Cannot-bypass：真实假进程持续活动超过 idle 窗口仍完成（三调用面）；完全静默被终止；原有 reconnect/fatal 与显式 deadline 用例；长时间结构化 thought 连续健康。

变更不含 workflow/schema/fork/模型/权限/产品源码。历史 timeout 文字保留，catalog 兼容旧证据。新行为默认生效，显式正数旧配置维持原预算含义；继续执行必须改为 0，不能只换 JAR。

## 继续执行口径

原 frozen run 已发生 timeout，不能再声称未经中断的 fresh PASS。本次为用户授权的 fix-and-continue 阶段：新 JAR SHA 与 diff、测试、retry note 单独归档。保留合同/设计/计划、已有 dirty 产品文件，不修改验收标准或恢复其他样本。不把最终完成外推为无回归正常路径。

验证：新增聚焦 29/0/0/0；full `mvn package` BUILD SUCCESS，162 XML = 1403/0/0/0；diff-check clean；新 JAR Doctor exit 0。
首次新增进程 fixture 的普通 `working` 行不属于既有 activity 分类，测试正确失败；改为 acpx 真实 `[thinking]` 形态后通过，未放宽 production 识别器。
共享 Claude/Codex/correction 装配、显式 deadline、静默/重连/故障/session closure、run result 超时分类已走查。Boundary Review: PASS（实施方自审，未冒充独立评审）。
源码基线 HEAD=6784b662851eb82ff7b0397ba656ee70089a854a，加归档 adapter diff SHA=06d87847e74f2a9aee9bee6d961e55261154df3d93ac571e069aac55b7142a50。
新 JAR SHA=937dacee97665e22b13ae089d3c670abedb15ebd25b2f252ebb6f22743d70e80。
测试、patch、Doctor 原始输出归档 `output/real-cases/20260906-native-codex-adapter-preflight/`。E2E NOT PROVEN，待同节点继续。

自然继续结果：SPM9 同 node-042 新 run-1788706182287 durationMs=1623714，SUCCESS/IMPLEMENTED，旧1200秒边界后仍正常工作并返回。
新节点消费有效 sidecar 后进入自测，未跳过 artifact/authority 链。随后两轮自测在缺测试依赖、沙箱禁网的实际环境阻塞处停到 human repair gate-053。
本修复：默认无总deadline/显式预算/持续进展边界 ENFORCED，当前自然长执行完成 PROVEN；完整产品 E2E NOT PROVEN。
