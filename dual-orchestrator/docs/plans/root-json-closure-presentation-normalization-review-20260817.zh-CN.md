# Root-JSON-Closure Presentation Normalization — 独立架构评审

日期：2026-08-17
评审基线：Dual `1660b8e59dfa29444e9e38c95010294b4b956801`（工作树 clean）
性质：独立、只读、代码级。未修改被评审代码；未调用 provider；未触碰 v15 workstream 状态；唯一落盘物为本报告与 /tmp 下一次性 replay harness（已删除）。

---

## 0. 评审总结论（先行）

**`Boundary Review: WARN`。** 候选方案在**架构形态上是可成立的**（owner 位置正确、不与 grammar authority 冲突、无需 durable schema 变化），但**证据基础在本次评审中被推翻了一半**：v15 result 声称的"2 个独立 real-case / 4 个 occurrence 同一 EOF morphology"经我对 T6 v5 原始 wire stream 的直接核验**不成立**——T6 v5 的失败形态与 v15 完全不同（围栏/前置 prose/深截断），不属于本方案的可恢复集。真实下界是 **1 个 run / 2 个 occurrence**（仅 v15）。在证据坍缩、且更小成本的 prompt-projection checkpoint 尚未获得验证机会的情况下，**当前不应建立 production checkpoint，应继续维持完全拒绝**；方案本身保留为"条件触发的后续候选"，触发条件见 §9。

---

## 1. Findings

### P1

**P1-1：v15 result 文档的跨样本 EOF 频率声明与原始证据不符（证据完整性缺陷，必须先修正）。**
`20260817-terminal-delivery-captcha-auth-completion-v15-result.md:46-51` 声称 T6 v5（`ws-20260802T152626Z-QN2R`，`plan.claude_plan）"initial output and its single correction both ended one root-closing brace early"，并据此给出 "2 runs / 4 occurrences" 下界。直接核验结果：

- T6 v5 workstream ledger 已不存在（全盘搜索无 `*QN2R*` 目录；archive 仅含 v14）。
- 但三个 QN2R-scoped acpx session 的 **wire stream**（authoritative raw assistant fact）仍在（`~/.acpx/sessions/019fc316-…`、`24de4b13-…`、`ef15424f-…`）。按 turn 重建全部 6+3 个 assistant 终消息并做结构扫描：
  - `24de4b13` turn1（10,258 chars）：以 ``` 围栏结尾的 fenced presentation；
  - turn2（8,830）/turn4（15,106）：brace 平衡但整体不可解析（fence/多文档形态）；
  - turn5/turn6（17,076/17,052）：**在嵌套 depth=2 处截断**（深截断，非 root-only）；
  - `ef15424f` 唯一 turn（7,821，record 视图）：depth 平衡但含前置/成对内容，invalid 非 PLUS1 可修复；
  - `019fc316` msg[1]/msg[5]：**前置中文 prose + JSON**（2026-08-02 早于 presentation normalization checkpoint，属 fence/prose 类失败）。
  - **没有任何一条消息呈现"仅缺根对象最后一个 `}`"的形态**；`+1 brace` 修复对 T6 v5 的 wire 消息全部不适用。
- 结论：result 文档把 T6 gate doc 中"malformed JSON in first occurrence and correction"（`20260802-t6-interruption-ledger-ownership-v5.md:18-19`，未描述 morphology）错误外推为同形 EOF。**该声明必须修正**，否则未来任何以"复现频率"为前提的决策都会建立在假数据上。

### P2

**P2-1：候选方案的架构形态本身可成立（条件性正面结论）。** 详见 §3 逐问（Q3-Q5、Q9-Q12、Q17-Q18）：owner 位置（`presentationCandidate`）、gated-append + 原 parser 重解析的闭环、既有 provenance 字段（raw/candidate SHA）都支持其"不破坏现有边界"的主张。不成立的是**当前时点的必要性**（证据坍缩 + 更小替代未验证）。

