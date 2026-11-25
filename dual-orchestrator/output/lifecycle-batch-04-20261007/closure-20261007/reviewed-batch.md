# B04 原生权限请求与合法续作 · 实施批次

当前状态：IMPLEMENTED / OFFLINE_VERIFIED / AWAITING_CC；实施及当前源码单轮完整 clean 回归已完成，B04 尚未独立评审收口。作者自查不是 CC 独立判断。用户显式批准进入实施；不提交、不安装、不更改用户配置、不启动额外 agent，真实 native/provider/E2E 的 NO-GO 保持。

## 目标与范围

补齐原生客户端真正 ask 后的合法续作：原请求→人工决定→同 L 下 C/X 排序→原存活调用一次回复→执行/拒绝→恢复→业务自测与独立验收。用户可看到真实待决操作并选择其原生一次选项；决定仍需原执行核验，结果不明不自动重发。推进 W03/W05/W08，复用原客户端规则和既有执行/ledger/run owner；不建设安全沙箱、命令分类器、LLM 越权裁判、权限平台、DSL、新独立锁/服务或 Rust 机制。“80% case 优先”是优先级方向，不是已测成功率。

默认总调用预算 0 不启用在线等待。只有显式配置既有 `DUAL_ACPX_COMMAND_TIMEOUT_SECONDS` 为正数，才在固定总预算内开放桥接；人工等待不制造额外无限时间。correction 保持 deny-all。原生兼容版本及环境入口共识不变；此次严格更新的是本项目维护 patch 身份，未改为固定 Claude/Codex 版本白名单。

## 已落地的联合链

1. acpx SDK requestId/signal 与原 session/prompt/generation 关联。自动许可、明确拒绝沿原 resolver，只有真实 ask 进入 exact once 选项回调。host 文件/terminal 确认同 lane，不用自然语言猜批准，不 fallback 为更宽权限。
2. `NativePermissionRequests` 沿 R owner 固定原始请求与人决定 A（ref/SHA）。CLI/API 保存 A 零发送资格；按真实 offered once 选项选择，不能用模型正文、旧 id 或他人请求制造决定。
3. 同一 L 写会话重新核对 claim、当前节点/合同/输入/epoch/期限，然后 D06 提交 execution 中的 C 与事件。撤销 X 也取 L、单向关闭并保留 C；完整新 C 仅向原调用返回一次 fresh payload，既有 C、固定补齐或冷启动重读都不发许可。
4. 原调用记录发送意图后核对 X/期限，现有 owner 控制 IPC 仅尝试一次，不启动新 owner、不重连重发、不建立 outbox。C 后发送前本地短 BUSY 可在原调用内暂存其 fresh payload；一旦外部尝试开始，记录失败/断线/超时一律 UNKNOWN，不能回到资格获取或自动重发。C-first 的单次在途语义已由用户明确接受。
5. 原 callback 在兑现 Promise 前同步核对身份、原始请求 SHA、once 选项、期限、取消与 consumed 标记。ACK 只说明该选择被消费，不证明工具已执行、通过验收或已交付；R03、唯一执行判定、scope、D05 证据、自测、独立 Codex 角色验收及交付批准继续守原规则。
6. CLI/API/report/两套 UI 消费中央 view，区分待决定/决定保存/C 已提交可能在途/回执/停止未确认；BUSY、pending 与历史请求不提供当前动作。非跨 L/R 全局原子快照；真正动作仍重新核验。

完整写序、每个 durable-write 前缀、重启/replay/abort/恢复及兄弟路径见 [转换与失败切面](transitions-and-siblings.md)。Definition 是原生 options 和批准合同；Instance 是当前节点及 claim；Execution 是 C/X 与 run 准备事实；R 的请求/A/观察是材料，不是第二授权账本。唯一 C owner 在 L，唯一对外消费者是原 live sender 与原 queue callback，projection 不赋权。

## 验证与过程证据

最终凭证：[test-results.json](test-results.json) 与 reactor-final.log。2026-10-07 22:09:39–22:25:19（+08:00），8 模块一次 clean test，1,701 tests / 204 suites，0 failure/error/skip，exit 0；945 项构建及测试输入前后无变化；204 份原始 XML 已归档于 final-reports/，身份见 final-report-sha256.json。不能消费上一轮 1,699/204 的 FAIL，也没有拼接定点复跑宣称单轮绿色。

