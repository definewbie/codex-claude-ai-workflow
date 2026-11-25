# CC 受影响复审处置（仅离线）

接受：进程核验候选的离线证明成立；原会话命令直接超时机制已证，内部阻塞阶段未证；未来完整 Gate 尚不可启动。历史第七例 INVALID、领域 session_bootstrap_failed、usage UNAVAILABLE 及停止标记不变。本文记录用户提供的 CC 结论，不冒充本轮再次独立评审。

## LOW-1：缓存时间戳补查

见 [cache-mtime-followup.json](cache-mtime-followup.json)。仅 stat 既有 npx 缓存根、package/lock、node_modules 及对应包目录/清单，共7项；mtime 均在2026-08-31，未落在第七例55秒故障窗口。没有执行 npm/npx/acpx、网络、原生客户端或模型。

**没有新增定位证据。** mtime不记录读取；目录mtime不覆盖所有子文件内容更新；元数据咨询可在其他缓存或内存发生；后续写入也可覆盖旧mtime。不能据此断言当时未访问缓存、未请求元数据或未启动adapter。即使mtime落窗，也只能证明相应对象修改时间吻合，不能唯一归属第七例。

补正评审中的术语：已有VM对照的第二分支是“workspace local tree 中有精确依赖”，不是“npx cache 已有精确版本”。安装缓存存在不能排除在检查缓存前咨询manifest。原证明文件已经清楚区分，两文件保持不改。

## LOW-2：既有 verbose 入口与观察边界

源码入口确认：`src/cli/flags.ts:379` 定义 `--verbose`；`src/cli/session/session-management.ts:203` 传入client；`src/acp/client.ts:837-842` 透传子进程stderr，`:916-930` 输出launch信息，`:1042` 在initialize及必要authenticate结束后输出协议初始化完成。`src/cli/command-registration.ts:103-112` 的 `sessions new` 是会话创建入口，不是prompt入口。

这些标记**不保证精确区分全部内部阶段**。看到launch只证明准备启动；没有initialized标记不能唯一断言卡在npx；initialized标记位于auth之后，不能用其缺席区分initialize与auth。成功创建则证明本次已穿过启动链，但不复原历史45秒原因。仍无法区分时保留UNKNOWN，不扩展遥测框架。

## 最小下一步

交用户批准 [一次45秒、禁prompt的观察方案](native-initialization-observation-gate.md)。不自动安排90秒第二次初始化；需要时依据本次证据另行决定。当前只产出文档、argv候选和stat侧证，运行授权仍为false。

本次延续 boundary-first-engineering：Definition为观察协议；Execution事实归原命令输出/进程及协议材料；诊断不产生Instance/业务批准或推进事实。产品代码、历史包、停止现场不变。没有新增服务、锁、schema、依赖或自动重试。
