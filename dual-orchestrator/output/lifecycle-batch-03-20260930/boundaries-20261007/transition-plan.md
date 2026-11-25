# B03 权限证据与运行身份续作

2026-10-07；P0 稳定性，B03 IN_PROGRESS。沿已冻结 D02/D05/D06/D07，无新 authority、模型层、DSL 或原生权限回复桥接。相对本轮前 dirty 源码记录增量。

## 权限边界

定义是已批准 EXECUTION 的验收绑定与精确非必需声明；instance 是实际 node/合同 occurrence；execution 是当前 run 的 ACP 请求/决定/工具观察。transport 生产操作身份，validator 生产 permission assessment，唯一 resolver 决定是否能成功；模型正文不生产上述程序事实。

| 前态/触发 | 写序与消费者 | 失败/重试/恢复 |
|---|---|---|
| R01 输入已批准 → R02 后 ACP 请求 | 当前 prompt/session 内保存请求 id、tool id、参数身份、原始请求/决定内容身份；健康信号携带同一操作关联 | 缺/错身份、重复异内容、结果不明不制造 PROVEN_NONESSENTIAL；没有授权重新执行被拒工具 |
| provider 完成 → typed 观察固定 | R 锁下不可变观察 ref/SHA，run signals 保存同一 operation；无模型声明替代 | 观察写失败保留原有 I/O/UNKNOWN 路径，不产生成功发布 |
| 候选合法 → validator 判断 | 已批准 EXECUTION + 当前固定工具观察/D05 逐项结果 → permission_assessments；独立完成证据不得依赖被拒工具；第二通道仅精确批准声明 | 不支持的操作、不可降级边界、材料/参数/合同漂移均 UNKNOWN/原阻断；正常无拒绝与合法 warning 仍可成功 |
| 单次 resolver → 固定 outcome/publication → R03/L03 | 同一 signal 精确消费 assessment，原 timeout/incomplete/合同顺序保留；runtime/worker 共用 | 缺关联不能用 JSON 完整覆盖；持久化失败只补同次本地，不重判旧结果或重发 provider |

参数身份仅规范化 JSON 字节，不分析任意 shell。首版只对现有 typed read/search 操作取得可判定、非控制目录目标证据；其他操作不能借“可选”标记降级。这个支持集合不能被称为沙箱，也不改变原生审批。第二通道声明由冻结 workflow/node 合同输入编译进 EXECUTION，明确展示并随完整 SHA 批准，不能只读取未获批准的节点 config。

## 身份边界

运行 executable/schema/checker/prompts 由既有 FrozenRuntimeIdentity 生产并被 ledger 写/读会话消费；本轮继续核对 installed acpx/adapter 与有效配置的真实生产路径。声明常量、配置命令、实际解析包字节是不同证据，不从前两者推导后者；未知解析不能伪称同版。

新副作用前与恢复入口必须消费相同当前身份；缺项和漂移只能拒绝/诊断，不能通过默认值放行。凭据不得落盘。具体原生解析若无法沿现有接口取得，记录消费者与缺口，遵守既有成本/authority 止损，不在实施中发明包管理器或认证平台。

## 验证义务

合法完整成功、精确关联且独立验收完成的警告、已批准声明通道，分别配缺证据/错 signal/参数/合同/规则/只读违规/控制路径/旧 session 拒绝；ACP 历史/多请求/重复 id 的兄弟入口、inline/worker、固定发布恢复与 E02 反证保留。新字段写/重读不丢关联。LOW-1 的退出码要求弱化重批序列与 smoke 迁移仍是 B03 收口义务，不能用本切片代替。

零真实 native/provider、额外 agent、提交；离线测试允许自建本地文件/进程 fixture。末尾按 Declared/Proven/Consumed/Enforced before/Failure route/Cannot-bypass 自查，最终一次 clean 全量回归后再作 B03 收口判断。