**P2-2：T6 v5 的 record-history 视图存在 8,000 字符截断与成对重复**（`24de4b13` record 中 5 条 agent 消息恰为 8,000 chars，wire 同 turn 实为 8.8K-17K）——adapter 历史存储层观察项，**不能**作为 morphology 依据（本轮以 wire 为准）；来源 unknown，与 v15 的 history 重复现象同类，仅记录。

**P2-3：语义截断接纳风险（Q8）无法被完全守住**，只能压缩与显式化——见 §3 Q8 与 §8；任何实施都必须把它写成 typed disposition 可见的 residual，而不是宣称零风险。

**P2-4：实施顺序倒置**：条件 2（依赖 Jackson `JsonEOFException`）意味着 normalization 必须发生在一次 parse **之后**、终 parse 之前——它不是纯粹的"presentation 前"步骤，而是 acquire 流程内 `parse(raw) → gate → append → re-parse` 的中间步。设计文档若实施必须如实建模（见 §5 Required changes 第 3 条），否则会造成"presentation 层调用 parser"的伪分层。

---

## 2. 已确认事实 / 合理推断 / 无法验证

**已确认（本轮直接核验）**
1. v15 两个 candidate 均"仅缺根对象最后一个 `}`"：追加一个 `}` 后 grammar 完整（braces 14/13、brackets 9/9；Jackson EOF 列 3326/3262 = 输入长度+1）。
2. **离线全链 replay PASS（Q19，实测非推测）**：用 production `AgentResultEnvelopeParser` + `AgentResultEnvelopeValidator` + `TaskContractRevisionContextResolver` + `TaskContractProposalRevisionMerger`（对 v15 ledger 只读）依次处理两个 `raw+"}"`：
   - attempt-1：`parse.valid=true` → `validate.valid=true / disposition=NONE / errors=[]`（含 target-uniqueness 消费，`AgentResultEnvelopeValidator.java:242`）→ context（2 obligations CO-1/CO-2，previous proposal lineage `91dc5c58…` 一致）→ `merge.valid=true`，2 个 revision responses；
   - contract-correction：同样全 PASS。
   - 即：**两个 occurrence 的语义内容完整**（所有 required/optional 字段、义务覆盖、lineage 齐备），缺的只是 framing 的最后一个字节。这是 Q7"framing 级恢复"命题的直接证据。
3. presentation normalization 唯一 owner：`AcpxAgentResultAcquirer.presentationCandidate`（`:290-307`，fence helpers `:309-397`），仅两个调用点（initial `:44-45`、correction `:78-79`，同一 acquire 流程）；全仓 grep 无第二 owner。
4. grammar 唯一 owner：`AgentResultEnvelopeParser`（`:12-25`：`STRICT_DUPLICATE_DETECTION` :18、无标量强转 :21、`FAIL_ON_UNKNOWN_PROPERTIES` :22、`FAIL_ON_TRAILING_TOKENS` :23；`JsonEOFException` → typed `UNEXPECTED_END_OF_INPUT` :33-41）。全仓唯一 `JsonEOFException` 消费点。
5. provenance 模型已存在：`AgentResultOccurrenceDocument` 每 occurrence 持久化 `presentationDisposition` + `rawAssistantSha256` + `candidateSha256`（`AgentResultAcquisitionDocument.java:36-38`，经 `AcpxAgentResultAcquirer.java:509-511` 写入 run.json）；输出文件写 candidate（`AcpxAgentTaskHandler.java:243-253`）——fence 场景今天就有 candidate≠raw 的先例。
6. T6 v5 wire 形态与 v15 不同（见 P1-1）；本方案的 11 条排除条件会正确拒绝 T6 v5 的全部形态（in-string / 深截断 / fence / 多文档 / prose）。
7. 既有测试网覆盖 presentation 边界与 cannot-bypass：`AcpxAgentResultAcquirerTest.java:55-131`（fence 各形态）、`:138-165`（ambiguous 永不提取）、`:174-213`（normalization 不能绕过 strict JSON/schema）、`:215-247`（correction 同管线 + audit 字段）、`:280+`（失败 correction 不产生第三次 prompt）、`:351+`（typed EOF 诊断不冒充）。

