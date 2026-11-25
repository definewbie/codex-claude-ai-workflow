# 两个边界与实际消费者

## F1：封闭契约/可修正诊断（P0）

`AgentResultEnvelope.RevisionResponse` 两字段为 Definition SoT。`AgentResultPromptContract.contractRevisionInstruction` 在 resolver 允许 full 时枚举同 record；patch-only 不新增 full 资格。作者产物仍经唯一 `AgentResultEnvelopeParser` 的严格 mapper，未知字段产生原 AGENT_RESULT_PARSE_FAILED + UNKNOWN_FIELD(fieldName,allowedFields)；没有忽略字段/程序代修。

`AcpxAgentResultAcquirer` 消费 typed diagnostic，向唯一一次原 deny-all 修正展示具体键和该对象合法字段集（JSON 转义，不复制未知值/整个 raw/异常正文）。修正失败继续唯一 outcome→R03/L03→原恢复。成功解析只是候选，仍须原 validator/基准 SHA/merger/lineage/独立复评/人门；不产生新的 authority。EOF、普通坏 JSON、schema 修正、主调用与 correction 共享契约不变。

## F2：可读正文/帧（P0.5）

唯一协议语法 owner `AcpxStreamWireLine` 新增 mixed-stdout 入口，复用原 fromNode；严格 session NDJSON 的 parse 语义不变。成功解析且有 jsonrpc/method、或 id 与 result/error 组合，才按 RPC 形状核结构。单独 id（正文条款）和多行 JSON 碎片不是协议身份。识别到的真协议形状若结构坏，返回 MALFORMED；reader 明确 INVALID_CURRENT_TRANSCRIPT，不借共享成功。

`AcpxUsageReader.readCurrentUsage` → 原当前完成身份/重复/错误/非负完整量核验 → 原固定 occurrence/acquisition → aggregate → R03/L03 → metrics/operator。没有当前完成帧才保留严格唯一共享出口；金额只作原同 token/唯一历史补充，快照不是请求总量。判别依赖客户端输出形态，未新增对任意恶意正文的安全隔离保证；不可解析片段不被推断为完整可信帧。

## 兄弟检查

- initial/full revision/patch-only；intake 与 design/plan 的不同 response record 不混字段。未知字段 generic parser 同源；EOF/普通语法/schema 仍有原用例。
- 当前 stdout vs session NDJSON 分开入口；projector/health/tool/permission 原 parse 保持原义。此次不更换它们的权威来源。
- 双客户端、主/修正、延迟共享、跨代重复、日志后续追加、错误/重复/部分/快照和金额均由常驻族覆盖。
- 未来 operator observer 候选同步正文分类，仍先核 R03 完成与日志 SHA，再核原生帧；只读、不是生产预算 guard，不回填第六例。

## Durable 边界

本批未改 R01/R02/R03、L03、C/X/D06/D07 的写集或次序。解析/诊断与混合日志分类发生在原候选/usage 生产处；失败不能新发布，缺量不能被 aggregate 作完整量。既有前缀/冷 JVM/四变体/inline-worker 在本批单轮回归承接，不声称本批为所有新增组合重做全笛卡尔积。旧 ledger 只读；新 reader 分析不改变旧 R03，因此也不授旧运行继续资格。
