# OKX Java MODIFY：400万续作进展与待决计划门

累计 **1,728,741 token / 8 请求 / 1714.832 活动秒**。总限 4,000,000 /18 /3,600，跨天不重置。原300万储备停止与全部原包字节保留。当前实验 IN_PROGRESS；领域 WAITING_FOR_HUMAN，门 `gate-node-gate-human_plan_approval-021`，来源 `VERIFICATION_READINESS_REQUIRED`。没有实现，也没有EXECUTION批准，不能称E2E完成。

设计完成一次真实语义返修并通过独立复评：DECIMAL128独立原值断言、同展示不同原始增量/建议的成对防绕过。计划作者及独立计划复评完成，复评 READY_TO_IMPLEMENT；但该评语不代替程序资格和当前人门。程序因验收接线前提转入等待，未放行实现。

## 已核实的计划接线缺项

1. 计划 verification_bindings 使用 TR-1..TR-4，`VerificationReadiness.validateBindings` 唯一消费方要求与GOAL acceptance_criteria AC-1..AC-5集合精确相等。
2. 计划 environment_refs 全空，feasibility_refs 指向作者提示词manifest；消费者要求已由原record-readiness生产并登记、未撤回且匹配criterion/method/executor/evidence/detailOwner的ref/SHA。提示词manifest不满足。
3. 四条绑定均新增 actual_exit_required=true/expected_exit_code=0，而GOAL的测试通过依据是程序工具观察及Surefire报告，并未统一要求底层真实退出码字段。现有本案只读调用没有证明所选客户端测试路径可给该实际退出码；不据此声称不可能，也不能宣称已可取得。

这三项是当前计划尚不能进入EXECUTION的事实，不是产品实现缺陷，不修改validator、contract或观测器绕过，也不自动启动修复/重冻项目。

## 已完成的必要材料

原入口 `review record-readiness` 已登记 `.agent/workstreams/ws-20261026T142203Z-7SNA/artifacts/verification-readiness/okx-ac-availability-20261027-01.json`，SHA `1212daf7ab19146a19336a41ac32668c60a31d76f48a6bf0292d361006129d08`；准确对应当前等待subject和GOAL epoch1，AC-1..AC-5，AGENT / PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS。Maven版本、Docker服务及mysql:8.4/ryuk:0.14.0缓存已只读复核；固定Java/Maven及观察材料SHA为环境依赖。它只证明operator声明的前提可用，不证明原生已执行测试、不提供实际exit、不批准实现。宿主原166项基线绝不替代本次源码测试。原始回执在 `operations/readiness-environment-recheck/`、`operations/record-readiness/`。

## 下一步需要用户决定

建议对当前门使用原 `revise`：让计划作者把绑定改为AC-1..AC-5、引用上述正式readiness材料，并沿当前GOAL的程序观察+报告标准消除未经证明的统一actual-exit附加要求（明确要求实际exit时仍必须取得，绝不伪造）。按原计划返修和另一客户端复评，再提交精确EXECUTION文件范围的人批。当前400万/18/3600完整尾链核对允许该返修，未来每步仍需重核，不能保证预算/时间必够。

人门和commit未委派，因此本轮未代为approve或revise。无需新Java/acpx机制、环境认证、额外原生探针或模型实验。保留六文件范围与全部质量要求。

## 证据与边界

- `evidence/continuation-fixture-tests.json`：35项整包装配/observer，替身与合成缝隙，不冒充Java/native E2E。
- `budget-dry-run.json`：26项独立算术，不与35相加。
- `human-plan-readiness-progress.json`：用量、当前门、计划身份、材料身份和返修完整尾链预算输入。
- `human-plan-readiness-checkpoint.json`：等待点固定账本SHA。
- 产品target与workspace Git干净；全部8个run均完成，pending为空；原停止和包字节保持。
- 整个续作只调整operator接线和预算授权，不改Java/acpx/契约schema；检查过程中的定位错误单列，不算生产反证。

Declared：用户4M续作批准及原GOAL。Proven：8次真实run、两轮设计复评及当前程序等待、实际材料登记/只读重核、35与26离线分层。Consumed：原validator/outcome/R03/L03、readiness owner和人工门。Enforced before：无当前合法EXECUTION与合格绑定不启动实现。Failure route：正常readiness等待，不改领域FAILED或借预算授权代决。Cannot-bypass：原资格守卫实际将本计划挡在实现之前；本轮未删守卫、未写账本事实。