**合理推断**
8. T6 v5 record 视图 8,000-char 截断是 adapter 历史存储上限所致（5 条消息精确 8,000 与 wire 长度不符）；与 v15 的 history 重复同为 adapter 历史层观察项。
9. v15 形态的成因仍是 provider 输出（模型两次在内容完成后丢最终 `}`）；history 重复来源 unknown，不作为事实。

**无法验证**
10. T6 v5 的**全链恢复**：ledger candidate 已删除，无法对其做 parse/validate/merge replay —— `NOT VERIFIABLE`；且 morphology 已被 wire 证据**反证**，任何"恢复后完整通过"的说法都不成立。
11. v15 形态的总体发生率：既有 retained 证据中仅 1 run/2 occurrence 可确认；旧 ledger 缺 typed diagnostic 字段，无法统计总体率（result 文档自己也承认，但其"2 runs"下界仍错）。

---

## 3. 二十问逐答

1. **Authoritative raw assistant fact 的 owner**：ACP session stream 的 turn 终消息，由 `AcpxSessionStreamProjector` 投影（`appendAssistantMessage :196-208`、`finalAssistantMessage :210-219`），经 `AgentLifecycleEventReader` 进入 acquire 流程。v15 已证明输出文件与 stream 逐字节一致。
2. **Presentation normalization 唯一 owner**：`AcpxAgentResultAcquirer.presentationCandidate`（`:290-307`）——package-private、每 occurrence 调用一次、无旁路（grep 证实）。
3. **放在 parser 前的 presentation 层是否职责正确**：**大体正确但需修正表述**。该层的既有职责是"确定性文本变换"（fence 剥离）+ typed disposition + raw/candidate 双 SHA。root-closure append 属于"由 typed parse 诊断触发的、受结构门约束的单字节补全"，仍可落在这层（同层、同 owner、同 provenance 模型），但它依赖一次前置 parse 结果（P2-4 的顺序倒置），实施时应建模为 acquire 流程内 `parse(raw)→gate→append→re-parse` 的显式步骤，而不是假装它不碰 parser。
4. **是否破坏 parser 的 grammar authority**：**不破坏**——append 后的 candidate 必须重新进入同一个 strict parser（proposal 条件 9），接受/拒绝的最终权威仍是 Jackson/parser；gate 只决定"是否尝试"，错误 gate 的最坏结果是浪费一次 parse（被拒）或放弃恢复（fail-closed），不可能造成假接受。
5. **是否形成两个矛盾 grammar owner**：**不会形成"矛盾"，但会形成部分冗余**。结构扫描是一个受限 tokenizer（判 in-string/array 平衡/depth==1），与 Jackson 的判定可能不一致；不一致时以 fail-closed（不恢复）收敛，parser 永远是终审。冗余成本要在 tests 里用 parity 用例钉住（gate 说可恢复但 parser 拒绝 → 拒绝；gate 说不可恢复 → 不尝试）。
6. **能否完全证明只缺 root 的一个 `}`**：**grammar 层可证明**（in-string=false、arrays 全闭、depth==1、末字符为合法 closer + Jackson EOF-at-EOF + 重 parse 通过）。**语义层不可证明**——见 Q8。
7. **若 required fields 已全部输出后漏 `}`，恢复是否只是 framing ETL**：是。v15 两个 occurrence 是直接证据（§2 事实 2：恢复后全链 PASS，语义完整、覆盖齐全、lineage 一致）。
8. **若模型恰在可选字段前停止，追加 `}` 后 schema 合法，是否错误接纳语义截断结果？** **存在该风险，且不能被完全守住**。semantic/artifact validators 只校验 required 字段、义务覆盖、lineage、target uniqueness——不校验"模型本想继续写"。缓解：(a) 结构门要求 EOF 恰在"完整值边界"（末字符为 `}`/`]`/`"`/数字等合法 closer，而非逗号——逗号结尾应拒绝，可作为额外条件补充）；(b) disposition typed + raw SHA 使每个被恢复样本可审计；(c) normalization 成功后如校验失败仍走既有单次 correction。残余风险必须在方案里如实声明（见 §8）。
9. **raw 与 normalized 建模**：沿用既有模型即可——`PresentationDisposition` 新增枚举值（内存 enum，按 name 序列化进 run.json 字符串字段），occurrence 已有 `rawAssistantSha256`/`candidateSha256` 双 SHA；输出文件写 normalized candidate（与 fence 场景 candidate≠raw 同构）；raw 全文保留在 session stream（authoritative）。**禁止**把 normalized 内容写回任何"raw"命名的字段/文件。
10. **correction 应消费 raw 失败还是 normalization 后失败**：**消费 normalization 后的最终 parse/validation 失败**——correction owner 唯一（acquire 流程内的 `correctionReason`，`AcpxAgentResultAcquirer.java:179-190`）。若 normalization 在 presentation 层（每次 occurrence 内部）完成，则 correction 逻辑完全不感知它，天然无双 owner。**禁止**在 correction 层再做一次 normalization（那才是双重 owner）。
11. **normalization 成功后是否还应 correction**：**不应**。恢复且全链校验 PASS → `NOT_NEEDED` 路径（不发起 correction）——这正是方案的价值主张：把"1 次 provider 调用 + 1 次 correction"降为"1 次调用"。恢复但校验失败 → 现有单次 correction（reason 取校验失败类别）。
12. **与单一 fence normalization 是否安全等价**：**不等价，三点**：(a) fence 剥离是结构盲、无损的文本变换，不可能把非法 JSON 变合法；brace append 是语法补全，**能**把（这一种形态的）非法文档变合法——风险类别更高；(b) fence 剥离不依赖 parse 结果，brace append 必须由 typed EOF 诊断 + 结构门触发；(c) provenance 上 appended 字节是合成物，审计语义更重。共同点：raw 保留、disposition typed、重进同一 parser、validator 链不可绕过。
13. **更小替代（多行 JSON 示例的 prompt projection）**：v15 result 已选定它（`:71`）。**长期价值比较**：prompt projection 是模型面风险消减（不保证、可能降低发生率、零控制面风险、还能改善 EOF 诊断定位）；root-closure normalization 是确定性恢复（保证接纳该形态、但引入 grammar 邻接 owner + 语义截断残余风险）。**二者互补而非互斥**；正确顺序是先 projection 后 normalization——projection 落地后若形态仍自然复现，才有 normalization 的增量价值可衡量。
14. **2 runs/4 occurrences 是否足够**：**不足以支持 production checkpoint**——且经核验该下界实为 **1 run/2 occurrences**（P1-1）。这个量级只够支持"设计并保留一个 deterministic 实验 checkpoint 的候选资格"，连实验 checkpoint 的立项都应在 prompt projection 验证之后。
15. **成熟度**：deterministic tests 通过后，该能力（结构门 + 恢复 + 全链校验）本身最多标 `ENFORCED / NOT PROVEN`；其**恢复价值**（对自然发生的 provider 输出真的救回 run）在获得自然样本前只是 `PROJECTED`。不得标 `PROVEN`，不得描述为"provider 行为已修复"。
16. **是否继续完全拒绝**：**是——当前继续完全拒绝**。理由：(a) 证据基础坍缩至 1 run/2 occurrences；(b) 更小、更安全的 prompt-projection checkpoint 已立项但未验证；(c) 方案虽有可控架构形态，但引入 grammar 邻接 owner 与语义截断残余，属"成本非零、收益未证"。**重评触发条件**：prompt projection 落地后的自然运行中，该 exact morphology 再次出现（≥1 个独立 run），或第三个历史样本被原始工件（非文档转述）证实同形。
17. **自动补 `}` 后绕过 target uniqueness/lineage/schema/semantic 的路径**：**不存在**。normalized candidate 与任何合法输出走完全相同链路：parser（dup-key/trailing/unknown，`:18-23`）→ validator（schema/semantic/target-uniqueness `:242`）→ `ArtifactContractValidator` → merger（lineage/coverage/scope/target-uniqueness `:46`）→ durable 发布。append 只影响"能否进入链路入口"，不影响链路任何一环。v15 replay 已实证四环全过。
18. **是否需要 durable schema 变化**：**不需要**——新 disposition 枚举 + 既有双 SHA 字段即可承载（`AgentResultAcquisitionDocument` schemaVersion 2 不变）。若实施中提出"新增 raw 文件落盘/schema 字段"，即说明方案已超出最小边界，应回到设计。
19. **v15 离线 replay**：**已实测，全 PASS**（§2 事实 2）：strict parser ✔、schema ✔、semantic ✔（disposition NONE、errors 空）、revision coverage（CO-1/CO-2 两 response）✔、lineage（base proposal id/sha 与 context 一致）✔、merge（durable）✔。target uniqueness 顺带被 validator/merger 双消费且无重复 target。
20. **T6 v5**：workstream ledger 已删 → **全链 NOT VERIFIABLE**；且 wire stream 证据**反证**了"仅缺 root `}`"的同形声明（P1-1）——不存在"恢复后通过"的问题，因为其形态根本不在可恢复集内。

