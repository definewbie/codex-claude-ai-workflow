# Agent Result Prompt Projection Adequacy — 独立架构评审

日期：2026-08-17
评审基线：Dual `c750b25f2892994c1462cb819d47280c9c3b1422`（工作树仅含待评审 assessment + active plan delta）
性质：独立、只读、代码级。未修改被评审文件；未调用 provider；未运行 real-case。所有结论以 production 代码与 v15 captured prompt 原件为准。

---

## 1. Findings

### P0 / P1

无。待评审计划的事实主张**全部**经代码与 captured 工件核实成立。

### P2

1. **测试节点清单为手抄而非 registry 派生**：`AgentResultPromptContractTest.everyAgentNodePromptExampleIsStrictAndMatchesItsNodeContract`（`:16-41`）用硬编码 10 节点 map 遍历。当前与 `ContractRegistry.CONTRACTS` 的 agent 契约集合**恰好一致**（10 个 acpx.agent_run 契约节点，已逐一比对），不构成前提缺陷；但未来新增契约节点时该测试会静默不覆盖。建议作为后续卫生项（非本 checkpoint）：从 registry 派生清单。
2. **active plan 措辞时序**：`__ACTIVE__...md:91` "当前唯一 active 动作仍是 bounded prompt-projection checkpoint" 与其后 `:97-101` 的"前提不成立、建议 NO IMPLEMENTATION"是同节内的先后续述——提交 assessment 时建议把该句改为过去时或加"（已被下述核对推翻）"，避免未来读者误判仍有两个并列 active 动作。
3. **观察项**：`AgentPromptRenderer.renderWithSections:72-77` 在 `agentResultContract==null` 时有一个无 emission-context 的 fallback render 调用。生产路径（`AcpxAgentTaskHandler:79→:143→:227`）总是传入预渲染 contract，fallback 仅为防御性默认；两者都委托同一 owner，不构成第二 owner——记录备查。

---

## 2. 已确认事实 / 合理推断 / 无法验证

**已确认（本轮直接核验）**
1. **多行 pretty-print 属实**：`AgentResultPromptContract.java:64` `OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)`——全仓 prompt 相关仅有的两个 `writeValueAsString`（本处与 `AgentPromptRenderer:502` typed evidence）均用 pretty printer；**不存在单行 JSON 示例 producer**。
2. **"Every JSON object is closed" 属实**：`AgentResultPromptContract.java:52` 字面存在，且被 `AgentResultPromptContractTest:89` 断言。
3. **v15 两个 captured prompt 均展示最终根 `}`**：initial prompt 的 Output Contract 以完整多行 envelope 结尾（最后非空白字符为根 `}`）；correction prompt 以同一份完整多行示例结尾（`...evidence_ref_additions" : [...] } ] } }`），并前置 typed EOF 诊断（`UNEXPECTED_END_OF_INPUT / line 1 / column 3326`）与 "The previous response ended before the root JSON object was closed. Re-emit the complete AgentResultEnvelope and verify that every object and array is closed"。
4. **captured prompt 即当前 production 行为**：`git diff 070ad84d..c750b25f` 仅文档变更（src 零改动），v15 运行时的 prompt 生成代码与 HEAD 一致；`PromptManifestWriter` 按 section 记录 sha256（新鲜度/溯源在案）。非旧 artifact。
5. **唯一 projection owner**：`AgentResultPromptContract.render` 仅两处调用——`AcpxAgentTaskHandler.agentResultContractPrompt:441`（带 revision emission context）与 `AgentPromptRenderer:74`（null-fallback，无 context）；10 个 prompt 模板均以 `{{agent_result_contract}}` 变量委托（grep 证实模板资源无自嵌 JSON 示例，`schema_version` 零命中）。
6. **correction 复用完整 contract**：`AcpxAgentTaskHandler:79/223-233` 把同一 `agentResultContract` 传入 `acquirer.acquire`；`AcpxAgentResultAcquirer.renderCorrectionPrompt:205-222`（PARSE_FAILURE 分支）= typed diagnostic + 专项指令（`:247-256`）+ policy + **完整 contractPrompt**。
7. **测试网真实**：`everyAgentNodePromptExample...`（`:16-41`）对 10 个契约节点把示例（自首个 `{` 起）交给 **production parser + production validator + 对应节点 contract**；closed-fields 断言（`:59-95`）存在。
8. **correction 已比"末字符须为 `}`"更具体**：typed kind + 精确 line/column + 根对象未闭合的因果说明 + 完整闭合示例——信息量上严格超过拟议的通用末字符提示。
9. provider 在 initial 与 correction 两次 `end_turn` 后仍缺最终 `}`（v15 run.json occurrences + 上一轮已证的 stream/candidate 字节一致）。

