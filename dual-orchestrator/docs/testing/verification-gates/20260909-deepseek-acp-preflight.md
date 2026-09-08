# DeepSeek ACP Preflight

状态：无工具 health PASS；production transport Read PASS。本轮只验证现有连接，不实现 model 平台。

## Verification Gate

- Target: 当前 operator Claude Code 配置经 reviewed acpx、既有 wrapper、pinned ACP 完成一次无工具结构化回复。
- Source of truth: acpx JSONL 的实际完成/错误事件与回复；配置观测仅证明请求配置，不把模型自述当 provider identity。
- Trigger precondition: wrapper 有凭据但不输出值；有效 endpoint host 为 api.deepseek.com，model 为 DeepSeek-V4-Pro；acpx build-info 与 manifest 一致；claude-agent-acp 为 0.70.0。
- How precondition is checked before run: 读取 wrapper 子进程中脱敏的模型/host/presence；核对 acpx config 的 agent command 和 build-info。以上本轮只读检查已通过。
- PASS: 单次 exec 正常完成，响应可解析且精确等于 {"probe":"dual-acp-health","ok":true}，无工具调用。
- FAIL: 有效请求遭 provider/protocol 拒绝、回复不符合约定或出现工具调用；不自动重试，不据单次失败判断所有 DeepSeek 模型不兼容。
- INVALID: 配置/身份漂移、宿主权限阻断、缺失完成证据或手工中断；不当作模型能力结论。
- Stop-loss: 仅一个无持久 session 的 exec，deny-all、空 allowed-tools、no-terminal、prompt retries 缺省0。quota 立即告知用户；耗时只观察，不作为模型失败。发现持续无进展再根据进程/输出判读并报告，不重跑。
- Run artifact freshness: 本次独立 output 目录；归档原始 stdout/stderr、退出码及 hash。本检查不依赖 CLI JAR，不声称批次B或sandbox已通过。

## 第二项：production transport Read

Health 单次已通过：exit0、end_turn、零工具、精确 JSON；stream SHA
`a3dfb9d6a6574454433cd6139960d3f8a434555d89c6262b044b815b43c988d9`。
原始 usage 为26785 input/14 output，只记录token，不把SDK cost当DeepSeek账单。

第二项单次测量复用 production policy writer + transport + observe profile，fixture仅收紧retries=0。
Target为一次真实Read获取prompt中没有的随机nonce并返回JSON。SoT为stream工具输入/成功结果、
typed transport result及nonce文件。前置为fixture编译成功、当前已验acpx身份及新目录；
PASS必须恰一次Read指定文件、无其它tool_call、JSON nonce匹配、exit0、无active adapter signal、
session关闭。FAIL/INVALID和无自动重跑纪律同上；不开放domain、不修改production policy。
fixture为fixtures/20260909-deepseek/DeepSeekReadProbe.java，运行前源码SHA为
`2b3ca93eb7c258f208c66d6065bf97399cc6bbb00174800cc69c50bbb057496a`。
只证明该配置下的read round-trip，不证明完整ARC、自测或网络授权。两个检查范围不重叠，
不扩为模型矩阵/性能评测。预授权覆盖本次最小工具检查。

实际结果：真实ACP session `1e9f0ff8-4aee-466e-8a4a-e0d1dcd3cddf` 的结构化messages
恰一ToolUse、指定nonce.txt、对应tool_results.is_error=false；正式transport返回exit0、
end_turn、adapterSignals=[]，finalAssistantMessage中的nonce与原文件一致；closed=true。
工具判定依据是session JSON，不是展示流的两行pending/completed，也不是模型自述。
stream.jsonl扩展名为沿用transport输出路径，第二项该文件实际为text，原始ACP事件另归档。
该fixture不经完整AgentRunMetadataWriter，result.usage=null；实际session保留request_token_usage，
不能把此处null断言为token不存在或完整生产归因已验证。

证据目录：`output/real-cases/20260909-deepseek-acp-preflight/`，含原始session JSON/NDJSON、
read-probe目录、health输出及package日志。不输出或复制settings/token；no-session exec正常结束，
有持久session的Read检查显式关闭。未运行Codex probe或业务workstream。

新JAR打包成功（`-DskipTests package`，不是新一轮测试）：
`fb6dbcabb5f1d3aa2839d3489ddad1b3c0e03e7289d31a9090623ea63fb8ef3e`。
Doctor OK，acpx patch `2313fe2d53e573dc0227a453fd1c4cbe6107373472a2027bb7ed3e2952038b63`。
测试仍引用2026-09-08的1425/0/0/0，不把打包当独立复跑。

## 通用性决策与后续边界

结论：现有wrapper/ACP足以完成这两项测试，当前没有实施新model层的必要证据。
Dual的状态/权限/批准以typed事实为准，模型名只用于连接与归因；AcpxAgentTransport本次
实际command无--model，使用operator配置。provider endpoint/model是配置观测，服务端内部
实际权重未独立证明；不引用模型自述。兼容层能规范协议，不能保证任意模型服从scope或通过验收。

若E2E出错：连接/鉴权修adapter配置；协议格式映射修现有adapter；有效但越权的输出继续由
通用validator拒绝；不根据模型名称开白名单、降schema、扩大权限或改workflow。只有可复现的
协议/能力差异无法由既有adapter承担时，才先设计最小能力契约与对应反例，不预建模型平台。

通过后才准备含批次A/B的运行工件及 fresh 产品 Gate；工具调用、正式 typed artifact 和权限政策仍须沿 production 路径检查。失败先区分配置、协议、模型能力、Dual输出假设。仅配置问题改配置；仅协议差异在 adapter 转换；业务 validator/批准权限不能为某模型降标。只有重复、可复现且现有 adapter 无法承担的差异才考虑最小 model 层。

不启动额外 agent，不恢复旧样本，不提交无关工作树变更。关于其他 agent 的跨模型能力，本轮未取证，不作为新抽象的依据。
