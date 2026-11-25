# B04 评审导航与证明边界

这是导航，不是独立结论。评审应自行重建控制链，允许推翻作者判定。

| 契约/主目标 | owner 与强制消费者 | 可复核证明 | 尚未证明 |
|---|---|---|---|
| 真 ask 与实际一次选项 | acpx client SDK requestId/signal → QueuePermissionReplies；原 resolver 自动路径不变 | client.test / permission-replies.test / permission-cli.test；本地假 ACP peer、真实 SDK/CLI/Unix socket | 安装后的 Claude Code/GLM 与 Codex 实际行为 |
| 人决定零发送资格 | NativePermissionRequests R 请求/A → L C唯一消费 | NativePermissionSequenceTest 正向/错选项/重复决定/漂移；CLI/API同owner | 对抗恶意同用户进程的独立安全保证（产品不承诺） |
| C/X同L排序 | ExecutionLedgerWriter/WorkflowFileLedger → 原 live sender | before_manifest 实际barrier；8个C +8个X前缀；cold子JVM；sibling 7入口；duplicate-C隔离反证 | 跨部署/多ClassLoader及未授权外部平台 |
| 原callback一次消费，零隐式重送 | queue permission control →原 pending Promise；Java send only fresh C | 实际CLI批准/拒绝继续；真实本地IPC丢回执仅一次连接；faketransport UNKNOWN/cancel；persistent/wrong ids拒绝 | 真实原生回执或真实工具退出码 |
| 研发主流程保持成立 | 实际AcpxAgentTaskHandler inline/worker →R03/唯一outcome/业务sidecar/自测/独立Codex角色验收→人工交付门 | HumanRequirementContinuationSequenceTest#nativeOnceChoiceContinuesThroughIndependentAcceptanceOrStopsWithoutPublication：真实批准/合同/handler/ledger消费者 + 合成原生权限消息 | 不是一条真实provider端到端运行；IPC实际接通由另一组本地peer用例证明 |
| 恢复不误导 | 同一typed outcome →RecoveryAssessment/D07；现有stop先X | RecoveryAssessmentTest 从真实resolver输出消费native_permission_interrupted；冷JVM零新许可、unknown零补发 | 真实外部停止是否已发生（未确认即不宣称停止） |
| 统一呈现 | NativePermissionRequests.view →CLI/API/summary/共享UI renderer | NativePermissionCliTest、NativePermissionApiTest（BUSY/PENDING）、ui-projection-test.cjs | 浏览器运行/视觉与真人交互（NOT RUN） |
| 受维护补丁可重建 | upstream diff → tools patch/manifest →configure guard/ReviewedAcpxBuildIdentity | patch-replay.json 44文件；built-acpx-identity.json；native-verification.json | 未运行installer，用户安装仍可能旧patch，真实Gate前须更新/核对 |

R记录：request/decision/send-intent/send-result是不可变材料，不是另一个授权账本。C/X事实仅在既有execution.result.permission_reply。requestOptions有限总预算是既有配置绑定；默认0不开在线桥接。人同意不代替scope、独立复评、D05证据或交付门。

已查兄弟入口：inline/worker共享handler；correction deny-all不桥接；宿主fs/terminal同确认入口；acpx cancel/close/mode/model/config change关闭pending；Dual retry/abort/iteration/revise/readiness/gate/claim替换不可变更live资格；结束/失败/stale与恢复裁定保留并关闭C/X。核心移除fresh区别的隔离反证必须响亮失败；不从disk、旧C或新owner产生发送资格。

证据按层拼接，不能把合成消息、本地模拟进程与真实原生/provider能力合并成一种证明。详设中的原生安全沙箱义务已撤销，不恢复硬链接/内核/任意命令对抗实验。

## 信任边界需明确核对

acpx 校验固定回复里的原请求身份、option、generation、期限与一次消费，并对 commitRef/SHA 做关联格式校验；不跨读 Java 账本，不独立验算C。C是否合法由同L的Dual owner及原live sender强制。本批证明的是Dual入口不可借旧C/错请求授许可，不提供同用户恶意直接调用原生CLI的独立认证或沙箱。请评审核对这是否符合§8.25依赖边界与冻结协议，不默认将其包装为任意外部入口不可绕过。