**合理推断**
10. v15 correction 输出与 initial 近似（仅 +19 chars、结构重排）——模型理解了诊断并重发，但生成侧仍丢末括号；继续增加同义 prompt 文案不改变该倾向的信息基础。
11. provider 内部根因（为何两次丢最终 `}`、history 视图为何成对重复）仍 unknown——不做断言。

**无法验证**
12. 任何 prompt 变体对该 provider 行为的实际影响——需要新 provider run 才能测量，而当前无新输入变量（见 Q15）。

---

## 3. 十八问逐答

1. **是**——`:64` pretty printer，全节点同一 renderer（事实 1）。
2. **是**——两个 captured prompt 均以完整闭合的多行示例结尾（事实 3）。
3. **是**——correction 携带 typed kind/位置/因果 + 完整示例，严格更具体（事实 8）。
4. **否**——10 个契约 Agent 节点全走同一 owner；模板不自嵌 JSON；无 legacy bypass（事实 1/5）。
5. **否**——唯一 owner，两调用点均委托它；fallback 亦同源（事实 5、P2-3）。
6. **是**——遍历 10 节点，与 registry 当前集合一致（P2-1 为手抄漂移风险）。
7. **是**——production parser + production validator + per-node contract（`:35-39`）。
8. **否**——prompt 文案无法约束生成过程，只能形成字符串存在性断言；cannot-bypass 只能由 parser/validator/finalizer 持有。
9. **否**——v15 已在信息量更大的 correction 下失败两次；一句同义提示无行为证据、无 enforcement，作为 production checkpoint 是把 soft 变化包装成 reliability 边界。
10. **否**——评估过三个候选（末字符提示、correction 指令再锐化"缺的就是根 `}`"、示例结构变体）：三者与现有指令的信息内容同义或为未验证猜测，均无新 enforcement。不存在"更小但非重复且有合理收益"的 prompt delta。
11. **否**——correction 已含完整 contract + 最具体诊断，initial 已含完整示例与闭合指令；两侧均无可修内容。
12. **足以否定**——可用信息已达上限（精确位置 + 根闭合因果 + 完整示例），失败在生成侧；继续堆文案是零信息增量。样本量虽为 1 run，但边际价值论证与样本量无关。
13. **是**——v14（enum）与 v15（EOF）均被正确拦截、正确分类、正确 recovery 路由；`OUTPUT_INCOMPLETE / MANUAL_ONLY` + 单次 correction 的边界在两次独立失败形态上都正确 fail-closed。
14. **合理且已正确换基**——原 root-closure review 的触发条件以"prompt projection 落地后"为前提；本计划 §6.1 已改为"在现有 pretty-printed prompt 与 typed EOF correction **保持不变**的情况下再现"（即承认无 delta 可落地），并补充两个触发（第三历史样本由原始工件证实；provider-native structured-output 能力出现）。触发集自洽、可判定、且要求原始工件（拒绝文档转述）。
15. **否，明确拒绝**——无新输入变量：prompt 不变（NO IMPLEMENTATION）、contract 不变、provider 不变，重跑只采样 provider 方差，不检验任何假设；且违反"不为标签制造样本"原则。
16. **否**——该空间内可执行的边界修复只有 root-closure normalization（已按触发条件正确推迟）与 provider-native 结构化输出（外部能力，触发 3 已覆盖）；无被掩盖的小修复。
17. **符合**——停止的收益：避免伪 checkpoint 成熟度、字符串测试剧场、无证据的 prompt churn；成本：无（fail-closed 行为不变，触发条件保留重启权）。
18. **是**——captcha-auth 仍是 `NOT ACHIEVED`，v15 已 `FAIL / CONSUMED`；本停止决定正是把围绕同一 JSON 格式问题的 checkpoint 链关闭（v14→v15→root-closure→prompt projection 四连收敛于 diminishing returns），下一步应制定**新的 fresh Verification Gate**，且不得把 provider compliance 当作启动前保证（它是 `NOT PROVEN` 的运行期风险，由 strict rejection + 单次 correction 兜底）。

---

## 4. Sibling-path sweep

