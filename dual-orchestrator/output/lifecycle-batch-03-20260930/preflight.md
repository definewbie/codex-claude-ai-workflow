# B03 开工核对：提交机制与授权事实生产的批次依赖

2026-09-30。状态：已建立开工基线；产品实施尚未开始。用户已授权此前描述的 B03（W05/D06 本地提交、统一会话、必要 D07、D08 构造迁移）。本核对发现此前描述没有交代 D03 生产链与 D07 消费链的实施依赖，因此在扩大范围或降低本批完成口径之前停止产品修改。

这不是撤销 D01–D10 的设计冻结，也不是证明 D06 不能先开发。问题是**本批究竟完成哪条合法用户链**，不是新增设计机制或安全实验。

## 1. 基线与当前事实

- `before-head.txt`、`before-status.txt` 保存当前 Git 身份与未提交清单；`before-source-sha256.json` 固定 593 个 source/resource 文件；`before-source-text.json` 保留可文本比对的内容。包含已有 tracked/untracked 源码，不以 HEAD 代替实际工作区。
- B01/B02 的修改和证据保持原样；本轮没有覆盖、还原或提交代码，没有修改测试源码。
- 本轮只作源码与冻结文本核对，没有运行测试、native/provider、额外 agent 或历史恢复。下面是静态证据与控制链推演，不能称运行实证。

## 2. 已确认的生产／消费依赖

| 位置 | 当前事实或已冻结要求 | 对本批的影响 |
|---|---|---|
| 详设 §7.1；freeze-details §7 | task/attempt 要绑定 GOAL/EXECUTION refs、批准 occurrence、输入 epoch；L02/L03 在同会话重核 | 不能只在 execution 加几个空字段便称 D07 身份闭合 |
| freeze-details §4 | 同一 TaskContract v2 DTO 分 GOAL/EXECUTION，epoch 表示授权修订；编译、资格、作者/复评、定义和消费端须共同迁移 | 这些事实由 W03/D03 生产，不能由 W05 存储层自行推导批准 |
| TaskContract.java:9 起完整 record | 当前只有 version、scope、字符串验收项等旧字段，无 schema_version/phase/epoch | 新授权事实尚无正式合同生产者；这不意味着现有系统毫无身份校验 |
| ContractCompileHandler.java:40–44、107–131 | 当前固定 approved_task_contract_proposal 入口，输出稳定 task-contract.json 及 SHA；未按 phase 生成 GOAL/EXECUTION | 不能把同一旧合同的 SHA 分别填成两个 phase 来满足新契约 |
| AuthoredStageLineageResolver.java:146–179 | 已有批准合同 ref/SHA、作者/复评谱系；以节点创建历史固定 semantic input epoch | 这是真实可复用的已有保护；它不等于 v2 合同授权 epoch，不能改名冒充已完成 D03 |
| NodeExecutionDocument.java:11 起完整 record | 有 node/task/workflow execution、run refs 与实际结果，尚无冻结身份/新授权引用字段 | 正确 owner 已存在；应扩展既有 execution，不能另造 authority |
| AgentWorker.java:106–114；WorkflowFileLedger 对应 writers | claim/task/node 分步落盘，随后执行与 finalize | D06 合并写集确有必要；但合并字节不会自动产生缺失的 D03 授权事实 |
| freeze-details §6.1、§6.2 | 同一会话一致读、完整 after-state、同版补齐；外部过程在锁外，完成时重验 | D06 物理协议和 D07 业务资格是两种验收，不能互相代替 |

具体源码 SHA 与锚点见 `source-checks.json`。freeze-details 中 D04 的旧历史要求由详设 §8.25 覆盖；此处只引用仍有效的 D03/D06/D07 条款，不重新引入已废弃的沙箱要求。

## 3. 合法用户链与失败切面（验收推演，NOT RUN）

