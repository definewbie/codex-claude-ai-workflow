# 外部验证材料续作 Checkpoint

状态：ENFORCED / DETERMINISTIC SEQUENCE PROVEN。未调用 provider；245D 只读；不是独立评审报告。

## 目标与范围

P0：闭合测试前源码基准 -> 人工提交固化材料 -> 自测核验 -> acceptance 读取同一材料。提交不是测试通过，也不是批准验收。

沿用 EvidenceCollectionBaseline、evidence_collection_gate/ref SHA、既有 inline/worker/publication 校验、原有 self-test 与 acceptance 契约。仅增加 submit_evidence 声明及专用材料入口，不新增 workflow 节点、网络权限、模型层或通用证据平台。

## 实际生产面

| 层 | 文件与职责 |
|---|---|
| core | HumanGateActionSemantics：submit 与 collect 同样限制到 implementation repair 的正常 self-test 边；scope violation 不可用 |
| ledger | EvidenceSubmission：typed ref/SHA+baseline；RoutingEngine、WorkflowFileLedger：专用提交入口，普通 decide 不可伪装；gate result 唯一绑定并沿原 marker 传播 |
| runtime | ExternalVerificationEvidence：prepare、显式文件固化、有限大小、SHA、声明、锁内复验、幂等；EvidenceCollection：执行前后/冷读核验与 prompt 材料引用；ArtifactContractValidator：自测及 acceptance 的 exact ref membership、发布前复验 |
| CLI | ReviewPrepareEvidenceCommand、ReviewSubmitEvidenceCommand、ReviewCommand；两处动作提示共用 ReviewPacketCliHints |
| projection | HumanReviewGateHandler、console.js：CLI-only 提示，不提供网页摄取或伪 submit 按钮 |
| definition | full/contract/terminal-delivery 与 small-plan-human-review-experiment：只增加已声明 submit -> self-test 边 |
| retention | 两份 implement/self-test prompt：保留引用的原始验证材料再清理；本次提交的材料由共享保存服务固化，不依赖 prompt 保证持久性 |

## 完整状态转换

1. 当前 OPEN repair gate，CLI prepare 在锁内核验合法动作、已批准合同、完整源码字节身份，写内容寻址准备记录。gate/current 不变，不调用 provider。
2. 操作者在同一源码上执行验证，声明命令、环境、起止时间、退出码与用途。时间和执行事实是人工声明，不是机器证明。
3. submit 先验证准备 SHA 与源码，再复制显式材料至本 workstream 的 artifacts/verification；最多32文件、单个8MiB、合计32MiB。拒绝 symlink 与非普通文件；不扫描 HOME、target 或其他目录。
4. 获取 workstream 锁后重验准备、材料、源码、当前 gate；专用 ledger 方法先写 gate 决定，再写 node 绑定，再按定义路由至 self-test。上传本身不输出 COMPLETE/ACCEPT。
5. inline/worker 的既有执行边界验证 gate SHA、准备 SHA、材料 SHA、合同及源码；运行后再验。自测与 acceptance 的 result.evidence 必须逐项精确包含 manifest ref 与所有材料 ref，摘要、substring、相似文件名均无效。
6. sidecar 发布前再次验证。acceptance 经同一 marker 获取并读取相同材料；sidecar 的 evidence 保留相同 ref。材料不足仍由现有自测/验收语义决定，不通过上传改写决定。

## 失败切面

| 切面 | 结果 |
|---|---|
| 准备/材料写入失败或源码漂移，决定前 | gate 不变；可能有孤立内容寻址文件，无人消费，不授权执行 |
| gate 写后、node 写前失败 | 原有部分状态显式停止；重复 submit 不恢复或重抓基准 |
| node 写后、route 前失败 | 检查不到已持久化 collection route，拒绝幂等成功，不重复 routing |
| 完整决定后同内容重交 | 核验原决定及 route，返回原引用，事件数不变；异内容拒绝 |
| retry/冷进程读取 | 从持久化 route/gate/ref 读取，准备/材料删除或篡改即拒绝 |
| provider 前/后或发布前变化 | typed EVIDENCE_COLLECTION_SOURCE_INVALID，零有效 sidecar；不回滚源码掩盖修改 |
| revise/终止之后提交旧准备 | 当前 gate 身份不符即拒绝，不恢复旧 case |

