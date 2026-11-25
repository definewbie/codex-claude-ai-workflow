# Fresh Java MODIFY 装配结果与证据边界

本次推进 P0：增加对既有 Java 项目 MODIFY 的完整主链证明准备；不做 token 优化。边界是 operator 准入与原 Java/domain 的分工：新主体、新基线、新独立预算，同一 E 经核验后传 Popen；原 R01/R02/R03/L03/人门/终态继续唯一掌握业务事实。

## 已完成

- 在 fixture/case/target 从305文件归档建立独立 Git 基线，树与源 commit 931db5db 一致；原项目的脏状态和文件未改。新本地 commit 不冒称源 commit。
- 原 Java start 无 --run 产生工作树及 ws-20261026T142203Z-7SNA，初始账本清单固定；无 runs、pending、当前业务批准或 native 会话。
- gate_support.py、observer 原12项、selected-release/SDK观察文件逐字复用；selected_environment 和预算 check 函数源码一致。本案仅将旧续作的主体/历史账/停止引用改为 fresh 初始清单与本案时间。process_check 的独立命令入口改导入 admission，检查函数不变。
- run-authorization 默认预算/运行 false、launch批准SHA null、无业务门/commit委派。实际执行 false 入口，拒绝发生在 target/step/Java 创建前。完整 launch 字节随后固定；当次批准仍待用户。
- 新 case 没有旧执行 PID。NO_UNRESOLVED_PIDS 只表示无本案未决号码，不伪造当前成功读表或任意进程子树已停止。未完本地 process/step、pending 或外部不明仍拒继续，不重查旧案已确认缺席的号码。

## 证据分层

| 层 | 事实 | 不外推 |
|---|---|---|
| 产品既有基线 | 默认完整166/42全绿，含11个MySQL套件52项 | 不是新阈值或模型执行证明 |
| 本次装配单轮 | 最终29项：17装配+12原observer；零失败，fresh-assembly-tests-final.json | Java替身/合成usage seam，不是真实Java/native E2E |
| 预算 | 原24项对照保持；总预算3M/18/3600仍待批 | ALLOW只证规划，不授业务权或单请求硬限 |
| 隔离反证 | 删除首授权检查、重引父PATH、移除observer SHA，各触发指定不变量失败 | 另列，不加入29项；仅离线隔离测试 |
| 实际准备 | 原Java无--run初始化、Git树/305字节、false拒绝、只读重核 | 不证明原生客户端可运行Maven/Docker |
| 历史发行 |659输入/两JAR/33客户端配置字节一致 | 1813旧回归不重报为本次新回归 |

过程错误：attempt-01 中缺失程序在 frozen_check 更早抛 FileNotFoundError，而测试只期待 AssertionError；改测试接受原有拒绝出口，生产规则未放宽。attempt-02 的26项为先前切片；补3项后最终单轮29作凭证，不拼接。

## 时间和授权

本案累计时间从本案离线初始化命令回执计起，包含 false 授权检查、Git/start/只读核验，之后每次单步、人门命令和清理继续累计。此前基线测试、装配UT属运行前准备证据，不伪装为本案模型执行时间；无旧案时间/余额继承，无人等待计入。准备时 false-guard 回执最初在包根，已按原始elapsed纳入 operations/authorization-guard/result.json 一次，无估值或重复。

运行批准不能代需求、scope、EXECUTION或交付门；commit也未委派。先冻结launch→授权记录填launch SHA、workstream、初始清单/预算SHA→引用用户当次批准→显式启用budget_approved/real_run_authorized；获准后重核最新外部状态。语义返修次数上限仍由operator在每次具名单步前按原迭代事实核对，不声称本预算函数自动识别所有业务返修。

## 自查与剩余

Analyze→Modify→Review→Reflect→Analyze：完整transition/写前缀见 fresh-assembly-design.md。自查未发现必须新增机制；保留WARN：CC尚未复审本次装配、当次预算和真实运行未批，原生工具出口和新产品/交付尚待真实执行。无新独立agent；自查不冒充CC。

下一步最小动作：CC只读合并装配复审→用户决定当次Gate/3M预算/运行→重核后原单步入口。无Java/acpx重建、Relay接入、进程监督或新模型实验。