---

## 4. End-to-end invariant（若实施，方案应满足的边界）

| 维度 | 要求 |
|---|---|
| Declared by | ACP stream turn 终消息（raw assistant text）——永远是 authoritative fact；normalized candidate 只是派生输入 |
| Proven by | raw/candidate 双 SHA + typed `ROOT_OBJECT_CLOSURE_RECOVERED` disposition + gate 条件（Jackson EOF@EOF + 结构扫描）+ 重 parse 结果 |
| Consumed by | 唯一消费 normalized candidate 的是 strict parser（`AgentResultEnvelopeParser.parse`）→ validator → `ArtifactContractValidator`/merger 链；correction owner 消费的是 normalization 后的最终 parse/validation 结果 |
| Enforced before | normalization 不满足 11 条件 ⇒ 阻止：不 append、不进入 parser 的候选通道之外任何路径、不发布 sidecar/reference、不产生 successor；满足且校验失败 ⇒ 不发布且最多一次 correction |
| Failure route | gate 拒绝 → 现状路径（typed EOF + 单次 correction + `OUTPUT_INCOMPLETE/MANUAL_ONLY`，`AgentExecutionOutcomeResolver.java:61-65`）；gate 通过但校验失败 → 现有单次 correction（reason=校验类别） |
| Cannot-bypass test | "normalizationCannotBypassStrictJsonOrSchemaValidation"（`AcpxAgentResultAcquirerTest.java:174-213`）模式扩展：对 root-closure 各拒绝形态断言 fail-closed；对恢复形态断言全链重走 |
| Residual raw/legacy path | session stream 与 acpx record history 永远是调查材料；normalized candidate 不得写回任何 raw 命名的字段 |

