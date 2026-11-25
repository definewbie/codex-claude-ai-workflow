# B03 · W03 原生兼容接线（2026-10-07）

B03 当前 **CLOSED / PASS**，已收到合并批末独立判断，见 [收口处置](../closure-20261007/disposition.md)；W 项及真实 NO-GO 不变。用户同意环境变量/既有入口和兼容升级，版本不同/未知不单独阻断。本轮按 boundary-first-engineering 实施及主 agent 自查，未启动额外 agent，未调用 acpx/native/provider，未提交。

## 对总体目标的贡献与实际接线

消除 W03 把动态启动身份推成固定安装平台的阻塞，将普通 Claude Code/GLM 作者与 Codex 评审的启动输入、会话观察、typed 失败和恢复接进既有 R01–R03。没有创建 Java npm/config resolver、安装包平台、版本兼容矩阵或安全沙箱。详设 §7.4 是用户决定后的当前边界；此前固定原生字节候选未实施，不再是隐性前置。

- transport 生成选定入口/agent/canonical cwd/session/PATH/该客户端原生路径变量，handler 固定进 R01，允许 primary 与 contract-correction 请求配置；原业务 claim、合同批准及材料 SHA 继续独立强制。
- 每 native command 的 intent 在启动前由 AgentRunCommitProtocol 唯一核对 invocation/请求配置；普通命令还核对原 live executor、未释放/未完成。结果匹配该 intent；cleanup 只能精确 cancel/close，不产生 prompt authority。
- 首次、rebuild、correction 统一在 run 前读既有 JSON sessions show。固定白名单摘要、核记录 schema/identity/cwd/session/open/协议1；未知可选能力不误杀。native version 缺失明确 UNKNOWN，兼容版本标签变化可续作，不编造已测版本范围。
- session 命令失败保留 session_bootstrap_failed；结构化不兼容保留 adapter_incompatible 到唯一 outcome 与 RecoveryAssessment，零后续 prompt；真实 handler 的 pre-prompt 失败不引用不存在 transcript。完成投影保留已固定 native 诊断。
- 已确认 R03 在独立 JVM 从原 fixed 内容读出并补本地，换新入口也零新 native 命令；UNKNOWN 的旧调用不从 R02 重获发送权。新准备可选择新入口，旧 stop 不能猜新入口的旧会话。

**sessions show 是上次记录的握手，不是 live queue/实际原生启动包字节或全部未来接口兼容证明。** 运行接口错误、证据要求仍按原消费者处理；明确要求实际退出码的条款不被版本政策放宽。原生全量字节/任意配置完整测量已撤出本批义务，未伪称做完。

## 控制链与六维

完整 transition/write order/failure prefixes/siblings 见 [transition-plan](transition-plan.md)，本轮相对真实 dirty 前态的 [18 文件增量](changed-files.json) 和 [patch](incremental.patch)；未还原或覆盖用户原改动。

| Declared by | Proven by | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|
| 用户兼容决定；详设 §7.4；D03/D06/D07 | 配置环境独立 JVM、真实 handler 拒绝/R03 独立 JVM、协议/命令/持久化切片 | transport → R01/per-command → resolver/RecoveryAssessment；R03 reader | native command 的 R01 guard；prompt 前的**记录身份/协议**检查，非实际 live 引擎安全保证 | bootstrap/incompatible/I/O 分立；无可信外部结果不重发 | 缺 R01、错 session/入口/options、释放后发送、cleanup 伪 prompt 拒绝；版本未知/改变、correction、撤销后 cleanup 合法对照 |

现有 inline/worker、四 variant、GOAL/EXECUTION、验收/补证据/重批、UNKNOWN/终态与读写前缀仍由全模块序列回归承接，不据局部新增类外推整链闭合。

## 验证与纠偏

最终结果以 [verification.json](verification.json)、[reactor-clean.log](reactor-clean.log) 和 reactor-reports/ 原 XML 为准，必须单轮 clean 完成且身份无漂移。此前 1,631 或 focused 绿色不替代此轮；第一次 reactor 主动中止为 INVALID，原文件以 aborted- 前缀保留，未执行模块残留报告不能合计成覆盖。

先红、编译/测试作者错误、实际诊断缺口和兄弟夹具修正逐项分类在 [reflection](reflection.md)。聚焦最终 `focused-final-2.log` 全绿。新 protocol 帧/版本标签为合成消息；独立 JVM是真实本地 Java，均不冒称真实原生运行。

## 合并评审与尚未完成

入口是 [B03 合并评审索引](../b03-review-index.md)，本次不单开材料评审轮次。原生权限真实回复 C/X、真实工具/客户端能力与 E2E、浏览器 runtime、目标 Git 交付等保持总体未竟或批外；W 项不能因 B03 离线切片自动关闭。首版全量声明规则仍是已接受限制，运行中补声明具体入口未证明，不扩充声明平台。内核 I/O 故障/多 loader 部署限制沿既有记录，不以本轮冒领。

## 最终离线凭证（2026-10-07 15:25）

本轮单次 `clean test` 完整通过：8 个模块、199 个 suite、1,656 项测试，失败/错误/跳过均为 0，exit 0；2,985 项输入前后 SHA 无漂移。原 XML 已复制到 reactor-reports/，详细模块统计见 verification.json。零真实 acpx/native/provider，零额外 agent。本结果是离线回归 PASS；B03 仍为 IN_PROGRESS_PENDING_INDEPENDENT_REVIEW，待合并批末独立判断，真实 Gate 仍 NO_GO。此前主动中止的 reactor 仍为 INVALID，不与本轮拼接。

## 合并批末评审接收

2026-10-07，CC 合并独立评审 PASS、无新增 WARN/FAIL；本批授权范围与离线验证收口。上文最终凭证中的待评审状态是测试结束时的历史状态；原 verification.json 不回写。当前状态由 closure-20261007/closure.json 与主清单 §17 登记。没有新增实施、提交或真实运行授权。
