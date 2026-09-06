# Codex 原生配置与维护中 Adapter：最小修正

## 必要性与边界

用户要求不固化模型，并授权确认模型后直接继续 fresh E2E。两次 health 的结论必须分开：

- 旧临时 CODEX_HOME 固定 gpt-5.4，被当前服务端拒绝；不是 Dual 代码写死模型。
- 改用正常 `/Users/kriswang/.codex` 后，ACP 实际 currentValue 已为 gpt-6-astra；服务端明确要求更新 Codex。
  第二次未返回 OK，工具调用零，已关闭 session；未创建业务 workstream。

根因的上游事实：旧 [v0.16.0 Cargo.toml](https://github.com/zed-industries/codex-acp/blob/v0.16.0/Cargo.toml)
固定 codex-core 等到 rust-v0.137.0，不会调用宿主 CLI。旧仓 [迁移说明](https://github.com/zed-industries/codex-acp)
明确转向 agentclientprotocol/codex-acp；npm 旧包 latest 仍为 0.16.0。
新 [adapter](https://github.com/agentclientprotocol/codex-acp) 通过 Codex App Server 工作，
npm 1.10.0 依赖 @openai/codex ^0.153.3，本机已安装解析为 0.153.4。

不修 fork、不造 adapter、不在 Dual 解析 TOML、不把 gpt-6 写成生产常量。唯一改变是维护中的 ACP 包选择，
以及 Doctor 对该已选择版本的核对。模型与配置优先级仍由 Codex 自身拥有，Dual 的 ProcessBuilder 继承环境且不注入 --model。
固定 adapter 版本用于可复现，不等于固定模型版本；以后升级 adapter 与修改用户模型配置是两种动作。

## 最小变更与验证

- scripts/configure-acpx.sh：默认 @agentclientprotocol/codex-acp@1.10.0，删除旧平台包映射；保留显式 command override。
- DoctorCommand：相同包版本；删除平台映射；远程官方包不能被当作本地 binary 降成 WARN，从而掩盖旧包。
- DoctorCommandTest：新包字面量正例、旧包反例、installer 同步及不注入 model/home；原 Claude/local/floating 测试保留。
- 实际 ~/.acpx 配置使用既有 configure 脚本更新；Claude command 不变。Codex 用户 config/auth 不改，旧临时 home 不改。

改动前 Doctor 两条断言红灯（12 tests，2 failures）；改动后须 full package、Doctor、脚本语法、diff-check 全绿。
受限工具环境的 full package 遇 ProcessHandle/sysctl 权限错误后停止；宿主权限重跑，不改测试。
生产改动仅 CLI Doctor 与 installer，adapter transport、runtime、ledger、workflow、权限逻辑零变更。

## 新前置验证（执行前声明）

- Target：维护中 adapter 在正常 Codex home 下，以原生有效模型完成一次无工具 health，然后直接继续用户已授权的唯一 fresh E2E。
- Source of truth：包/依赖 identity、用户 config SHA（不保存凭据）、ACP session/new configOptions、prompt result、关闭记录。
- Trigger precondition：full package 与 Doctor 通过；新 JAR SHA 已更新 Gate；实际 command 为固定 1.10.0，实际 Codex 0.153.4；无 CODEX_CONFIG/CODEX_PATH/MODEL_PROVIDER ambient 覆盖。
- How checked：版本命令、安装包 package.json/二进制 SHA、选择性读取配置、Doctor 与真实 session metadata；未发 --model。
- PASS：实际模型匹配正常配置，本轮观测 gpt-6-astra；单 health 返回 OK/end_turn、零 tool call，关闭成功；随后 source/effective checks 仍须全通过。
- FAIL/INVALID：错误/超时/模型漂移/工具调用/关闭失败即停止，不再换模型试探、不降级旧包、不以 health 当产品 PASS。
- Stop-loss：一次新 adapter health，timeout 60s，无 prompt retries；不扩展成模型兼容矩阵。
- Freshness：新目录 `output/real-cases/20260906-native-codex-adapter-preflight/`；前两目录保持 INVALID 原始事实；没有第二业务样本。

模型成功后不再询问启动权限；人工产品 gate 仍由用户本人决定。

## 本地收口

宿主 full package exit 0，162 XML 聚合 1398/0/0/0；Doctor、新 JAR workflow validate、bash -n、diff-check 全通过。
新 JAR SHA：6ff7a946a109633bde0dcba6b15e3198fb8d0f49b962f4c1111a28258352bff2。
自身 diff 核对：producer=config script、consumer=acpx+Codex 原生配置，Doctor 只核对运行包；
Claude sibling 不变，旧 npm 包不能降为 local WARN；反例先红后绿。未宣称本轮存在 CC 独立 diff review。
当前仅 setup/Doctor 的 deterministic 收口；真实 adapter health 与产品 E2E 尚待执行，不能由版本命令代替。
