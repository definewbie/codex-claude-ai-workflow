# CC 只读设计评审执行边界

状态更新：用户明确只要提示词，不要直接调用 CC。此前误启动的 CLI 在仅有连接重试、无读取/评审输出时已停止。本文保留为尝试记录，不构成继续调用授权；结果为未完成评审，不能报告设计 PASS/WARN/FAIL。

- Target: 一名 CC reviewer 独立评审 20260923 整体设计与验收契约；不是测试 provider 或产品 E2E。
- Source of truth: 本次冻结文档 hash、CC CLI 结构化输出及实际读取轨迹；review verdict 仅代表设计意见。
- Trigger precondition: 用户明确要求 CC 评审；设计已落盘、引用存在；工作区已有变更保留。
- How precondition is checked before run: 保存受保护文件 hash；检查设计引用和 diff whitespace；使用已安装 Claude Code 的只读工具白名单与 safe-mode。
- PASS: 返回有证据的完整设计评审；不要求结论必须 PASS。
- FAIL: 试图写入、运行外部任务、委派、超出只读范围，或超出本次评审运行限额。
- INVALID: 认证/连接失败、没有有效评审输出、文档身份漂移或工具不可用导致无法核验；不冒称已评审。
- Stop-loss: 最多一次初始评审请求，15 分钟外部进程上限、最多 50 个 assistant 消息；权限不满足不放宽为写权限，不自动换模型或连续重试。连接故障先诊断，不以产品样本补证。
- Run artifact freshness: 运行前冻结设计与两份入口文档的 SHA-256；记录 CLI 版本/本次 argv；返回后检查文件内容未被 reviewer 改动。
- Authority: 仅本次只读 CC 评审。生产实现、历史恢复、真实产品 E2E、模型/付费配置变更均未授权。
