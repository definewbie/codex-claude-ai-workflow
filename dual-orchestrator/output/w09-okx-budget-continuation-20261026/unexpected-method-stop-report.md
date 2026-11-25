# OKX MODIFY：计划方法身份不匹配，意外停止

## 结果

按用户新授权落实了原计划人工门revise，并执行一轮计划返修。当前累计 **2,034,803 token / 9 请求 / 1979.975 活动秒**；400万/18/3600不变、不重置。三项旧缺项已改：完整AC编号、真实已登记ref/SHA、移除非GOAL要求的统一actual exit字段。但新的计划方法字符串被附加了逐AC说明，五项均不等于所引用的固定声明方法。现有消费者明确要求逐字相等；不能用“语义相似”替代。

这是以真实材料与冻结源码谓词发现的**前置身份不匹配**，不是新一次Java运行报错，不伪称完整资格器已执行。当前实验FAIL（preflight），领域仍RUNNING / node-plan-codex_plan_review-025 READY；未开始实现，未批准EXECUTION。新stop已保存，旧stop完整保留。未发送计划复评模型请求。

## 真实材料与责任边界

`evidence/plan-method-mismatch-readonly-proof.json` 固定正式声明、候选原文、逐AC追加部分、其他字段一致性和源码SHA/行锚。规则在 `VerificationReadiness.validateBindings`：`request.method().equals(binding.method())`；与executor/evidence/detailOwner等一起强制消费，违反即不能采用该验证出口。

模型并未把正式方法删掉，而是在其后追加各AC测试说明。是否语义等价不是这条资格器能判断的事实，不能临时改成prefix/contains。验收叙述与声明身份目前由模型重复拼装，这是本次最值得核对的职责点；但本轮未据此宣布生产设计缺陷已证或擅自改代码。当前已知明确结论是：这些原字节不符合现有强制规则。

## 保全与账目

九个run均完成、零pending；四个仍开放具名会话经原close/show关闭，所有本案记录现closed=true，不宣称整个进程子树已停止。cleanup前后领域账本字节一致。target/workspace产品Git干净，原项目全部已存文件与92项状态SHA不变；冻结Java/659输入/客户端组合核验通过；原包与原stop字节不变。没有原raw/sidecar/ledger补写或单例救援。

新自动授权按当前用户原话固定于automatic-decision-scope.json：仅本案既定目标、六文件和累计上限，正常决定经原CLI归一化，包含隔离交付/提交；它不豁免意外、边界、未知、范围或预算。旧预算授权文件的“不隐式委派”不作为本次新授权来源；每次新业务动作以该显式用户授权和原core guard为据。

当前余额 **1,965,197 token**。再发一轮计划作者+复评并保留全部尾链与一次修正需 **2,025,000**，缺口 **59,803**，原checker已返回STOP。因此也不能自动再试一轮。活动时间还剩 1620.025 秒；即使未来调整token，也必须独立遵守时间储备，不能只抬token遮盖。

## 最小下一步建议（未实施）

先做有界离线责任核对：能否让程序从固定readiness ref/SHA取得方法原文，模型仅在已有validation_plan等叙述字段写逐AC验证说明；保留完整内容身份、方法资格与逐条验收，不放宽字符串匹配。核对实际生产/消费位置、最小差异及成本后再决定是否实施。若这不是必要且划算的根因前向修复，就停交取舍，不新增注册表、DSL、环境认证或兼容平台，不救当前raw。

本轮没有生产修改，不开重建/全回归/CC实施复审；未来若动资格/批准边界，必须按BOUNDARY级完整要求先设计、再授权实施。不能把本轮离线数据比对当修复测试。

## 过程错误分列

在同一工具批中，方法一致性检查失败时，上下文准备单步也已发出并完成（零模型，约1.847秒，计入账目）；之后未发模型复评。该排序错误单列为operator error，不掩饰，也不把它写成产品失败。领域current保留真实已到reviewer READY的位置，不回写为旧节点。

Declared：现有readiness严格规则+当前自动执行授权。Proven：实际声明/候选/源码逐字比对（非完整Java资格器重放）、九个完成run、cleanup保全。Consumed：未来原validateBindings；本轮operator preflight已阻下一模型派发。Enforced before：没有新复评、实现、EXECUTION或目标写入。Failure route：新stop与原D06/D07/人工取舍，无自动重发。Cannot-bypass：原文、SHA、源码谓词与逐项阴性保留；未删规则、未把prefix结果当等值、未改旧事实。