## 不变量与证明

- Declared by：workflow action + typed EvidenceSubmission + 人工 note。
- Proven by：准备 baseline、manifest 和各材料 SHA、原始源码字节摘要。
- Consumed by：EvidenceCollection 的已有执行守卫及 ArtifactContractValidator；两个结果类型使用同一 exact membership 检查。
- Enforced before：provider 执行、结果发布；submit 锁内决定前复验。
- Failure route：typed拒绝/部分状态停止；不使用模型正文或文件名推断路由。
- Cannot-bypass：真实 Git dirty/untracked 反例、篡改/删除/大小/路径、冷 JVM、部分 durable write、inline/worker 实际 sidecar序列及四 definition parity。

## 使用

先在目标工作区运行 `review prepare-evidence --workdir WORKSPACE WORKSTREAM`，取得准备 ref/SHA。验证完成后运行 `review submit-evidence`，提供 `--workdir`、`--preparation-ref`、`--preparation-sha256`、`--request-id`、重复的 `--file`、`--command`、`--environment`、`--started-at`、`--finished-at`、`--exit-code`、`--purpose`、`--note` 与 workstream ID。起止时间为 ISO instant；文件使用 canonical 绝对路径。命令不会执行这些声明里的测试命令。

提交成功只路由到 self-test；使用已有 `run` 继续。此入口不额外提供自动运行开关，以免幂等查询顺便启动下一次执行。

## 诚实边界

保存字节及源码绑定可以机械核验；人工是否确实执行所声明命令、材料是否充分，仍须自测/acceptance审议。不能据此宣称材料内容天然真实。

本轮材料复制由共享服务强制；一般 agent 测试输出的清理纪律仍是提示，不冒充自动化报告归档平台。源码守卫比较检查点身份，不声称监控了每一次瞬时文件写入。部分写入 fail-closed，不新增自动修复或历史样本救援。

历史 definition 未声明 submit 时直接拒绝，不迁移旧 ledger；245D 的原始 definition 不因更新主仓库而自动改变。

## 验证与自查

- 全模块离线回归 BUILD SUCCESS，1476/0/0/0。首次回归暴露的旧边数/动作集/资源SHA断言与裁剪fixture已按新增submit声明同步，保留原一致性断言。
- 随后加强同一个运行时序列，加入正式 `context.acceptance_codex_verifier_review` 与 required_agent_evidence 消费；补齐fixture的真实run目录身份后复跑通过。此增量只修改测试，无production放宽。
- 加强后的 ExternalVerificationEvidenceTest + EvidenceCollectionSequenceTest 聚焦复跑为15/0/0/0；CLI离线重新打包成功，新JAR的 `review prepare-evidence --help` 与 `review submit-evidence --help` 均退出0，命令与必填参数注册正确。
- inline/worker均通过生产WorkflowRuntime/ArtifactContractValidator发布self-test与acceptance sidecar；两者 evidence 为同一manifest+材料ref。只返回摘要或相似路径时停WAITING_FOR_RECOVERY且不发布sidecar。
- 其余反证：准备SHA/内容篡改、dirty/untracked同porcelain源码变化、材料删除/篡改、源码后改、冷JVM、旧准备/普通decide、symlink（含存储目录外写反例）、单文件/总量/个数上限、完整提交幂等、两处gate部分写入失败；四definition的collect/submit均只允许正常self-test后继。
- `git diff --check`与console JavaScript语法检查通过。真实provider、浏览器、外部API未调用。自然 real-case 未执行，不能标记 NATURAL E2E PROVEN。

Boundary Review（主agent自查）：PASS。prepare不授权执行；submit唯一持久化决定在gate node；后继必须验证同一marker与全部字节身份；两个结果类型发布前精确引用；部分状态不猜测恢复。未引入业务标识、模型/provider特判、第二证据扫描器或自动审批。

本次教训：单测中的自测->验收直连不足以代表生产链，需要包含context及真实sidecar/run lineage。加强序列先发现fixture路径身份不一致，再修fixture，不能为让测试通过而绕过production verifier。
