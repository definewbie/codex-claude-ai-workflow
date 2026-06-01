# 真实 smoke 修复复盘：从“脚本能跑”到“流程能闭环”

日期：2026-06-01  
分支：`dev-smoke-multi-variant`  
范围：`scripts/smoke-test.sh` 多变体真实 E2E、acpx agent 调用、ledger 契约、prompt 契约、delivery prepare 验证

## 背景

这轮工作的目标不是再补一个小功能，而是确认 orchestrator 能不能真的把一个需求交给 Claude / Codex 跑完。之前的代码结构已经有 workflow、handler、ledger、prompt、context package、contract、failure classify，但这些模块大多是局部验证。真正的问题只有在真实 acpx smoke 里才会出现：Agent 会不会按契约输出？失败能不能被看见？review 的反馈能不能带回下一轮？ledger 里的证据够不够复盘？

所以这轮我把 `scripts/smoke-test.sh` 从单一 smoke 改成了多变体 E2E：

- `thin-basic`
- `p5-contract-happy-path`
- `p6-reject-classify-path`

然后用真实 acpx 跑，而不是 fake agent。事实证明这是对的。单测没有暴露的问题，真实 agent 一跑就出来了。

## 第一层问题：脚本自己的可信度

最开始先审 `smoke-test.sh`，发现脚本本身还不能作为证据：

- `--case all` 在某个 case 失败时容易被 `set -e` 提前打断，拿不到完整 summary。
- cleanup 直接 `rm -rf`，路径保护不够。
- 预期 event 缺失只是 warning，不适合作为 E2E 质量门。
- delivery artifact 判断不准确，把 `delivery.json` 当成 workflow 内置产物。
- 每个 case 的数据记录不够清晰，后续不好比较。

这批问题先修掉以后，smoke 才有资格当“验收工具”，否则它自己也会误报。

## 第二层问题：Agent 输出和解析契约不够现实

第一次真实跑 acpx 后，流程卡在 dual-control 解析。

原因很具体：acpx 会在 agent 输出末尾追加：

```text
[done] end_turn
```

而我们的 `DualControlBlockParser` 之前要求 `dual-control` 必须是文件末尾最后内容。这个规则本身是为了防 prompt injection，是对的，但没有考虑 acpx 的 sentinel 行。

修复方式不是放宽到“末尾附近都算”，而是只允许一个非常窄的例外：

- `dual-control` 后面只能跟空白，或者精确的 `[done] end_turn`
- 其他正文、代码块、伪造内容仍然会被判定为 non-terminal

这样保留了注入防护的意图，也兼容了真实 acpx 输出。

## 第三层问题：Agent 写 ledger 的边界不清

真实跑的时候 Claude 尝试写 `.agent/.../brainstorm.md`，被权限拦下。这个失败是好事，说明权限保护生效；但也说明 prompt 没有把边界讲清楚。

这轮给所有 agent prompt 加了明确的 ledger write policy：

- 不创建、编辑、删除 `.agent/` 下的文件。
- 运行账本只能由 orchestrator 写。
- Agent 只把产物输出到 stdout。

这个改动看起来很小，但很关键。orchestrator 的核心价值就是“谁说了什么、什么时候说、基于什么证据说”都能复盘。如果 Agent 也直接写 ledger，账本就失去可信边界。

## 第四层问题：Prompt 和 workflow 的 Decision 合同不一致

后面又卡在 intake / design decision。

根因是 prompt 里让 Codex 输出的 Decision 值，和 workflow decision node 允许的值不一致：

- prompt 说 `PASS`
- workflow 期望 `READY_FOR_PRODUCT_REVIEW`
- design 类似，prompt 和 workflow 也有漂移

这类问题靠“模型聪明一点”解决不了。orchestrator 是状态机，状态机需要稳定枚举值。修复后，prompt 中的 Decision 值和 workflow routing contract 对齐，并加了测试锁住。

## 第五层问题：自测阶段权限太紧

流程进入实现后，Claude self-test 阶段失败。原因是 self-test 需要执行命令，但该节点仍按普通只读 agent 运行。

这里的判断是：实现阶段的 Claude 是 developer，自测是 developer 职责的一部分，它应该能运行验证命令。于是把 `implement.claude_self_test_report` 改成 writable / approve-all 模式，同时在 JSON workflow 里同步配置。