| 路径 | 结论 |
|---|---|
| initial / revision / design / plan / implementation / review / acceptance / handoff | 10 个契约节点共用 `AgentResultPromptContract.render`（`AcpxAgentTaskHandler:441`，revision 经 `ProposalEmissionContext` 分支注入 patch 专属指令含 target-uniqueness 投影 `:85-88`）；测试逐一覆盖 |
| legacy prompt template / node-specific sample | 模板资源零自嵌 JSON 示例；全部经 `{{agent_result_contract}}` |
| adapter-side prompt assembly | `AgentPromptRenderer` 只做变量替换与 section 记录；typed evidence 投影亦 pretty-print（`:502`） |
| correction prompt | 复用同一完整 contract（`acquire(contractPrompt…)` → `renderCorrectionPrompt:205-222`），另加 typed EOF 诊断 |
| prompt metadata/manifest | `PromptManifestWriter` per-section sha256（新鲜度在案） |
| captured v15 prompt vs production | `070ad84d..c750b25f` src 零 diff——吻合，非旧 artifact |

## 5. End-to-end invariant

| 维度 | 结论 |
|---|---|
| Declared by | `AgentResultEnvelope` + `ContractRegistry`（schema/shape 硬 owner） |
| Proven by | production 示例可被同一 parser/validator 接受（`AgentResultPromptContractTest:16-41`）；prompt 为投影，非 enforcement |
| Consumed by | provider 把 prompt 当**软输入**；返回的 stream 终消息由 strict parser 消费 |
| Enforced before | sidecar / reference / routing / successor 创建之前，非法输出被 parser/validator 拒绝（硬边界） |
| Failure route | typed parse failure → 单次 bounded correction（复用完整 contract + typed 诊断）→ 仍失败 → `OUTPUT_INCOMPLETE / MANUAL_ONLY` → recovery gate |
| Cannot-bypass | parser（dup-key/trailing/unknown/EOF）、validator（schema/semantic/target-uniqueness）、finalizer/outcome tests；**prompt 文案本身不构成 cannot-bypass**——字符串断言只能证明"文案在场"，不能证明 provider 遵从 |
| Residual raw/legacy path | provider 可无视格式指令——text-only ACP 边界的已知能力限制；parser fail-closed ≠ provider 输出问题已解决 |

## 6. 是否存在有效 production delta

**不存在。**拟议两项中："单行→多行"无代码 delta（早已多行）；"末字符检查"为零信息增量的 soft 重复。评估过的其他 prompt 变体（correction 锐化、示例变体）同属无 enforcement、无行为证据类。

## 7. 是否应关闭 prompt checkpoint

**应关闭。**`NO IMPLEMENTATION / KEEP STRICT REJECTION` 成立：前提（单行示例）被 production 代码事实推翻；唯一候选 delta 不构成边界；v15 证明该 failure mode 下可用信息已达上限。root-closure normalization 维持推迟，触发条件（§6.1-6.3）已正确换基且优于原版。

## 8. 是否允许只提交 assessment + active plan

**允许。**两份文档与代码/captured 事实一致（P2-2 的措辞时序建议随提交顺手修正；非阻塞）。不提交任何 production 代码。

## 9. 下一步唯一允许动作

按 assessment §8：制定**新的 fresh captcha-auth Verification Gate**（v16），其 Trigger precondition **不得**把 provider JSON compliance 列为启动前保证——provider compliance 维持 `NOT PROVEN`，作为运行期风险由既有 strict rejection + 单次 correction + fail-closed recovery 兜底；root-closure 触发条件作为该 Gate 的观测项之一（自然再现即留原始工件并触发重评）。不重跑 v15、不建第二样本、不调 provider（Gate 评审与 preflight 授权另循既有流程）。

## 10. 最终结论

| 项 | 结论 |
|---|---|
| **Boundary Review** | **PASS**（计划全部事实主张经独立核验成立；P2×3 均为卫生/措辞项） |
| **`NO IMPLEMENTATION` 是否成立** | **成立** |
| **Implementation READY** | **NOT APPLICABLE**（无有效 delta 可实施） |
| **当前成熟度** | prompt projection：`VALIDATED`（示例经 production parser/validator 验证、全节点覆盖）；strict rejection/correction 边界：`ENFORCED`（v14/v15 双形态实证）；provider compliance：`NOT PROVEN`（且不得由字符串测试声明为 ENFORCED）；root-closure normalization：推迟，触发条件在案 |
| **是否授权 provider / real-case** | **否**——本停止性评审不授权任何 provider 或 real-case；新 Gate 的 preflight/probes/workstream 授权按既有流程另行决定 |