---

## 5. Required implementation changes（仅当未来触发条件成立、立项时适用）

1. `AcpxAgentResultAcquirer`：acquire 流程内在 presentation 步骤后增加 `parse(raw) → gate → append(仅一个 ASCII `}`) → re-parse`；`PresentationDisposition` 新增 `ROOT_OBJECT_CLOSURE_RECOVERED`（或 `RECOVERED_ROOT_CLOSURE`，命名保持动词明确）；gate 实现 in-string/array/depth==1/末字符非逗号 的受限扫描，**禁止** message.contains/regex 猜测。
2. 结构扫描与 parser 的 parity 测试（gate 判可恢复但 parser 拒绝 → fail-closed；gate 拒绝 → 不尝试）。
3. 设计文档如实描述"依赖前置 parse 诊断"的顺序（修正"纯 presentation 前"表述）。
4. v15 两个 candidate 作为 captured fixture（等价 shape，含 lineage 字段）固化"恢复后全链 PASS"replay 测试（本报告 §2 事实 2 已验证可行性）。
5. 修正 v15 result 文档的跨样本频率段（P1-1）——**此项与是否立项无关，现在就应做**。

## 6. Required cannot-bypass tests（评估 + 增补）

提案的 16 条覆盖面良好，评估：1-12 与现有 fence 测试同构、可行；13/14 正确锁定 correction 单 owner；15 沿用既有 audit 字段断言模式；16 由 `ContractRegistry` 全节点共用同一 acquire 流程天然满足（仍应加断言）。**增补四条**：
- T17（parity）：gate 与 parser 判定不一致的两个方向均 fail-closed；
- T18（逗号结尾）：输入以 `,` 或半开值结尾（如 `"summary": "ab`）→ 拒绝（Q8 缓解的测试化）；
- T19（恢复成功零 correction）：恢复且全链 PASS 时 `correctionDisposition=NOT_NEEDED`、无第二 occurrence（Q11）；
- T20（raw 不可变）：恢复后 occurrence 的 `rawAssistantSha256` 等于原始 stream 消息 SHA、输出文件 SHA 等于 `candidateSha256`（双 SHA 各归各位）。

