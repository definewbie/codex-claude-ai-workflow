# OKX Fresh E2E：引用纠错修复后

状态：FAIL / DESIGN_SCOPE_INVALID；唯一样本 `ws-20260912T081920Z-KPPZ` 停在设计发布前，workflow 为 WAITING_FOR_RECOVERY / evt-000015。NATURAL E2E NOT PROVEN；不重试、不创建第二样本。

用户已要求继续；复用[现有 Gate](../verification-gates/20260909-okx-human-reviewed-scope-fresh-e2e.md)的产品目标、完整消费链、权限和止损。仅一次 fresh 样本；GCTW、245D、BXWV及其他旧样本只读，不恢复、不手改账本、不使用 autorun、不启动额外 agent。产品交付止于有效工作区和 accepted candidate，不向原 target apply/commit/push。

## 运行身份与前置

- 代码 checkpoint `10cd2fbe`；当前文档后代 `60725e64`。独立 diff review PASS，完整 package 1493/0/0/0。
- JAR SHA `38d49979291909696b429d76f678f74db09b58388aa3ca01c332f8ce0b48b448`；external small workflow SHA `3ea9ea6c567608f4593bfef65f5868d7cbbd1be0925315c650abd8ec3f1896d5`（28/51/8/6）。其余 target/task/check/fork身份沿 Gate 核验，不更换需求。
- 同日 health 复用来源为 `output/real-cases/20260912-okx-external-evidence-e2e`：Claude 单 Read nonce 成功、exit0/end_turn、adapterSignals空、实际session记录已closed；Codex无工具health end_turn。当前完整配置SHA与Gate冻结值一致；本次修复未改 transport/profile/policy/wrapper/fork，不重复运行相同探针。复用只证明既有健康证据仍适用，不宣称实时quota可用或本次真实纠错已成功。
- 必需浏览器环境沿用同日宿主标准Playwright成功证据；这不是产品浏览器验收。若需外部材料，使用既有 prepare/submit/self-test/acceptance 全链，不用文字摘要替代绑定材料。
- 证据目录：`output/real-cases/20260912-okx-authored-correction-e2e`。三环境变量沿 Gate，timeout=0。每段前复核配置/工件，quota暂停提示切换，其余缺陷按Gate止损；时间长本身不判失败。

## 执行记录

1. 身份/同日health复用机械核验、Doctor、workflow validation及原source check全部通过；证据已归档。start未带run，创建唯一 `ws-20260912T081920Z-KPPZ`，分支 `codex/okx-authored-correction-20260912`，工作区 `/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260912T081920Z-KPPZ`。
2. 新workspace capture/verify均PASS，`verifiedAt=2026-09-12T08:20:10Z`，身份与check SHA吻合。随后才允许业务执行。
3. Intake 首轮 product review 正常要求补充九列位置/decimal-safe 映射、同库应用上下文关闭重启验证、图表实际输入及定时更新断言；修订后 reviewer 判 READY_FOR_PRODUCT_REVIEW，无剩余 must_fix/contract_obligations。这是合法语义修订，不是 execution recovery。
4. 主 agent 按用户逐门委托阅读全文并批准 requirement gate-011；当前 proposal SHA `ff57e4a38c65b798360d8b7b6cbac9d5b2041846b1298b86092f3839b3996723`、binding SHA及八工件SHA冷读通过。批准 note 明确默认离线测试不免除最终 E2E 真实 OKX 证据，未加入历史样本的额外机制。
5. contract.compile-012 的合同与当前提案 objective/scope 直接等值，non_goals/acceptance/test/constraints 按既有编译的 text 投影逐项等值；合同与resolution SHA对compile账面值复验通过。主 agent 批准contract gate-013，进入design。机械核验分别归档 requirement-gate-cold-check.json 与 contract-gate-cold-check.json。
6. `design.claude_design` / `node-design-claude_design-014` / `run-1789202097910` 正常返回 exit0/end_turn；持续307933ms，adapterSignals空，未超时、未中断。模型配置及JAR SHA前后未变。调用11项只读工具（Read9/Glob2），零写入/Bash。
7. 输出的 `checks` 自称 planned_files 与批准范围34路径逐字一致，但实际只有8项：7个授权路径加 `src/main/java/com/example/okxkline/okx/okx 占位`（CREATE，responsibility=占位），另外27个合同路径未列。发布校验命中 `DESIGN_SCOPE_NOT_AUTHORIZED`，产生 `DESIGN_SCOPE_INVALID` 诊断，节点FAILED，进入WAITING_FOR_RECOVERY。未生成设计sidecar，未创建design reviewer后继，未进入实现。

