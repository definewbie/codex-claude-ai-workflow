# 本案 fresh 装配边界（实施前分析）

P0：既有 Java MODIFY 的完整主流程准备。定义沿用 terminal-delivery@5；Instance 由原 start（无 --run）生成；Execution 仍由 Java R01/R02/R03/L03 生产。operator 只记录启动、预算核对与命令时长，不产生业务 authority。无 Java/acpx/产品源码改动、无新服务或永久兼容。

本次删除旧续作数据依赖：不复制 continuation-spec、旧授权 true、旧 PID、旧账本或余额。复用 gate_support 的固定用量 reader/planning、selected_environment 的单次 E 构造，以及原单步启动/失败留证顺序。授权绑定当前 case、程序生成 workstream、初始账本清单、预算及 launch SHA；默认预算/运行/业务委派/commit 委派均 false。

| 前态/owner | 顺序与下一消费者 | 每个失败前缀/恢复 |
|---|---|---|
| 无本案 target/operator 准备 | 核 archive/305 文件→独立 target 初始化 Git→提交与原树相同的基线 | 部分 target 不能覆盖重试；保留日志交人工，不动原脏仓库 |
| 固定 target/原 start | 同一核过 E→start 无 --run→程序生成工作树/初始 ledger→固定 location/初始清单 | start 超时/半写留现场；无模型调用；禁止自动再 start |
| 新包未批/operator | authorize 首行核当前具名批准、SHA/预算/初始主体→inspect 核固定输入/当前 pending/命令结果/用量 | false/错主体/改字节/新 stop 均在 step/Popen 前拒绝；新 case 不消费旧停止的续作豁免 |
| 准入通过/原 Java | current READY/无 open gate→step/预算/前态→Popen 同 E→PID→wait→result→Java 固定事实→observer 全量重读 | mkdir/prestate/Popen/PID/result 任一点中断留未完成 step，inspect 拒自动重跑；外部不明沿 D07，不假定零副作用 |
| 人工门/原业务 owner | 原 action、当前 subject、当前人批→原 compiler/qualification→R01/R02/R03/L03 | Gate 运行批准不代产品需求、scope、EXECUTION、交付人批；不改状态模型 |
| 验收/交付 | 原独立验收→candidate→最终人批→apply→人 commit→finalize | UNKNOWN 沿 D06/D07；终态只读，不重开；旧样本停止永久保留 |

活动时间只计本案命令回执（包含准备/只读核验/人工门命令/清理），无历史余额；人等待不计。源事实与 token 只读原 run/occurrence，历史追加不能撤销固定完成。600 秒为原命令超时；3600 秒为累计活动时间；3M/18 是待批提案。原生工具能否运行 Maven/Docker 须在真实计划/EXECUTION 前以现有 readiness 证明，宿主全绿不能替代。

验证：实际 false 拒绝（step/Java 之前）；完整替身链的同 E 正向与父 PATH 无关；错身份/预算/launch/输入/停止/未完写入/terminal 拒绝；写前缀、冷子进程、程序消失与用量未知；observer 原 12 项与预算原 24 项分别记账。替身不声称真实 Java/native E2E。只做本案适配，出现需要新机制时停止提交 ROI。
