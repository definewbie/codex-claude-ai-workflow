# W09 F01/F02 前向修复边界分析

目标：P0 合法原生会话可进入既有执行链；失败摘要保真。不扩大原生权限，不恢复首例，不运行真实客户端/provider，不增加版本锁。

## Source of truth / owner
- Definition：既有 workflow/分期合同；零变化。
- Instance：L03 固定 node/current；失败摘要是派生输出，无批准或路由 authority。
- Execution：R01/R02/R03 与核心唯一 agent_execution_outcome；session show 为 ACP 接口观察。
- AcpxSessionCompatibility 负责字段资格；acpxRecordId/acpSessionId 必需，agentSessionId 是可选内部标识，不复制其他 ID。
- FailureSummaryWriter 消费已固定 primaryReason，不重判失败；RecoveryAssessment/核心 resolver 不改变。

## Transition / failure cuts
| 前态→动作 | 顺序与后继 | 失败与恢复 |
|---|---|---|
| 已准备 invocation→sessions ensure/show | 原 command intent→实际命令→result(兼容性观察)→prompt intent→prompt | 身份/协议不合格零 prompt；兼容性 intent/result 写失败零下一阶段；可选 ID 缺席本身不拒绝 |
| prompt/adapter 返回→核心 finalize | 唯一 outcome→R03 固定→L03 同原 prepared 提交→派生 current-failure | 原 D06 固定字节补齐与 D07 UNKNOWN；摘要不能创建第二原因或许可，I/O 不能变模型失败 |
| 冷读/查询 | 原 node/outcome→RecoveryAssessment / persisted report | 报告不产生重试资格；首例禁止在新 binary 下恢复 |

## 首批验证与 sibling
- Claude/Codex：存在/缺席/显式 null 可选身份；必需 ACP/record、schema/name/cwd/open/protocol 拒绝成对；有字段但类型错误或空字符串仍拒。
- 主调用、correction、现有 reconnect/rebuild 与 permission 本地 peers 复用无可选字段的共享夹具；不替代真实客户端证明。
- inline/worker：adapter_incompatible 和 process_output_incomplete 的 run/node/recovery/summary 同源；缺模型产物只作次级诊断；冷 JVM 重读。
- 原非 agent 失败/真正合同失败摘要保留。malformed typed primary 不回落旧 reason。
- 先红后绿、删除式反证、稳定源码单轮全模块、发行字节核验。只改上述 owner/相关夹具、测试及材料。

## 停止条件
发现身份下游实际需要可选 ID、必须改 R/L/权限协议、成本超出现有 owner 接线，先停止提交证据。无新 authority、兼容旧 ledger、样本救援、平台或安全实验。

独立 CC 评审后再提交 fresh Gate；真实运行另需用户授权。原失败报告和账本保持原字节。