## 7. Explicitly rejected alternatives

- **通用 JSON repair / 任意 brace matching / repair 库**：引入第二 grammar owner，直接违反 parser 单一权威。
- **message.contains / regex 猜测异常**：违反条件 6，且是本轮所有结论反例（T6 v5）的教训来源。
- **自动补字段/默认值/枚举/业务事实**：伪造 provider 输出。
- **放宽 parser（如容忍 EOF）**：破坏 typed EOF diagnostic → correction 协议的输入链。
- **在 correction 层再做 normalization**：双重 correction owner。
- **新增 raw 落盘文件/durable schema 字段**：超出最小边界（既有双 SHA 足够）。
- **以 T6 v5 作为第二样本立项依据**：证据已被反证（P1-1）。

## 8. Residual risks（若实施）

1. **语义截断接纳**（Q8）：模型恰在可选字段边界停笔时，恢复会接纳语义不完整但 schema 合法的结果——不可完全消除，只能靠结构门收紧（末字符非逗号）、typed disposition 审计、以及 correction 兜底压缩。
2. **gate/parser 冗余漂移**：结构扫描与 Jackson 判定长期不一致未被发现——由 parity 测试钉住。
3. **形态漂移**：provider 未来截断在其他位置（如 depth 2）则完全不救（正确行为，但要有观测字段统计 disposition 分布）。
4. **误读为"provider 已修复"**：恢复 ≠ 输出质量改善；prompt projection 仍是独立必要的。

## 9. 最终判定

- **Boundary Review: WARN**（架构形态可成立；证据基础被本评审推翻一半；时点错误）。
- **是否值得建立 production checkpoint：否。** 维持"任何情况下不自动补 `}`"的现行决定。重评触发：prompt-projection checkpoint 落地并经自然运行后，exact morphology 再现于 ≥1 个独立 run（以原始工件证实），或第三个历史样本被 wire/ledger 证据证实同形。
- **是否需要 durable schema 变化：否**（若实施也无需）。
- **Implementation READY：NO**（方案设计可存档，未到实施时点）。
- **deterministic 通过后允许的成熟度：`ENFORCED / NOT PROVEN`（边界本身）；恢复价值仅 `PROJECTED`，自然样本通过前不得标 `PROVEN`。**
- **下一步唯一允许动作：修正 v15 result 文档的跨样本 EOF 频率段（P1-1）为经原始工件核验的事实——T6 v5 morphology 不符、确认下界为 1 run / 2 occurrences——并按既有决定推进 prompt-projection checkpoint 的架构评审。** 不改代码、不加 correction、不建第二样本。