## 原因与边界判定

```text
提案修订与审阅 -> 需求批准 -> 合同编译/批准
  -> design.claude_design 正常返回 JSON
     -> schema 校验通过
     -> 设计范围校验拒绝“okx 占位”
        -> WAITING_FOR_RECOVERY（发布前停止）
        X 设计reviewer / 实现 / 自测 / 验收 / 交付
```

- 已确认的直接原因是模型产出了不完整且包含未授权占位项的设计。原始prompt第38-39行已要求完整文件集合及复制合同精确路径；合同也实际被模型读取。因此不能归因为未提供批准范围。
- 最终ACP assistant message按messageId重组，9952字节，SHA `c8d0f9dbed06b6028181d433e88b108903f65c2e253ab93bf265cbcb874a722e`，与run中rawAssistantSha256/candidateSha256完全相等，JSON树也与保存候选相同；协议结束原因是end_turn。未观察到传输截断或adapter改写。模型为何选择以占位结束无法从这些事实确定，不把猜测写成根因。
- harness的拒绝发布生效，模型“34路径一致”的自述没有成为authority。schema合法不等于符合TaskContract。未列全27项是额外内容事实；本次实际拒绝码指向未授权占位路径，不把未执行的其他校验描述为已证明。
- `revision_responses=[]` 是首次设计且无active义务的合法形态，没有坏引用ID。本次有界引用纠错 `correctionAttempted=false / NOT_NEEDED`，自然路径NOT EXERCISED。不能用替换ID的纠错权限补造27项职责，也不能先删除占位条目伪造完整设计。
- 当前没有走设计不完整的自动语义修复；这是本样本的续作限制，不是安全校验失效。本记录不以单样本宣称所有harness缺陷已排除，也不默认开工新修复机制；不改变冻结输入、不在原样本手工修JSON或retry。
- 原始run.executionAttempt.retrySafe=true与recovery摘要Retry Safe=false并存；实际executionOutcome.retryPolicy=MANUAL_ONLY且workflow已停。此处如实登记为诊断投影差异，未将局部布尔值当作自动续作授权，也不是本次失败原因。

## 收口核验

- `failure-evidence-check.json` 保存原始工件ref/SHA及机械断言：8项/1项未授权/27项未列、raw stream与候选相等、零sidecar/零reviewer后继、原target与effective workspace均clean。无产品代码写入、无外部交付副作用。
- 样本三Claude会话与一Codex reviewer会话均已经acpx正式close并重读closed=true；没有运行中的业务调用。旧样本ledger未改。
- 结论为有效执行中的产品设计契约失败（FAIL），不是INVALID、quota或超时。完整产品E2E、外部材料链、引用纠错自然行为、accepted candidate均未证明；后四段尚未到达，记NOT EXERCISED。
- 唯一新增证据来自本样本；不以新的自动retry、放宽scope或再开样本弥补本轮失败。

逐门阅读全文与typed证据并作合法决定；不人为制造引用错误、scope违规或外部材料分支，未触发记NOT EXERCISED。模型纠错通过仍必须进入正常reviewer/human裁定，不等于产品任务完成。