| 阶段 | authoritative fact / owner | 下一消费者与必须成立的结果 | 失败或重启解释 |
|---|---|---|---|
| 目标批准后设计／计划 | 已批准 GOAL subject；合同/人工门 owner | 只读设计和独立复评消费正确目标，精确文件仍可提案 | 缺或错 subject 拒绝；合法新文件提案不能因 GOAL 无写清单被全拒 |
| 设计／计划复评后执行批准 | reviewed candidates + EXECUTION 合同及批准 occurrence；原 decision/contract/gate owner | 同范围合法 implement 取得资格；reviewed 不冒充 execution_authorized | revise 走作者→复评→新 occurrence；旧批准不能替代新合同 |
| 排队、领取、准备 | task/execution 唯一 claim + 已验证输入引用；L01/L02 与 run PREPARED | 当前批准、epoch 与运行身份相符才可进入外部 dispatch | 每次本地写前缀依 D06；pending/BUSY 不冒充 RUNNING 成功 |
| 外部调用与完成 | run owner 的 dispatch intent / 不可变真实完成引用 | L03 重新核验 claim/epoch 后采纳当前结果 | 外部不明不重调；仅 PID 消失不证明未执行；旧 epoch 完成保留历史但不给新输入成功资格 |
| 路由、查询、恢复 | 已固定的 node/task/execution/后继/event/current/instance after-state | 一致读；同版只补已固定的本地字节，零 provider/Git/重新批准 | pending 补齐不重新生成路由/事件；旧版本或第三种字节 INVALID；外部未知走原人工裁定 |

有判别力的对照是：同一合法授权的完成在本地写中断后可继续；已换授权的旧完成不能接到新执行。只让两者都拒绝，或只用合成 epoch 证明比较函数，均不足以证明产品合法链。

## 4. 判断及可行替代

**确认的缺口是批次收口口径没有处理生产／消费依赖。** 尚不能由此断言“D06 必须等所有 W03/W04 全部完成”，也不认定需要新平台或新 authority。

| 批次组织选择 | 能完成什么 | 成本／限制 |
|---|---|---|
| A：把 D03 的合同身份生产与相关消费者纳入联合实施 | 从目标批准、设计/计划、执行批准到 claim/完成/恢复，使用真实新格式事实证明前述链；新字段由其领域 owner 生成 | 扩大此前 B03 所述 W05 范围：涉及 TaskContract/validator/compiler/artifact writer、review decision/lineage/qualification、四变体与投影；EXECUTION 验证出口必须与已冻结 D05 对接，不能省略。须明确获准后才动这些产品代码 |
| B：先交 D06 可独立验收的完整本地转换，D07 新授权身份闭合后置 | 可以证明确定写集、读会话、事件幂等与同版本地补齐；必须接真实现有转换，不能只交孤立 helper | 必须重新限定 B03 结束条件：不称完整 D07 执行/恢复链完成，不填虚构 GOAL/EXECUTION，也不新增旧格式兼容。之后仍需与 W03 联合迁移和组合评审；有重复接线成本 |

不接受的第三种做法：把旧 version、节点创建顺序或稳定路径 SHA 当新 phase/epoch 的默认值；或者在 mock 中构造不存在的生产事实后宣称整链通过。

作者倾向 A，以避免授权链先按旧格式接一次、随后再迁移。但没有完成两种方式的逐文件改动量对比，**不宣称 A 成本最低，也不把 A 标为唯一必要路径**。现有主清单仍是唯一工作索引；此表是用户取舍材料，不是新的并行 roadmap。依用户“扩大范围时停止”要求，待批次范围确认；不以另开设计评审代替用户取舍。

## 5. 六维自查与当前上限

- Declared by：R3 I3/I5、详设 D03/D06/D07、主清单 W03/W04/W05；未修改验收义务。
- Proven by：当前生产者/消费者源码静态核验；本轮零测试，不证明新协议能力。
- Consumed by：现有 contract/qualification 与 runtime/worker/ledger；新格式端到端消费尚未实现。
- Enforced before：本轮未新增任何执行点，不能把材料标为 ENFORCED。
- Failure route：前表为冻结契约的验证义务；没有新恢复实现、没有把安全停止称为可续作。
- Cannot-bypass test：需要合法同授权补齐和跨授权拒绝成对、两执行入口、四变体、每次写前缀冷启动及查询并发；目前 NOT RUN。

B02 LOW-1 仍归 W05/T10、关联 T14：kernel 临时文件/load/clean 故障纳入 D06 故障族，本轮未实施或验证。B01/B02 收口状态不变，W 项没有新增关闭，真实运行 NO-GO 不变。

未核验范围：142 候选逐方法的新会话改动量、全部运行配置身份生产路径、所有 reader 真实调用图、D03/D05 联合实施完整文件范围；没有声称这次开工核对已替代上述必要实施检查。
