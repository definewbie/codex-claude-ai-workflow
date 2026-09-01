# Review: E1 Sandbox Proxy Provenance R2 Gate（独立只读评审）

日期：2026-08-31
评审对象：`verification-gates/20260831-e1-claude-sandbox-effect-proxy-provenance-r2.md`、
`fixtures/e1-sandbox-effect-r2/`（probe 脚本 + 离线分类测试）、
`capability-proofs/20260831-e1-claude-sandbox-effect-49495db-r2/result.md` 及两次真实样本的原始证据。
结论均自行核实（含独立复跑离线测试与原始 stream/证据对盘），不复述实现方结论。
评审方式：只读。未修改代码、未调用 provider、未恢复 OKX/real-case。

## 0. 独立核实结果

| 检查项 | 方法 | 结论 |
|---|---|---|
| fixture 冻结 | 四文件 + 测试 `shasum -a 256` | ✅ 与 r2 Gate 冻结值逐一吻合（launcher/prompt/websearch 沿用 r1 原值；网络脚本 `8e3040f2…`、分类测试 `ed4abe0c…` 为新值） |
| 离线分类测试 | 本评审独立执行 `e1-network-probe.test.sh` | ✅ `[PASS] value-safe and fail-closed`；五场景（absent/controlled/external/userinfo-bypass/credential-canary/wildcard）断言真实 |
| 样本 2 原始证据 | `bash-run/environment.tsv` + stream | ✅ 与 result.md 一致：3 个 Anthropic 变量 `absent`、8 个 proxy 名 `present`、`filesystem 0`、results.tsv 仅 filesystem 行（网络矩阵未执行）；stream 含 2 处 `E1_TOOL_ENV_LEAK`、tool input 精确 `./e1-network-probe.sh` |
| 样本 1（前次） | `…-49495db/result.md` | ✅ Claude 2.1.178 下 10/11 denied 名可见（含 ANTHROPIC_AUTH_TOKEN/BASE_URL）→ 有效 FAIL——与 r2 Gate "为什么需要 R2"的叙事吻合 |
| fixture 修订链 | 三版本 SHA 对比 | ⚠ 我上轮评审的是 `49b63e3c…`；执行前被修订为 `121be03d…`（加入 presence→exit 73 规则，即上轮 P2-2 建议方向），Gate 冻结值同步更新，两次样本实跑 `121be03d`——修订本身未经 delta review（见 P2-3） |
| 代理注入假说佐证 | SDK schema（前轮已核 `sdk.d.ts:2903-2904`） | ✅ `SandboxNetworkSettings` 含 `httpProxyPort`/`socksProxyPort` 字段——与"Claude 以本地 proxy 作 enforcement transport"的解读一致；且 R2 设计不依赖该假说成立（分类不断言来源） |
| 分类器值安全 | 通读 + 测试断言 | ✅ 原始 value 仅经 `${!name}` 管道给 node stdin，node 静默退出；tsv 只记类别；stderr 只含变量名；测试以 canary/端口值/域名做不泄漏断言 |
| 工作树 diff | `git diff scripts/acpx-claude-agent-env.sh` | 仅 adapter pin `0.46.0 → 0.70.0` 一行——与 r2 运行时基线一致的一致性修正；未提交，需随 checkpoint 落盘（见 P2-4） |

## 1. Findings

### P0

无。

### P1

