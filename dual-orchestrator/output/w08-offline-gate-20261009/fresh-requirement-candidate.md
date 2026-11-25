# Fresh 任务需求候选：换行规范化函数

状态：未批准、不授实现权限、未创建repo/workstream。仅为Gate预算/出口比较提供具体对象，用户仍可更换。

为一个新建、只含README与初始提交的小型本地Python仓库增加 `normalize_newlines(text: str) -> str`。将 CRLF 和单独 CR 转为 LF；保留其他字符、空串、已有LF及末尾有无换行。给出标准库unittest测试与README用法。不得引入第三方库、外部网络、数据库、浏览器、后台服务或修改机器配置。

建议文件提案：`src/normalize_text.py`、`tests/test_normalize_text.py`、`README.md`。它们在此只是待设计/计划审查的提案；首次实现前须在EXECUTION精确批准CREATE/MODIFY。禁止其他产品路径，control工件由程序owner写入。初始target README已存在，CREATE/MODIFY须由实际目标/工作区核定，不照抄本文件假定。

## 验收条款

- AC-1：空串、纯LF、CRLF、单CR、混合换行、Unicode、末尾有/无换行、连续空行至少八种输入具有明确期望，规范化后重复处理保持相同结果。
- AC-2：测试实际执行；实际输出明确显示覆盖数、零failure/error；程序工具观察和报告内容支持此结论，不接受模型只说“测试全绿”。不统一强制原生工具提供真实退出码；若最终获批条款明确要求退出码，仍必须取得且不得自行降低。
- AC-3：README例子与代码一致，包含导入和典型混合换行输入/输出，实际读取源码/文档或执行例子支持这一结论。

AC-1/2 拟用 AGENT / PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS；作者实际本地测试、程序观察与独立reviewer的证据核对，允许合法复用同源码同合同的固定观察。AC-3用原自测/独立验收语义链，不另建证据种类。

独立Codex客户端检查实际源码和当前测试证据，是流程推进的必要步骤；模型自评不代替独立验收。它不另编成要求作者在自测前证明“后继独立验收已经发生”的产品条款。

## Gate 的交付确认（不作为自测/验收前的合同条款）

最终用户批准后，指定candidate应用到新target，用户实际commit，Dual finalize核对目标精确parent/tree/clean与操作停止事实；COMPLETED/APPLIED/COMMITTED分别呈现。这是已验收产品的后继交付及Gate终局判据，**不编为要求自测/独立验收事先证明的 AC-4**，以免“先验收才能交付、先交付才能验收”的循环依赖。delivery owner消费原批准及程序观察，加用户实际commit；不伪造为agent工具exit，也不新增 verification evidence kind。

EXECUTION前要落到实际verification_bindings，不以本候选表代替已编译合同。若必需测试工具在真实adapter内不可用，先走已实现readiness/prepare/submit出口并核准证据类型；不默认把operator声明当机器执行。没有可用出口则停止，不临场加服务或放宽条款。
