# B04 完整转换与失败切面复核

L=既有workstream会话/锁，R=既有run材料锁。二者不嵌套；人工等待、native/IPC调用不持L/R。Definition=原生options及既有业务合同；Instance=当前node/task/iteration/claim；Execution=C/X与run准备事实。所有许可结果不生成新业务批准。

| authoritative pre-state / action / owner | durable顺序、post-state与消费者 | 写前/间/后失败、重启与合法出口 |
|---|---|---|
| 当前批准/claim；handler dispatch | L核资格→R01固定配置/合同/输入/有限预算→L绑定prepared→R02外部intent→原客户端 | 无固定准备零外部；intent后不明走D07；默认0不开放在线等待；未知原生版本不单独拒绝 |
| 原生自动许可/明确deny/真ask；client resolver | 原规则resolver保留；真ask才建原RPC/signal/generation/prompt下的内存callback，通知Java | 新exact lane不fallback；旧嵌入API是上游既有接口，Dual进入ask不使用它；宿主fs/terminal合法与禁止成对 |
| 活ask；R请求owner | L当前核验并释放→R不可变request+SHA→统一view可呈现 | R失败零A/C/IPC；短L BUSY有限等待而非重发；请求过期/错schema/无预算不动作；read model没有授权 |
| 人选择原一次option；CLI/API→R | 读L并释放→R固定request ref/SHA/option/note→不可变A | 同键同内容幂等，异内容拒绝；保存决定零发送；原executor消失/请求结束不能假回传；多请求逐项 |
| 原executor消费A；L C | 先R取固定引用并释放→同L重核claim/合同/输入/epoch/期限→prepared写execution.C+event→完整commit返回freshpayload | 各前缀失败零新payload；pending读拒；D06固定补齐不返回fresh；冷JVM/已有C零新发送；不把当时允许choice当所有后继前置已过 |
| 资格撤销/取消；L X | 与C同一L写session.closed，不清C→提交→原sender停止核对 | X-first零C；C-first可能一条在途（用户已接受）；失败before_manifest不捏造X；其余固定补齐X，不伪造已停止 |
| fresh C；原live sender | R send-intent（不是grant）→再查X/期限→一次IPC命令/一次连接，maxAttempts=1，无owner恢复/outbox | intent写失败零发送；IPC前短L BUSY只在原live调用内保留其fresh payload并在有限预算中等待，不重新取得C；IPC开始后锁忙/缺回执/IO/观测失败均保守UNKNOWN，绝不重发；cancel/close清理不授allow |
| 同原生活callback；acpx owner | 同generation/prompt/session/RPC类型/rawSHA/deadline/精确once option校验→同步consumed标记→Promise→原ACP response | 错/重复/重用/新owner/终止/持久选项拒绝；发送失败不reset消费；deny与取消分立；implicit retry不适用于reply命令 |
| callback回复 / turn结束；R→业务消费者 | R外部观察/原生tool observations→R03固定→L03唯一execution outcome→合格sidecar→scope/self-test/独立reviewer | ACK只是消费选择；必需权限被拒或外部UNKNOWN不得冒充成功；可容纳的非必需拒绝仍须既有D02精确证据，不由回执推导；scope仍事后业务校验，D05仍证据身份核对，不偷换写前保护 |
| 完成/失败/stale；execution owner | 同L结束并close C/X保留诊断→既有后继/恢复 | 后续不得grant新许可；stale/未知先核外部状态；原Promise不恢复，新attempt不得和未确认停止者重叠 |
| 重启 / replay；D06/D07 | 补固定本地字节；原始C/A历史只读；当前运行身份严格核验 | 不重启旧sender或callback；UNKNOWN人工裁定；旧不支持Dual身份/schema INVALID，不救旧样本 |
| 查询 / UI / report | 独立L快照释放后R读取，暴露commitSequence/current/状态；application中央view统一 | 不声称跨L/R全局原子快照；BUSY/PENDING无动作；历史没有新发送资格；CLI/API错误不dispatch |

## 兄弟入口处置

- inline/worker实际handler共享R01–R03/sink/唯一outcome，新增研发夹具两入口都到人工验收门；deny分支两入口均停止、零被拒产品变更。correction原deny-all保留，权限bridge关闭；原非交互语义未用宽权限代替。
- acpx fs.writeTextFile/terminal.create确认均传原RPC上下文；自身原自动/明确deny规则保持。cancel/close/set-mode/set-model/set-config/turn替换/shutdown先close pending。未开放的别的代理客户端不是新增兼容承诺。
- Dual公共retry/abort/closeIteration/revise/readiness/gate决定/replace claim的live尝试分别实测拒绝，合法原C仍可达；其非live状态经既有close guard。node结束/失败/stale、R03与恢复裁定继续同L终结，不清commits。
- 新permission reply独立控制消息不能排在等待它的prompt之后；要求同owner generation不允许旧RPCid在重启借权；实际本地socket与CLI peer正反验证。
- WebSearch既有人工范围审批不是通用工具C的替代批准；本次未扩网络authority、不重开R4/Phase B。原生allow不放宽网络业务范围/批准合同，旧特例不因新bridge生成额外grant。

## 六维与成熟度

Declared：冻结§8.25/§8.27、用户实施授权及C-first在途取舍；Proven：各层离线slice+实际本地IPC/SDK peers+研发消费者序列（证据分层，非真实native）；Consumed：唯一L C/X→原live sender→原queue callback，唯一execution outcome→既有推进/恢复，view只消费；Enforced before：C提交前业务资格与材料身份、原RPC回复前取消/身份/期限、业务采纳前scope/证据/复评；Failure route：D06 fixed local / D07外部unknown / expired或deny不fallback；Cannot-bypass：重复C隔离反证、写前缀、barrier、冷JVM、错身份、多请求、合法续作对照。

本批机制在受测离线路径达ENFORCED/PROVEN。真实客户端能力、真实工具副作用/退出码、浏览器runtime、部署实况和交付链不提升成熟度；总体W项与真实NO-GO仍在。自查不是独立评审，B04须待用户发起CC合并判断。
