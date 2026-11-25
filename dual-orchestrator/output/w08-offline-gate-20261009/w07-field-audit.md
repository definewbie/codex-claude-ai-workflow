# W07 / D10 逐字段案头核对

这是现有 producer→prompt→parser/validator→consumer 的索引，不是新 schema 或减负实现；保留严格 wire identity。冻结设计参见 output/freeze-review-20260928/freeze-details.md §10，内容锚点见 source-checks.json。

| 字段 / 来源 | 模型义务 / 唯一消费者 | 现状与处置 |
|---|---|---|
| schema_version / workstream_id / run_id / node_id | AgentResultPromptContract 从本次request生成完整示例；parser/validator精确比对 | 保留；模板 Workstream/Node 头部是定位，不另造示例/规则。不能把字面值重复当第二SoT，暂不为省几行改wire |
| review_subject occurrence/ref/SHA / base proposal id/SHA | 受核验当前上下文给出；模型回应实际subject或revision patch；Authored/TaskContract谱系消费 | 保留。AgentEvidenceIndex按来源去重，稳定合同与typed context采用原TypedContextPayloadBoundary；不能让“latest”或旧回显修正身份 |
| obligation_id + disposition/evidence | 原决定/复评义务给ID；作者逐项真实回应；validator exact membership、reviewer判语义 | 保留。不自动ADDRESSED；受限同次correction只修被列出的ID槽，其余语义必须保持。GCTW复制负担仍可能存在，不能从合成夹具声称模型负担降低 |
| planned_files / edit_files / scope paths+operations | 作者在GOAL后只读提案，canonical/excluded检查；EXECUTION compile及首次dispatch精确批准 | **已确认G01**：DesignScopeValidator resolve GOAL，只有EXECUTION时才要求scope.included；design-claude.md :19–21却要求从已批准scope复制全部路径/operation，AgentPromptRenderer :268亦写必须已授权。TaskContract GOAL不授实现scope。纠正方向为“提案→后续精确批准”，扫intake/design/plan/reviewer同族。未改模板/代码 |
| decision/summary/must_fix/missing_evidence/category | schema + typed resolver，模型不能覆盖实际process/资格失败 | 保留；合法正向与E02反证已有。不把handler的程序错误转换成模型缺JSON |
| verification_checks criterion/assertion/execution_ref/tool_call_ids/material_refs | 本次固定观察/经批准历史或operator submission；D05消费者重验 | 程序观察由R02/R03生成，模型只能选择/断言；CURRENT和空tool_call_ids选完整有效结果可减少ID复制，仍不可免证据。actual_exit_required按批准条款，不推全局exit0 |
| provider/model/billing/usage / PID/start/exit / requestId / C-X / commits | 原adapter/runtime/ledger生成，投影消费 | 不向模型索取，不从模型正文造authority；保留未知/不计价。Dual不限定Claude客户端绑定GLM或其他模型 |
| 完整JSON示例 / correction上下文 | render本次冻结schema；AcpxAgentResultAcquirer沿原contractPrompt作最多一次修正 | 模板一致性测试覆盖唯一agent_result_contract、无第二decision/schema规则；实际首版同input去重不代表已消灭所有真实复制错误。不存在必须先换DSL或缩schema的证据 |

G01最初被离线完整流程掩盖的原因：受控transport依据程序示例直接构造合法输出，不实际遵从全部自然语言提示；schema/生命周期绿色不能排除提示词与分期授权之间的方向冲突。需要实际渲染语义的成对回归，并保留GOAL无写权/EXECUTION精确批准校验。此处是W07本来承诺的实际重复/过期来源核对，不是新增功能要求。

本轮案头盘点完成；W07提示冲突尚未纠正，真实模型回应/修订质量与token收益NOT PROVEN。其余机器身份和义务遵循用户ROI决定，不新增通用字段消除器、全量prompt重写或模型层。