这也对应我们之前讨论过的产品形态：不是 runner 强制指定测试命令，而是 Claude 自己判断怎么测，写出自测报告；Codex 再基于证据独立验收。

## 第六层问题：thin workflow 不应该要求 TaskContract

thin case 跑到 acceptance 前又失败，原因是 acceptance context profile 把 `task_contract` 当成必需。

但 thin workflow 本来就没有 contract.compile。TaskContract 是 P5/P6 能力，不应该反向污染 thin 主线。

修复方式是把 acceptance profile 的 `task_contract` 从 `FAIL` 改成 `PLACEHOLDER`：

- 有 TaskContract：照常展示，Codex 可以用它做验收依据。
- 没有 TaskContract：上下文里显示 `(not available)`，但流程继续。

这保持了 context schema 稳定，也保留了 thin workflow 的轻量性。

## 第七层问题：revision 反馈丢失，这是最关键的流程 bug

p6 case 里，Codex 在 intake 阶段指出了 Must Fix：JSON 必须是紧凑格式、table 输出要精确。workflow 正确路由回 Claude 修订，但第二轮 Claude 没有真正吸收反馈，最后触发 revision limit。

查 ledger 后发现根因不是 Claude “不听话”，而是 orchestrator 没把 Codex review artifact 传回去。

`ReviewDecisionHandler` 读了 Codex 的 output，但完成 decision node 时返回了空 `outputRefs`。于是路由回 Claude 的新节点没有输入 refs，自然收不到 Must Fix。

修复后：

- decision node 保留它消费的 input refs。
- `NEEDS_REVISION` / `REJECT` / `BLOCKED` 后续节点可以拿到上一轮 review。
- Claude prompt 也补了一句：如果 context 里有 Codex Must Fix，必须逐条处理。

这是这轮修复里最像“产品闭环”的地方。没有这个，系统只是会循环；有了这个，循环才有信息增量。

## 第八层问题：fixture 不能制造无意义噪音

p6 后面又进入 rejection classify，原因不是功能错，而是 Python 自测生成了 `__pycache__/`，Codex 把它当成超范围文件。

真实 Python 项目一般会忽略 `__pycache__/`。fixture 不忽略它，会把 smoke 变成“缓存文件检测器”，偏离目标。

修复是在 fixture 初始化时加入：

```gitignore
__pycache__/
*.pyc
```

这不是掩盖问题，而是让 smoke 更接近正常项目环境。

## 最终验证

本轮结束时的验证结果：

- `dual-runtime` 全量单测：226 passed
- `dual-adapter-acpx` 全量单测：51 passed
- `bash -n dual-orchestrator/scripts/smoke-test.sh`：通过
- `git diff --check`：通过
- 真实 acpx smoke：
  - `thin-basic`：通过
  - `p5-contract-happy-path`：通过
  - `p6-reject-classify-path`：最新单跑 `PASS-WARN`

`PASS-WARN` 的原因是 Codex 最终直接 `ACCEPT`，所以 p6 的 failure classification 分支没有触发。这说明 p6 主流程可用，但“强制触发 reject classify”还需要单独的 deterministic fixture 或 fixture 模式。

## 这轮最大的收获

这轮不是在修一个脚本，而是在校准 orchestrator 的几个基本事实：

1. **真实 agent 比 mock 更早暴露系统边界。**  
   prompt、权限、context、decision 枚举、stdout sentinel，只有真实跑才会同时出现。

2. **workflow 的每条边都必须带信息。**  
   只会路由不够，revision 必须把 review 证据带回去，否则多轮只是空转。

3. **ledger 要可信，就必须限制写入者。**  
   Agent 可以产出内容，但账本应该由 orchestrator 记录。

4. **E2E smoke 不是越复杂越好。**  
   fixture 应该暴露 workflow 问题，而不是制造无关噪音。

5. **下一阶段需要更好的可视化和人工 gate 体验。**  
   现在 workflow 已经能跑，但人 review 时只能靠命令行读状态、events、outputs。系统的能力已经超过了 CLI 的舒适区。

## 后续遗留

- p6 classify path 需要一个更稳定的 deterministic reject fixture，避免依赖 Codex 是否自然 REJECT。
- `status` 输出里 stage/checkpoint 信息还有旧字段，看起来像 “Stage: design (pending)” 没跟 runtime 当前节点完全同步，需要后续整理。
- 当前只有事后审计和 CLI 查询，没有实时进度推送。
- 人工 review gate 还缺少一个舒服的界面。
