# 实施前边界与范围

2026-10-28；依据用户本次有界实施授权与 `../w09-readiness-method-boundary-audit-20261027/assessment.md` 的完整转换表（§5）和六维（§7）。该表原样适用，不新增写点。

- 目标：P0。让模型输入准确投影既有 method 身份与 validation_plan 细节的职责；不声称模型遵守或 OKX E2E 已证。
- SoT：原 readiness 登记 ref/SHA、typed plan、批准合同及执行观察。两处提示只读投影，不授批准。
- Definition：terminal-delivery@5/原 schema；Instance：原 case 节点；Execution：原九次固定 run。本批测试使用隔离 fixture，禁止调用原 case 的推进入口。
- 修改上限：两个已有 runtime 类共享一段指令，现有测试族覆盖作者/返修/复评/correction/严格拒绝；无新 DTO、schema、锁、服务、选择器或迁移。
- 既有顺序：登记/事件 → GOAL 上下文 → R01/claim/dispatch/R02 → raw/唯一 outcome/发布/R03/L03 → exact-subject 复评 → plan.decision readiness → EXECUTION 编译/批准 → dispatch → 观察/自测/独立验收/交付。写入前缀、中断、未知与终态沿 D06/D07/CX/D09，不改写序。
- 补齐路径：无声明的初份计划仍可到原等待门；不能新增提前拒绝而关闭登记出口。未批准仍零实现。
- 字符串检查与批准比较不改。method 后缀、错误 SHA、缺声明、漂移仍拒。validation_plan 不制造观察或豁免验收。
- 新发行是独立候选；不替换原 case 冻结 JAR、身份、账本或原始模型产物，不暗加兼容。新旧运行身份的准入留给后续 Gate 决策。
- 当前累计：2,034,803 token / 9 请求 / 1,979.9747878720627 活动秒；4M/18/3600 不变。停止记录保留，本批不消耗真实调用、不为原案例追加时间回执。
- 验证：先红后绿、实际完整提示与一次 correction、本地真实 ledger/合法与拒绝序列、原关键写点与冷 JVM、全模块 clean test、独立发行字节核对；合成输出不冒称真实模型。
- ROI 止损：若必须扩大为新解析/填充机制、历史迁移或改变批准/状态，立即停下交用户；不自动委派 agent，不提交。
