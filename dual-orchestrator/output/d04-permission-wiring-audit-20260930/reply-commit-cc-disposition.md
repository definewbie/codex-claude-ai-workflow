# D04 提交协议 CC 评审处置（2026-09-30）

用户带回 **设计层 PASS，附冻结条件**。原 `reply-commit-protocol.md` 及取证包不改。本补记登记处置与 LOW-2 定点源码核验；不是新的独立评审，也不授权实现/运行。

## 1. 冻结条件

- CC 已认可 C/X 同锁排序、现有 owner/写集与一次尝试方案。此为设计认可，不是运行证明。
- D06 的 execution 提交/关闭 mutation、失效入口同写 closed、完成 writer 保留事实，以及 D07 的外部窗口/恢复规则，已并入详设 §6.5/§7.2；属于本轮 CC 指定的受影响增量，原冻结附录保留历史。
- **用户仍须确认在途语义**：撤销先提交时不产生新许可；批准回复先提交时，该单请求、精确选项/范围、有效期限内的一次尝试可能在撤销后送达。撤销阻止新的提交并请求取消，不能保证追回旧提交；结果不明不自动重发，旧执行停止未确认时不启动替代执行。
- 确认语义后才登记 D04 与该受影响增量冻结；确认本身不授权实施，实施须另行明确授权。不把 CC 末句“并批准后续批次”混成设计冻结前置。

## 2. LOW-2：IPC 重试入口核对与接线规格

仅静态读取当前固定 acpx 源码：

| 入口 | 已核事实 | 必须落实的接线 |
|---|---|---|
| `ipc-transport.ts:55–81` `connectToQueueOwner(owner,maxAttempts)` | `maxAttempts` 已可传入；默认 40，传 1 只进行一次连接尝试。循环发生在连接阶段，尚未发送请求 bytes；不能将连接重试本身说成命令重送 | 新 permission reply 路径显式传 1；不修改其他命令默认策略 |
| `ipc.ts:201–305` `runQueueOwnerRequest` | 当前第 208 行未透传次数；第 303 行只有一次 `socket.write`，error/close 交 reject/handler，此函数没有重新连接或重发循环 | 在原 helper 增加命令级参数并透传，reply 指定 1；一次写后任何不明不重新调用该 helper |
| `ipc.ts:468–534` `submitControlToQueueOwner` | 仅调用一次 helper，但多种错误携带 `retryable:true`；这不是已执行重试，也不是安全重试证明 | 新回复命令不得让上层通用重试器消费该布尔值重送；将该次固定提交关联的失败转换为不自动重试的类型化事实，已进入写入窗口而结果不明走 D07 |
| `ipc.ts:796–827` `tryCancelOnRunningOwner` | 无结果后的 health probe 不重发 cancel；不构成新 permission reply 能力 | 新回复入口不走 owner bootstrap、owner 更换、session重建或 prompt-retry fallback；单次失败不申请新提交替代原命令 |

结论：已核入口支持在现有 patch/helper 内表达单次连接与单次命令交付，无须替换 IPC。**新命令及参数透传尚未实现，完整调用链和原生行为未运行；不是“已有开关已经接通”。** 将断连前/写入中/回执丢失、错误标为 retryable、owner 重启及错误 generation 的反证纳入原协议 §6 / wiring audit §7。若实现发现其他层强制重试且无法按命令关闭，停止，不把该限制改写成自动重发许可。

## 3. 对 CC 两处表述的限定

- “deny 任何时候送达都安全”过宽。X 后允许的是对正确、仍可取消的请求做 deny/取消清理，不是向任意旧 RPC 回复。仍核完整身份、owner generation、存活/取消及消费状态；已终止请求不复活，错误身份不发送。非授予型决定也可能终止错误任务，不能豁免关联校验。原协议已经限定这一点，不改变协议去迎合 INFO。
- “每个失败前缀零 allow 泄漏”只可理解为零**未经合法提交**的 allow。C 先于 X 的在途回复仍可能被消费；它是待用户接受的明确窗口，不能被总结文字抹除。

## 4. 自查与证据上限

边界自查：设计处置已完成；D04 冻结仍等待用户决定。Definition 的原生选项不改；Instance 的业务资格由 L 决定；Execution 的 C/X 归 execution 文档，外部请求/决定/观测归 R，统一投影不生成许可。

六维沿用受审协议：Declared=§8.25/协议及待确认在途语义；Proven=静态源码与设计推演，NOT RUN；Consumed=L、live sender、acpx 回调、D07/统一投影；Enforced before=拟在 C 前同锁核资格、回复前核原生请求；Failure route=D06 本地补齐、D07 外部不明；Cannot-bypass=原序列加本节重试反证，均待实施验证。没有把设计 PASS 写成 ENFORCED/PROVEN。

本次零 native/provider/额外 agent，无产品代码修改；未覆盖实际成本、全部 patch 实现、运行竞态和性能极限。不新增独立实验、平台或材料轮次。

### 定点源码身份（SHA-256）

- `.tools/acpx-e1/source/src/cli/queue/ipc-transport.ts`：`df1cb0ab118a969ff74c2c786cdf4bcfc98ba8eda8dd9489575c9b356df436d0`
- `.tools/acpx-e1/source/src/cli/queue/ipc.ts`：`8b73437883f6149d73d25f6b5b40094c19ccccba7bf5ab7843eabf397af8a58f`

## 5. 用户确认：冻结生效（2026-09-30）

用户在得到具体撤销例子与“只确认设计，不批准实现”的解释后，明确答复：“我肯定接受，这个建议合理”。据此 LOW-1 已满足，D04 及 D06/D07 受影响增量登记 DESIGN_FROZEN，详设与主清单同步。前述待用户决定/待确认描述保留为处置过程；原协议及源码证据不改，合法请求身份校验和未知不重试等约束不变。

本次仅登记已给出的用户决定；不申请或启动新实验，不授权实施、合入或真实运行，不将设计冻结记为产品已 ENFORCED/PROVEN。