- 原生维护源码完整离线 check：1,005 tests，零失败/错误/跳过；coverage 143 是其中子集重跑，不能相加。真实本地 SDK/CLI/Unix socket peers 测 allow/deny、丢回执一次连接、过期/取消/重复 RPC、多请求分项、host 合法与禁止；不是 Claude Code/GLM 或 Codex provider 运行。见 [native-verification.json](native-verification.json)。
- C/X 32 项序列含 8 个 C + 8 个 X 写入切面、真实 before_manifest barrier、冷子 JVM、7 类资格变更兄弟入口、R 写失败与合法出口。隔离移除 fresh 区别必须出现重复 C 失败；移除实际 native reason 映射必须出现恢复提示失败。见 counterproof/ 与 recovery-counterproof/。
- Java transport 6 项真实本地模拟执行，覆盖批准、拒绝、不明、取消及发送前/后 BUSY。新增两 BUSY 反例先红后绿，不把旧 fixture 错误当生产反证。
- inline/worker 的真实业务 ledger/handler 消费者各测 allow/deny：合法选择后经过自测、另一客户端角色独立验收到人交付门；这些用例的必需操作被拒后，没有被拒产品变更或业务成功发布；已批准的 D02 非必需拒绝容纳规则没有改变，仍须其精确证据。原生消息为合成协议，本地 IPC 实接由另一组 peers 证明，不冒称一条真实模型 E2E。
- CLI/API 与两 UI 的投影同源检查通过；UI 为 Node VM DOM substitute，不是浏览器运行或视觉验收。
- 维护 patch 从干净 acpx 0.13.2 upstream 重放，44 个文件字节比对通过；当前 patch SHA `1517f828d303ea720a7a153e6dc8c33265ba1ea100d2b9260c98327b5604468c`，manifest/configure/ReviewedAcpxBuildIdentity 及本地 build 身份同步。没有运行 installer，既有安装登记保持原状；真实 Gate 必须检查实际选定装配。

[验证过程](verification-history.md)保留 INVALID/FAIL 和纠偏，包括 SDK fixture/错误命令/沙箱限制、并用 target、错误 blocking 分类判断及 SSE 测试线程清理错误；不以最后绿色抹去过程。源尚未稳定的轮次不作当前凭证。

## 证据索引与六维

评审导航见 [review-map.md](review-map.md)，源码身份见 implementation-source-index.json，增量见 changed-files.json / incremental.patch。以 `implementation-before-source-text.json` 的真实 dirty 前态比较，不是 HEAD diff；早先 PREPARED 基线按时点保留。`docs/reference.md` 的前态另以逆向重建标记，不冒称开工前测量；未变化的上游文档不列为新建文件。

Declared by：冻结 D04 §8.25/§8.27、D06/D07 受影响协议及用户实施/在途取舍。Proven by：分层离线 peers、业务序列、写前缀/冷启动/barrier/隔离反证和当前稳定源码的单轮完整回归。Consumed by：L 的唯一 C/X→原 sender→原 callback→R03/唯一 outcome→原业务验收/恢复；view 只读。Enforced before：C 前资格材料、RPC 回复前身份期限取消、业务采纳前 scope/证据/独立验收。Failure route：D06 固定本地补齐；D07 外部不明人工裁定，零自动重发；拒绝/过期不 fallback。Cannot-bypass test：重复 C/错误身份/多请求/失效入口/冷启动/发送前后 BUSY 与合法正向成对。

## 限制与剩余工作

acpx 对 commitRef/SHA 是关联格式检查，不跨读 Java ledger 或独立认证 C；C 合法性由同 L 的 Dual owner 与原 sender 守住。产品依赖本机客户端，不提供同用户恶意直接调用 native CLI 的安全沙箱保证，不能把此批说成任意外部入口均不可绕过。

本批不证明真实客户端权限/停止/工具退出码、所有未来版本兼容、浏览器 runtime、目标 Git 交付、外部部署/多 ClassLoader 或 kernel I/O 专项；整体 W 项及最终真实 Gate 仍未完成。首版全量声明限制及运行中补声明入口未证口径不变。未恢复历史样本，未扩大网络 R4/Phase B。

Analyze→Modify→作者 Review→Reflect→Analyze 的纠偏记录见 [反思与再次分析](reflect-and-reanalyze.md)。最终凭证现已齐备，只进入 B04 合并 CC 评审，不自动 CLOSED、不自动开下一批或真实运行。提示词见 [cc-review-prompt.md](cc-review-prompt.md)。
