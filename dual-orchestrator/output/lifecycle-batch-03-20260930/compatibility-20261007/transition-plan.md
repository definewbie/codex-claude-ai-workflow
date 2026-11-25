# W03：按接口兼容性接入原生客户端

授权：2026-10-07 用户同意环境变量入口、兼容升级不因版本变化拒绝并要求继续推进。P0 主流程闭合；本增量替代 native-identity-20261007 的固定安装/原生逐字节身份建议，不回写历史证据。原生版本不属于冻结 Dual binary 身份；Dual 的代码、定义、批准、输入内容身份和受审 acpx policy 接口仍严格。未声明任何具体版本范围已经验证。

## Owner 与事实

- Definition：既有任务/profile/policy。不新增配置 DSL。
- Instance：既有 claim/批准/epoch，由 ledger 唯一拥有。
- Execution：transport 提供 invocation（选定 acpx 入口、工作区、agent/session、PATH 与两项原生路径环境变量）；R01 固定此输入和允许的 primary/correction 配置。
- transport 读取既有 `--format json <agent> sessions show <name>`，只保存 schema/会话身份/协议/能力等白名单摘要。版本缺失记 UNKNOWN，不运行版本探针。该摘要是最后记录的握手，不冒充 live queue/current native bytes 证明。
- command intent 的 invocation 必须与 R01 一致，才允许该命令启动；cleanup 仍匹配原会话，不获 prompt 权限。
- 协议不支持、必需元数据缺失、错误/已关闭会话形成 typed adapter failure，由唯一 resolver/恢复投影消费，不改成模型 JSON 缺失。
- 可选能力不要求全部存在；具体验收所需证据在既有 D05 消费点检查，actual exit 要求不降级。

## Transition / 写入与失败切面

| 前态→动作 | 耐久写序/副作用 | 后态与消费者 | 失败、重启解释 |
|---|---|---|---|
| 合法批准→准备 | 保存 prompt/manifest；R01 固定 invocation 与配置；L 绑定 | prepared，dispatch 核验 claim/批准/输入 | 写失败零 native；旧/缺身份不得借权 |
| prepared→开始调用 | R02 attempt intent；每 command intent 校验 R01 后落盘；session ensure | session 已登记 | intent 失败零该命令；结果写失败不启动后继；ensure 不明保留 UNKNOWN |
| session→核对 | show intent→只读 metadata command→白名单结果固定 | transport 核协议1、session/cwd、open、record/session id，再发送 prompt | 不匹配/缺材料零 prompt，typed incompatible；show 失败为 bootstrap，不猜模型失败 |
| 合格→prompt/correction | 再核 invocation/权限配置，command intent→调用→结果；correction、rebuild 同样核对会话 | 工具/产物候选→唯一判定→R03→L03 | 调用不明不重发；不能把上次握手当当前功能全部兼容的保证 |
| 已确认完成→冷恢复 | 只读既有 R01/R03 固定内容 | 固定结果续完 | 不重新探测 native，不调用 provider，不因 native 升级推翻已确认结果 |
| 外部不明→明确 stop | 当前入口须匹配 R01，精确 cancel/close 的 intent/result | 停止事实供既有人工裁定 | 入口变化不猜旧 session；父进程消失不是 native 全停，失败仍 UNKNOWN |

## 验证预期（来自合法流程）

1. Claude/Codex、primary/correction 同 session；版本改变或缺失但必需协议正常→允许 prompt；未知可选能力不误拒。
2. 错 cwd/session、closed、missing/unsupported protocol→零 prompt、typed failure；非法输入不能由正文成功洗白。
3. R01 不匹配入口/session/profile 或缺 R01→零命令；合法 correction 与 cleanup 仍可达。无全权限 fallback。
4. intent/result 每个耐久失败前缀不得进入后继；重新建立会话和 correction 均经过核对；metadata 不持久化 messages/config/env/credentials。
5. R03 冷恢复仅固定字节续完；UNKNOWN 不自动补发；业务 scope/批准/证据回归维持。

边界成熟度在运行证据前为设计义务。零 native/provider；D04 C/X、真实 E2E、提交在批外。实现完成后聚焦验证、单轮全模块回归、自查与反思，再交 B03 合并批末独立评审，不自称独立 PASS。