**P1-1 分类器无法区分"node 执行失败"与"external proxy"，可能把 INVALID 级环境问题误标为 FAIL 级 provenance 证据。**
`classify_proxy_url` 以 `printf '%s' "$value" | node -e '…'` 的非零退出统一判 `external` 并 `exit 74`（FAIL）。但
`command -v node` 只覆盖 PATH 缺失（exit 76/INVALID）；若 node 在 PATH 中而**sandbox 下无法执行**（二进制位于
policy allow_read 列表之外，exec 返回 126/127），管线非零会被判为 external → FAIL。两个后果都严重：FAIL 会触发
"停止 `PUBLIC_NETWORK_DOMAIN`、回 bounded comparison"的错误决策；且 FAIL/INVALID 均烧掉唯一授权样本。目前对
sandbox 下外部二进制（连 curl）能否执行**零证据**（两次样本都在网络矩阵前退出）。修法（几行）：区分 node 退出码
126/127 → `exit 76`（INVALID），仅 1 → external；顺带在 probe 前置一个最小 `node -e 'process.exit(0)'` 自检。
注：网络矩阵同类风险由 PASS 7 的"必须 sandbox 可归因证据"兜底，但分类器没有等价兜底。

### P2

**P2-1 `NO_PROXY` 尾随逗号会被误判 `external_bypass`。** `IFS=','` 拆分后空 entry 落入 `*` 分支 → exit 74。
`NO_PROXY=localhost,`（尾逗号常见且无害）会错报 FAIL。修法：跳过空 entry。保守方向可接受，但一个错报即烧样本。

**P2-2 `controlled_loopback` 主机集偏窄（仅 `127.0.0.1/localhost/[::1]`）。** `127.0.0.2` 等同属 loopback 网段的
地址会被判 external。若 Claude 注入的 proxy 绑定非 .1 地址即错报。属故意保守可接受，但应在 Gate 文档登记该窄定义，
避免实施者意外。

**P2-3 r1 fixture 在评审后、执行前被修订（`49b63e3c` → `121be03d`）且无 delta review 记录。** 修订内容（presence
硬失败）方向正确且源于上轮评审建议，但"评审过的 fixture"链条实际断裂：两次样本执行的是未评审版本，r2 result 的
"fixture 四个 SHA 与 Gate 一致"仅对修订后 Gate 成立。要求：今后 fixture 任一字节变更须 delta review 或在 case report
显式记录修订说明；本次应在 r2 checkpoint 中补一段修订溯源。

**P2-4 运行时 pin 的落盘方式未闭合。** r2 Gate 前置要求"detached clean source `49495dbe` 加**经评审的 runtime pin
checkpoint**"，而工作树的 env script pin（0.46→0.70）尚未提交；样本 2 在 source `49495dbe`（已提交默认 0.46.0）下
跑出 0.70.0，意味着当时使用了 `DUAL_CLAUDE_ACP_COMMAND` 覆盖或未提交改动——应在 checkpoint 中写明实际来源，否则
runtime 基线的可复现性有缺口。

## 2. Boundary Review（对照用户四个重点）

| 问题 | 判定 |
|---|---|
| **代理来源分类是否可靠** | ✅ 设计可靠且克制：URL 白名单（协议 http/https/socks5/socks5h + 主机限 loopback + 端口 1-65535 + **禁止 userinfo/path/search/hash**）逐项拒绝伪装形态；`NO_PROXY` 仅允许本地 entry（`*`、域名、CIDR → `external_bypass` 硬失败——正确封死"绕过 proxy 直连"的分类口）。更重要的是认识论纪律：Gate 明文"分类不是安全结论"，`controlled_loopback` 不单独构成 PASS，不把 Claude 内部 proxy 当第二 authority。缺口为 P1-1（exec 失败歧义）与 P2-1/2（边角窄/严） |
| **是否泄漏敏感值** | ✅ 无泄漏路径：原始 value 只进 node stdin（内存），node 不回显；`environment.tsv` 仅 `name\tcategory`；stderr 仅变量名与类别；离线测试以 `! grep 4100/secret-canary/proxy.example`（tsv+stderr）钉住。credentials 出现即 exit 73 且只记 `present` |
| **URL/NO_PROXY 是否可绕过** | ✅ 四层封堵：(1) userinfo 代理 URL（含凭据/主机伪装）→ external（测试有专案）；(2) `NO_PROXY` 通配/外部域 → external_bypass 硬失败；(3) ambient `NO_PROXY=127.0.0.1` 下的 loopback 直连由矩阵 `loopback` 探针在真实 sentinel 上验证被拒；(4) 攻击者 unset 全部 proxy 后的 direct/loopback/LAN 由 PASS 6 三条 `env -u` 探针验证仍被 OS sandbox 拒。npm/Maven 已改用真实资源（`npm view acpx`、commons-lang3 真坐标）成为硬成功判别器——修复了 r1 评审的 404-capable 缺陷 |
| **PASS 是否真正要求双 regime 受控** | ✅ 精确说是"覆盖双 regime 而非要求代理存在"：PASS 5（ambient 环境——无论 proxy absent/empty/controlled_loopback——全矩阵被拒）+ PASS 6（显式清除 8 个 proxy 变量后 direct/loopback/LAN 仍被拒）+ PASS 7（每项须 sandbox 可归因证据，禁止以 non-zero/DNS 自然失败冒充）。三重合取确保 enforcement 归属 OS sandbox 而非 proxy-env 形态；全部 absent 时 PASS 6 与 PASS 5 direct 同义，冗余无害 |

