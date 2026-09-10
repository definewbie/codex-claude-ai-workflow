# 人工动作需求登记实施记录

日期：2026-09-10。主 agent 实现与自查，非独立评审。未提交，未调用 provider、额外 agent 或恢复 real-case。

## 本批结果

实现 request_action：人工可以在声明该动作的 implementation repair gate 登记现有动作无法满足的需求。
登记只表达意图，不创建新的可执行动作，不关闭 gate，不授予权限，也不启动 provider。
CLI、API、console 共用 WorkflowFileLedger.requestAction；review 展示从节点事实读取登记项。
没有业务名称或产品路径特判，没有动态 DSL、插件或命令执行载体。

## Transition 与失败切面

| 环节 | 规则 |
|---|---|
| pre-state | WAITING_FOR_HUMAN，唯一 active gate 与请求 gate 完全一致 |
| 声明 | human.review 的 implementation_repair gate 显式声明 request_action；禁止该动作存在出边 |
| 输入 | request ID、gate instance、来源 refs、预期结果、既有动作不足的理由；不接受命令或授权策略 |
| 准入 | workstream lock 内重新读取 frozen definition、current、node、gate；来源 refs 必须逐项相等 |
| owner | node.result.action_requests 为唯一事实；事件只是派生审计记录 |
| 写入 | 原子更新 node 后追加事件；不修改 current、iteration 或 gate resolution |
| 幂等 | 同 ID 同内容返回原记录；同 ID 异内容拒绝，不覆盖 |
| 部分失败 | node 成功而事件失败时，登记事实仍成立；冷读重试返回原记录，不靠事件重建事实 |
| post-state | 仍等待原 gate；后续合法裁定保留登记记录 |
| 旧请求 | gate 已变化或已处理、来源 refs 不同，均在写入前拒绝 |

来源 refs 的绑定仅证明登记针对哪个等待事项，不证明产品源码字节未变化。
事件失败后不补写事件是明确的审计缺口，不是登记丢失；没有增加跨文件提交协议。
普通 resolveOpenGate 不接受 request_action，防止它被误用成关闭 gate 的裁定。
scope repair 的既有 resolving action 校验保持，只把非 resolving 的登记动作排除出该集合。

Declared by：definition 的 allowed_actions 与非路由动作校验。
Proven by：锁内当前 gate/source refs 校验及 node 原子记录。
Consumed by：ledger 幂等判断、review CLI/API/console 冷读投影。
Enforced before：任何登记写入；该动作不触发执行副作用。
Failure route：陈旧或冲突请求拒绝，gate 不推进；事件失败可从 node 读回已登记事实。
Cannot-bypass：普通 resolve 拒绝、陈旧来源零写入、重复请求、冷读及事件追加失败测试。

## 验证

- 新增 ledger 三项测试、CLI 一项、server 一项、console 静态边界一项。
- 覆盖真实 ledger 冷读、幂等、冲突、事件失败、后续 revise 保留记录、server 不调用 resumer。
- 四份 workflow 同步声明；没有新增节点或 request_action 出边。
- 完整离线 reactor：1452 tests / 0 failures / 0 errors / 0 skipped，174 XML files。
- git diff --check clean；console 语法检查通过。未执行真实浏览器交互，不冒充 UI E2E。
- 日志：/private/tmp/dual-human-action-reactor.log。

## 未完成项与下一步

人工动作登记：ENFORCED，离线序列已验证。完整补证据续作：未实现，NOT PROVEN。
collect_evidence 不能只增加一条回 self-test 的边：已有 scope 校验允许修改范围内的源码，
而 inventory SHA 只覆盖路径集合，二者都不能证明补采没有修改产品。

下一批必须先锁定最小的产品字节比较方式：由人工批准时产生基准，续作前及结果可用前强制比较；
重试不能重新取基准来合法化已发生的改动。若出现 drift，拒绝把本次结果当作纯补证据继续验收，
不自动回滚用户代码。此约束是事后结果准入，不冒充 OS 级禁止写入或检测瞬时改动后恢复。
在这条链及其拒绝测试完成前，不开放 collect_evidence；submit_evidence 材料化仍是后续独立增量。

后续状态：上述为本登记增量收口时的状态。collect_evidence 与批准时源码基准已在后续增量接入，见 [源码基准收口](20260910-evidence-collection-source-baseline.md)；submit_evidence 仍未实现。
