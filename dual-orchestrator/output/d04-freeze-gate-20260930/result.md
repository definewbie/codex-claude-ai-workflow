# D04 合并 Gate 结果（2026-09-30）

本轮按用户明确批准执行已冻结脚本，**3 个 native 顶层会话、3 个原生子任务、20 个本地 Responses 请求，零真实模型调用/真实模型 token，无重跑**。三个 runner 的运行/观察/收尾合计约 28.94 秒（不含场景间人工读证据），低于批准上限。未修改产品、上游 adapter 或引擎；剩余额度不自动用于追加实验。原 Gate/plan 保留批准时字节，其 NOT AUTHORIZED/NOT RUN 是准备态，实际授权见 authorization.json，实际执行见本文件及 summary.json。

## 实际结果与边界

| 场景 | 结果 | 原始证据与局限 |
|---|---|---|
| read_only | **PASS：真实子任务权限切片** | request-02 的 /root/probe 与 read-only；request-05 的随机读取匹配，产品/外部 control/工作区内链接绕出三次写均由 OS 拒绝，never 拒提权；受保护 hash 未变。request-07 的 child 状态为 completed |
| implement | **PASS：真实子任务权限切片** | 子任务为 workspace-write，读取随机值并真实创建 legal.txt；外部 control 与工作区内链接绕出均拒写，never 拒提权；request-07 记录 child completed。不是精确业务 scope 或实际产品内 control 隔离已证明 |
| cancel | **取消触发与观测有效；全部底层活动停止仍 UNKNOWN** | active 已出现且 late 未出现后触发；request-05/06 中 interrupt 的 previous_status=running，后续 list_agents 为 interrupted。观察期内无 late；child 未回传该工具的终止结果，顶层 CLI 也随后退出，不能归因于 interrupt 单独保证停干净 |

前两个脚本末尾的 `Script failed` 来自预期的 never 提权拒绝，位于最后一个负例；前面的真实合法操作和写拒绝证据齐备，不属于协议测量失效。没有靠模型/桩最终文本判断成功。子任务正常 completed 中的文本是桩提供的测试内容；有效依据是原生状态迁移、对应 child identity 和此前同步工具实际退出。

原始入口：[read_only](run/read_only/result.json)、[implement](run/implement/result.json)、[cancel](run/cancel/result.json)。前两场景 continue.json 记录同 Gate 内作者核对，未跳过失败后重跑；取消解释单独保存在 [observation.json](run/cancel/observation.json)。CLI 的顶层 events 没有给出完整子工具细节，本次关联使用原生向本地响应端点发出的真实工具结果，不能假定当前 ACP 消费者已全部拿到。

## 停止判断及 D07 接线

本轮证实“正常已完成”与“执行中被中断”有不同原生事实，取消不是无条件全部 UNKNOWN；但 interrupted **不等于** 全部 OS 操作停止。取消样例中没有收到子工具最终结果，又存在根进程退出这一共同因素，因此不提供“interrupt 已强制清除所有后台活动”的 PASS。有限时间无迟到写和按 fixture 路径查询无残余进程都只是旁证，不是全后代普查。

D07 可采用的设计边界：正常同步工具结果齐备且身份/输入/发布合格时可进入正常完成消费；仅有 interrupted/根退出/收尾状态时，外部副作用仍不明，禁止自动 retry/resume，保留现场并走原有裁定。不能因本次有 completed 状态就忽略今后的未终结工具 session 或异步任务。D05 的稳定验证对象与证据消费义务保留，native 结果不取代产品组合证明。

同版 codex-acp 的接线定点核对见 [adapter-observation-notes.json](adapter-observation-notes.json)：已有子线程订阅、子事件路由、asyncTasks reconcile、thread status 更新等入口；**finishOutstanding 会在等待超时等路径将未决子项标 failed**。这是适配器收尾/投影行为，不是 OS 已停止事实。D07 必须区分原始 native 事件与 adapter 推导收尾，不得把后者洗成可信全部停止。这里明确的是已有接线位置和必须消费的来源，不宣称已实施桥接，也不建立逐子领域账本。

## 冻结距离与成本判定

- 子任务权限关键未知已有两角色正反切片，不再重复这组实验；父/子继承与共享强制点静态证据仍可复用。不能外推任意参数变化、旧 session、跨任务借权或任意工具，实际开放入口依原接线义务处理。
- 完成/中断状态生产者存在；“取消后底层副作用未知时交 D07，不自动续作”的方案已有观察基础。是否足以冻结，须由 CC 判断证据与既定要求是否相称；不为取得“永无残留”再扩大实验或新建监督器。
- [接线/成本核对](binding-and-cost.md)继续有效：两 provider adapter、acpx session/queue 与 Java run 传播的改动不可省；原生实验不是接入通过。实际产品内控制目录与原生可写根的对应仍需落实到现有机制，不能把本次 workspace 外控制样例当答案。若这要求存储迁移/新隔离平台，必须按 §8.6 止损，不能直接开工。
- 本轮不宣布 D04 冻结，不虚构总实施工期/费用。独立评审应判断：现有证据是否足够冻结受限方案；若不够，指出是具体接线设计缺项还是能力缺口，不默认另开一轮原生实验。

## 六维记录

| 维度 | 本轮证据及剩余边界 |
|---|---|
| Declared by | 详设 §8.21–§8.23、冻结 Gate、用户本次批准；仅试验用临时文件与原生子任务 |
| Proven by | 两角色实际工具正反；正常 running/completed、取消 running/interrupted 的原生事实。未证明全后台停止或产品接入 |
| Consumed by | 本轮 native 分派/OS sandbox/never policy 已实际消费；作者判读原始结果。未来 transport/run→D07/D05 消费尚未实施 |
| Enforced before | 已测禁止写由实际操作入口拒绝，提权由 never 拒绝；缺绑定的产品 dispatch 拒绝及未知结果拒绝续作仍为实施义务 |
| Failure route | 预期负例保留原生原因；取消保留 interrupted 与底层 UNKNOWN；不能改标 JSON 缺失或认为安全重试 |
| Cannot-bypass test | 子任务合法读取/合法写与越权写/链接/提权成对；父 PID/最终文本不作为停止证明。产品全入口与失败写序仍回原 W 项 |

## CC 评审提示词

请对 dual-orchestrator 的本轮 D04 合并 Gate 结果做独立冻结条件评审，遵守 AGENTS.md。入口 output/d04-freeze-gate-20260930/result.md，同目录包含 Gate、批准身份、脚本、接线/成本说明及原始 run；结合详设 §8.23/§13，自行重建父子权限、完成/中断、结果采纳与恢复链，不预设作者结论。重点判断原始证据是否支持声明、哪些冻结条件仍不满足、未决接线是否会要求超出已有 adapter 方向；区分 native 原始事实与 adapter 收尾推导、业务 scope 与原生上限，说明失败切面及六维缺项。给出分级发现和可否冻结的结论；若需更多工作请说明它改变哪个决定，不默认追加实验。只读，不启动 native/provider/子任务或额外评审 agent，不修改文件、不进入实现。