**其余边界**：FAIL/INVALID 纪律在样本 2 上已实证——按预承诺规则记录 FAIL，能力层改判 `INVALID_OBSERVATION_MODEL`
而非部分 PASS，未提升成熟度（"三个 Anthropic 不可见"仅记 `CAPTURED FACT` 且注明未做消融）。授权分层清晰：当前仅
fixture/Gate/离线测试/评审；provider health 沿 standing；两个带工具 prompt 需本 Gate 评审 PASS + clean checkpoint +
non-provider preflight 之后的**另行明确授权**；禁止 OKX/real-case/`PUBLIC_NETWORK_DOMAIN`。Stop-loss 与 r1 同构
（credential/external proxy/首次网络成功/filesystem 退化/unsandboxed 继续/tool drift → 立即 cancel+close；≤2
prompts、600s/prompt、20min、retries=0）。

## 3. Execution READY

**NOT READY（差一项 P1 + 四项 P2 的文本/fixture 修订）。** 修订全部离线可完成并可由 `e1-network-probe.test.sh`
回归（P1-1 修复应新增 126/127→76 的离线用例；P2-1 增加尾逗号用例）。修完并复跑离线测试通过后即 READY。

## 4. 下一步精确授权边界

1. **现在可做（无需新授权）**：按 P1-1/P2-1/P2-2 修订 r2 probe 脚本与 Gate 文本；补 P2-3 修订溯源段与 P2-4
   runtime-pin 说明；复跑 `e1-network-probe.test.sh`；本评审关闭后做一次轻量 delta 复审。
2. **需一个 docs/fixture checkpoint 提交**（clean SHA）：r2 Gate + fixtures + 两次样本证据 + env script pin
   （0.70.0）+ 修订溯源。该提交本身不含 provider 行为。
3. **checkpoint 后、样本前（standing 授权内，不触 provider tool）**：fresh package、Doctor、fixture SHA 复算、
   package-local launcher compile、离线分类测试、fresh minimal health probe（一次、零工具、即关）。
4. **需用户一次性明确授权后才可执行**：两个带工具的 capability prompts（Bash probe → 全部通过后才 WebSearch +
   session-conflict）。授权范围恰为这两个 prompt；仍禁止 OKX 恢复、新 real-case、`PUBLIC_NETWORK_DOMAIN` 实施、
   production network policy 修改。
5. **结果纪律**：FAIL → 停止 domain-grant 实施回 bounded comparison；INVALID → 不得第二样本；任何结论不得把
   transport PASS 或 env 观测外推为网络 enforcement PROVEN。

本次评审未修改代码、未调用 provider、未复述实现方结论（所有关键声明均以原始证据或独立复跑核实）。
