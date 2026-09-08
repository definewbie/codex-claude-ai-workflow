# 验收返工批次A：反馈裁剪与建议投影

日期：2026-09-08。依据：已通过独立delta评审的 `acceptance-repair-minimal-design-20260907.zh-CN.md` §7批次A。
状态：IMPLEMENTED / INDEPENDENT DIFF REVIEW PASS / FULL REACTOR PASS（未提交）。
目标：P0反馈消费稳定性；不实施本计划批次B的F1/F2/F5，不恢复SPM9，不调用provider。

## 变更与边界

| 项目 | 修改 | 唯一owner及下一消费者 |
|---|---|---|
| F4过度裁剪 | 每删一节重建实际文本、刷新统计，再判断是否足额；已删除正文renderedTokens归零 | ContextProfiles定义策略，ContextBuildPackageHandler执行；发布包由prompt renderer消费 |
| F4同边界统计错误 | header替换范围覆盖原治理表，避免最终重复一张表；裁剪前使用刷新后的header | 包metadata的estimatedTokens取最终Markdown，header Total仍是既有分项汇总 |
| F6建议丢失 | 按FailureClassification真实JSON键suggested_action读取，不加camelCase fallback | FailureClassification仍是原分类事实；renderer只展示，不改变路由 |
| F3撤回项 | production零修改；增加真实publication/runtime反证及合法notes正例 | 既有StageOutcomeCompiler语义守卫保持 |

范围：2个production文件、3个测试文件。其它Phase A/transport未提交变更不在本批；不覆盖或提交它们。
预算、优先级、PRESERVE_FULL/FAIL策略未改。必保留内容超软预算的合法情况仍如实报告，
不能宣称所有成功包withinBudget=true；无法容纳的非豁免内容仍返回失败，不发布新包引用。
重复header是本轮测试发现的同一实际计数缺口，修正前可令发布后包长度超过裁剪所检查的文本。

## 生命周期核对

正常：读取既有事实→解析section→按策略渲染→实际整包预算检查→必要裁剪→发布Markdown/metadata→下游读取。
拒绝：不能容纳的固定边界内容→FAILED/空outputRefs，不派发新的成功包；无新恢复状态。
F3：agent typed结果→真实runtime的publication语义校验→矛盾即FAILED→WAITING_FOR_RECOVERY；
无成功sidecar、无decision后继、无goal。冷runtime重读仍停在recovery，不重试该无效验收。
本批不改变多文件写入/已有旧包的R1崩溃语义，也不新增自动清理或补偿。

Sibling核对：所有governed profiles共用该裁剪循环；implementation与acceptance分别反证。
ReportGenerateFinalHandler已读取suggested_action，无需改；FailureClassifyHandler内部candidate的
camelCase是内部map，非该JSON reader，未误改。旧任意camelCase fixture不构成兼容承诺。

## 验证

- 聚焦四类：ContextBuildPackageHandlerTest 61、ArtifactContractFixtureRegressionTest 5、
  StageOutcomeCompilerTest 4、WorkflowRuntimeTest 10，合计80/0/0/0。
- F4突变：临时禁用“足额即停”，两个profile测试均因human_feedback被多删失败；已恢复代码。
- F6测试使用真实FailureClassification序列化，修改前Suggested Action断言失败，修改后通过。
- 新增拒绝矩阵为ACCEPT/ACCEPT_WITH_NOTES分别与blocking或must_fix冲突，走真实runtime；
  report/delivery是不可达哨兵，证明未创建/执行后继，不代表它们内部算法另获验证。
- notes携非阻塞missing_evidence的正例通过真实publication，不暗中收紧原合法语义。
- 首次沙箱内full reactor因两项进程枚举测试的sysctl权限失败；不是本批断言失败，未跳过测试。
  已在允许进程枚举的执行环境重跑完整离线测试，命令退出0；164份Surefire XML聚合
  1418/0/0/0（tests/failures/errors/skipped）。两项此前受环境阻断的测试亦通过，未跳过。
- git diff --check通过。只执行test，未package；现CLI JAR仍是旧SHA
  `cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70`，不得拿它声称包含本批修复。

## 独立评审

Boyle只读核对2production+3tests，未修改文件/运行Maven/provider，结论：
“未发现本批新增的阻塞性缺陷；边界PASS，最终测试验收待full reactor结果。”
原评审对测试突变的说明来自主线程实跑，本次不冒称独立复现。

Declared by：profiles/序列化契约/StageOutcome；Proven by：实际包、metadata、publication与ledger。
Consumed by：prompt renderer与runtime路由；Enforced before：包发布、成功sidecar与后继路由。
Failure route：超额失败或既有recovery；Cannot-bypass：上述内容保留、拒绝、冷恢复与突变测试。

## 下一步与反思

批次A不等于验收返工整链修好。F1混合分类、F2重新提案批准、F5累计预算仍待批次B整体实施。
F3不重新记为产品缺陷。本次将测试断言从“包够短”改为“删够且反馈仍被消费”，并用真实
序列化与runtime代替手写字段/只测envelope，避免再次用局部绿色冒充下游闭环。
