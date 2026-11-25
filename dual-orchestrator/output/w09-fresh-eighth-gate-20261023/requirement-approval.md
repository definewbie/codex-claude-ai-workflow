# 第八例：当前需求批准材料

当前状态：WAITING_FOR_HUMAN，**正常业务暂停，不是实验失败或主流程完成**。

当前门：`gate-node-gate-human_requirement_review-006`；workstream：`ws-20261023T005431Z-9YYF`。可选动作：approve / revise。

## 请决定的内容

在空的隔离 Python 仓库提供 `normalize_newlines(text: str) -> str`：CRLF与单CR转为LF，其他字符及末尾有无换行保持，纯函数且幂等，仅用标准库。交付函数、unittest和README。

验收：

- AC-1：至少八类输入有明确期望，并验证幂等性，包括空串、LF、CRLF、单CR、混合、Unicode、末尾有/无换行和连续空行。
- AC-2：实际执行 unittest，保留实际输出和用例数、零 failure/error，并核对测试源码；不强制独立测量的真实退出码，也不能用“已完成”替代执行证据。
- AC-3：README导入及混合换行示例与实际代码一致，由实际读取或执行例子支持。

`src/normalize_text.py`、`tests/test_normalize_text.py`、`README.md`仍只是提案路径。**本次需求批准允许形成GOAL并进入设计，不授产品写权限**；实际文件及CREATE/MODIFY仍经后续EXECUTION批准。无依赖、网络、数据库、浏览器、后台服务或机器配置修改；不改.git/.agent，不扩展Unicode换行语义或补齐末尾换行。

## 已完成的实际证据

Claude Code / GLM-5.3提案成功，Codex独立复评READY，无must-fix。两请求固定可归因用量分别24,216与27,546，合计51,762；无格式修正、无语义返修。两个请求的SDK金额/缺金额与token独立记录，不当实际订阅支出。活动执行时间约144.708秒；当前人门等待暂停活动计时。产品树仍为空，未实现、未验收、未交付。

完整当前提案（以下相对链接指向同一持久隔离实例）：

- [当前提案引用](../w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/artifacts/task-contract-proposals/node-intake-claude_brainstorm-002-cd7b184f400271de.ref.json)
- [完整typed提案](../w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792717427983/outputs/intake-claude-brainstorm.contract.json)
- [Codex独立复评](../w09-fixtures/eighth-20261023/worktree/.agent/workstreams/ws-20261023T005431Z-9YYF/runs/run-1792717558540/outputs/intake-codex-product-review.json)

主体身份及SHA固定于requirement-gate-checkpoint.json。上次“批准”已用作本例运行授权，未拿来填写此业务门。**下一步：用户批准当前需求，或指出需要返修之处；随后只能通过原批准入口消费当前门，再进入GOAL编译及设计。**
