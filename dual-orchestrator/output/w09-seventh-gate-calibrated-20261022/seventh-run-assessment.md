# W09 第七例：启动验证失效与会话启动超时

## 结果

**Gate：INVALID。领域事实：WAITING_FOR_RECOVERY，唯一 primaryReason 为 `session_bootstrap_failed`。** 两者分列，不改账本、不把缺输出诊断改写成模型错误，也不以此宣称 Dual 产品缺陷已证。[机器结果](seventh-run-result.json)。

已按用户当次授权执行启动前只读重核，记录新授权，再次核验后通过原 runner 推进 context 与 intake 作者节点。静态身份、659 输入、发行、配置、初始实例及预算检查均成立；但进程检查的 PASS 有一处无效，详见下节。本次真实启动未走到任何业务批准门。

## 已确认事实与未知

1. context 步完成。作者节点只固定了 `build_identity` 与 `session_ensure` 两类命令结果；前者匹配受审 patch，后者约 45 秒超时、输出为空。没有 prompt 命令结果/派发或 request ID，不存在模型已返回却缺 JSON 的证据。run 已固定为可恢复 adapter 失败，摘要同源。
2. 用量类型是 `UNAVAILABLE`，provider/model/billing 保留 zhipu / glm-5.3 / subscription_token_only。没有 prompt 派发记录与实测 token=0 是两件事；不能把缺用量的数值槽零当作实测零或预算 authority。
3. 启动前 `pre-dispatch-recheck.py` 对已知旧 PID 的判断仅检查 `ps` 退出 1 且 stdout 空。当前受限执行环境下，`ps` 因 Operation not permitted 同样返回 1/空 stdout；其 stderr 未检查，因此该前提被误判通过。这是作者启动核验的错误，不能宣称整个启动前提已被证明，故整体 Gate INVALID，而不是用事后事实补证为有效实验。
4. 停止后用获准的只读进程观察成功读取进程表（exit 0），17 个旧具名会话 closed、58 个旧 PID 及两次本例 runner PID 均未观察到；没有本例 workspace 的持久化会话记录。[停止观察](strict-post-stop-observation.json)。此证据有时间/范围限制，不宣称任意子树或远程执行全部停止。
5. 会话创建为何超时尚不确定。受限进程观察是确认的环境限制；网络、缓存/文件写入权限、客户端启动等待等不能据此定为根因。没有借重试或新 native 探针判断状态，没有升级/安装/换入口/改超时。

## 分级与处置

`session_bootstrap_failed` 本身可属于会话瞬断类 TRANSIENT 候选，但本次没有证明安全恢复前提，且启动检查参与了执行准入：不能把错误的进程缺席证据当作允许自动重试的依据。外部状态/启动前提无法按原观察链证明时停止；这不等于已断言产品 contract、权限或账本存在缺陷。

若进程检查只用于展示，其错误可作为第二类观测债；本包将其用于启动准入，故不能仅登记后忽略。需要离线修正成功读取/进程不存在/权限失败的区分，并重新确定启动环境检查。此项尚未实施；无产品代码、observer、runner 或发行改写。

已写 `stop-required.json` 禁止后续 runner；原实验不恢复、未启动新 case、旧余额不转授。两步活动时间约 48.188 秒，不是预算耗尽；请求数和用量不据此虚填为发生过的模型调用。运行授权是此次操作的历史依据，不能绕过停止标记。

## 证据保全与边界

完整停止账本已复制至本包非临时目录 `stopped-ledger-snapshot/`，逐文件核 SHA；原账本不修改。原输入 659 项、两份冻结 JAR、29 项当前装配、observer/Gate 输入再次一致。此前21/12离线对照与1813/210凭证保持历史证明边界，不能覆盖本次进程观察缺陷。

- Declared by：用户当次真实运行授权、原 Gate 启动前提与储备规则。
- Proven by：原命令结果、run/completion/current、步骤耗时、受限 ps 错误及后续严格本地观察、停止快照。
- Consumed by：原 runtime 固定 adapter outcome；observer 不生产业务或用量事实；原恢复摘要消费唯一 outcome。
- Enforced before：会话启动失败后未进入 prompt；停止标记禁止下一派发；启动前进程准入证明本次无效，明确标记未闭合。
- Failure route：原 WAITING_FOR_RECOVERY / MANUAL_ONLY 保持；外部不明不重试，INVALID 不包装部分成功。
- Cannot-bypass：本次实际停止、无 prompt 身份与无后继；尚需离线反例证明 ps 权限失败不会被误认缺席，不能以源码局部检查替代该义务。

## 下一步

**先离线修正启动核验的进程读取判别，并核对原生会话启动需要的运行环境；不以新模型请求试错。** 当前根因未确定，不能直接扩超时、换模型或启动第八例。修订候选明确后再按影响范围审查与确认后续运行路径。本报告不是修复或新运行授权。
