# P0：精确枚举诊断前向修复

## Analyze / Boundary Review

- Problem type：现有契约诊断信息在 parser→correction 投影中丢失；合法 JSON 的非法枚举被归为 INVALID_JSON。
- Touched boundary：结构化输出解析→现有单次 deny-all correction；不新增 workflow outcome、schema 或恢复状态。
- Source of truth / Artifact ownership：原始响应与 Jackson typed mapping exception；AgentResultEnvelopeParser 唯一拥有语法/类型诊断，AcpxAgentResultAcquirer 只渲染该诊断。
- Definition：AgentResultEnvelope 及现有 enum 为允许值唯一来源；无新领域对象。
- Instance / Domain state：原 node/workstream/iteration 不变；本次不改变路由、批准、scope 或终态。
- Execution state：保留原 process/completion/adapter 阻断检查、原始候选及原单次 correction；解析失败仍失败，不把诊断冒充修正成功。
- Projection surfaces：correction prompt；诊断消费者走同一 parser，不解析 exception 自然语言正文。
- Decision：在现有 ParseDiagnostic 增补字段路径/非法值/允许值。按异常类型识别 enum failure，并由目标 enum 定义派生值；unknown-field、EOF、通用语法错误继续各走原路径。
- Failure severity：此次是呈现诊断的前向修复。合法输出集合、artifact contract schema、SoT、ledger 写序、授权边界及执行次数均不变；按 TRANSIENT 的比例验证，不以宽泛“contract”字样触发完整发行仪式。若修改开始改变上述边界，回到分析并升级 BOUNDARY。
- Target phase：P0；本轮不声称 P0 整体 Verified/Promoted，不启动自动恢复或上下文优化。

## Transition / 写序与失败切面

| pre-state / action owner | 顺序 / authoritative post-state | 下一消费者 / 失败解释 |
|---|---|---|
| initial 外部响应已返回；acquirer | 原完成/进程/signal 资格检查；parser 产出失败 + typed diagnostic | 合格呈现错误才生成纠正输入；未完成/非零/provider failure 不纠正 |
| parser 捕获非法 enum | 从 exception path/value/target enum 形成诊断；原响应不改 | acquirer 渲染 code/path/value/allowed；无异常全文转写，无凭诊断批准 |
| 允许单次纠正 | 原位置写 correction prompt→原 deny-all transport→新候选→同一严格 parser/validator | 写 prompt 前失败无纠正调用；写后/调用中失败沿原执行协议；不新增 retry/resume/replay 入口 |
| corrected candidate 已返回 | 保留初始与纠正候选、按原规则判定 | 仍非法则失败，不启动第三次；错误身份、scope、权限继续拒绝 |

## 验收与最小证据

1. HHZL 原响应可在新版本获得枚举类型诊断，field pointer=/result/failure/verification_remediation、actual=REVISE_PROPOSAL、allowed 从 VerificationRemediation 派生。它是新版本历史输入回归，不是旧 binary 重放。
2. 实际 acquirer 生成的纠正文件消费上述事实，且原错误全文/任务正文不会被新路径拼入；脚本 transport 同时覆盖 Claude/Codex 入口，未调用 provider。
3. 修正后候选仍经原 parser/validator：合法字段类型不等于产品批准；错身份/非法枚举/未知字段继续失败；总调用≤2。
4. sibling sweep：VerificationRemediation、设计 scope operation、revision disposition/patch enum，以及 unknown field/EOF/generic error；不为 HHZL 硬编码字符串或白名单。
5. targeted tests：parser、acquirer、直接 handler、prompt/validator 与 typed outcome；一次现有 lifecycle sequence smoke。只在新增失败/差异要求时扩展回归。

原工作区四个文件已有修改，保存于 before/ 与 before-sha256.json；只报告本次相对该基线的增量，不覆盖已有工作。独立评审未获用户委派，不将作者自查冒充独立 PASS。
