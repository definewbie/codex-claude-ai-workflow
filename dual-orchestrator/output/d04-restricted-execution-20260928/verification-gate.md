# D04 受限执行原生切片 Verification Gate

用户已授权：修订候选后进行有界原生能力实验；未授权产品实现。本轮为 P0 生命周期可靠性前置证明，D04/W03 未 Promoted，不涉及 P1/P2/P3 或网络 R4/Phase B。

- Target: 固定 Codex CLI 原生 `:read-only` profile 下，shell/子进程读取与搜索可用，受保护样例写入被拒绝。结论限定于原生命令沙箱，不等于模型自主评审或 acpx 绑定成功。
- Source of truth: native-result.json 的精确命令、binary/payload SHA、真实 exit/stdout/stderr、每项拒绝以及宿主前后内容/链接指纹。模型/provider/token=不适用，实际调用 0、成本 0。
- Trigger precondition: 所有写入目标均为本轮新建临时样例；普通 Unix 权限允许宿主写入；固定原生 sandbox CLI 支持显式 profile；现有安装包不修改。运行固定 Python 诊断程序，不接收模型或任意外部命令。
- How precondition is checked before run: 已读取 native `sandbox --help`（支持 `-P`、`-C`），先检查脚本目标均源于 mkdtemp fixture；driver 在启动前验证宿主可写，保存完整样例 baseline 和版本/程序身份。Gate 已审阅后才运行 driver。原 `sandbox macos --help` 使用了旧 CLI 形态，当前版本将 macos 当作子命令；嵌套 Seatbelt 启动报 Operation not permitted，属于准备期 INVALID，不能当产品安全拒绝。实际探针采用本机 help 的正确形态。
- PASS: 必要读取/搜索/Git 状态读取准确；修改、新增、ignored、control/policy/Git、删除/重命名、链接、写后还原首写、子进程及 git add 均被拒绝；前后指纹完全一致。仅可称 PASS_NATIVE_SLICE。
- FAIL: 任一受保护写入成功，或合法读取不能完成（前提有效时）；首个写入成功即退出，保留现场，不回滚、不打补丁重跑。
- INVALID: 沙箱未启动、嵌套环境阻止、错误 CLI/profile、样例/身份漂移、异常类型不能归因、证据不足；不得用无文件变化算安全 PASS。
- Stop-loss: 单次 30 秒；最多两次有效形态尝试，第二次仅可修正实验环境/调用前提，不调整产品。出现同类两次失败回到边界分析。无网络请求、无模型调用；不新增依赖、不修改 engine/adapter，不授予未知命令写权限。
- Run artifact freshness: 直接使用已核验的本机 Codex 0.153.4 缓存 binary，记录实际 SHA；driver/payload 本轮新建并记录 SHA，运行后重验。未使用 Dual jar，因此不宣称验证 Dual 构建。

若外层桌面沙箱无法嵌套 native Seatbelt，执行工具可只为此固定 driver 申请运行权限；此权限只让原生沙箱得以建立，不允许 probe 跳过原生限制。自动审批拒绝则保存 INVALID 并停止受影响运行。无需模型调用即可回答的层级优先执行；本 Gate 不授权额外 provider 调用或把切片结果外推到 Claude。
