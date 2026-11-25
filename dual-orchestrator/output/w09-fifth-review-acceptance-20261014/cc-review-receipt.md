# W09 第五例 CC 受影响复审接收（2026-10-14）

接收：Gate FAIL正确；真实SHA错字拒绝和三请求用量归因成立。第五例不恢复、不改产物或原固定事实，不沿用余额；本报告未授权修复或第六例。identity-recheck.json单列原现场326条目和656源码身份检查；不把今日核对冒称运行当次前提。本次零native/provider、零测试、零代码或配置修改、零额外agent。

## 需要收紧的评审结论

1. “ID绑定后SHA只是冗余”只能针对模型侧转录义务，不能泛指程序侧完整性保证。proposalId实际是node instance id加SHA前16字符（TaskContractProposalReference），不是独立于内容的无条件全局唯一保证。可研究模型只引用当前ID，由已验证的context取得完整ref/SHA；原context的workstream/occurrence/谱系以及实际字节完整SHA必须仍被唯一消费者强制核验。仅删除merger:27不是完整方案。AgentResultEnvelopeValidator:288当前必填模型base_proposal_sha256，故至少需调整现有wire契约/validator/渲染/固定产物来源及其测试，不宜称为无需schema调整；这不意味着要建新平台或全量D10追踪。

2. “语义出口存在”不证明当前入口接通。ArtifactContractValidator:240–244明确在context.revision时拒绝full task_contract_proposal；TaskContractRevisionContextResolver:128–134对NEEDS_REVISION返回revision=true。GOAL失效规则是设计要求，返修作者不能仅自行换形态绕过它。需要核对受支持的目标级重提案路径或具名升级接线，保持原义务、谱系、独立复评和当前GOAL重批。不能反向声称本例已经触发第二拒绝/死端：它先止于SHA，未实际到达该路径。

3. “约77万可归因token”不是全部已固定且准确归因的总量。按第二例原生63493、第三235366、第四诊断403420、第五62136相加为764415；其中第四70687明确未进入原权威usage/预算，第二例原run总量仍是错误53096，63493来自原native材料后续只读核对。可以分别报原生诊断量/正确固定量/不可归因状态，不应混作可用于准入的总量。首例无实际prompt，总用量不能从UNAVAILABLE填0。所谓“三年前GCTW”无本轮证据支撑，不采信时间修辞。

## 建议给用户的ROI取舍

建议现在设计模型身份转录减负，并与目标级返修出口一起补齐，合并一次受影响评审。前者移除已发生的高熵复制负担，后者影响主流程合法表达，两项仍限定现有owner。没有已测工期或固定节省承诺，不能把“最小”当成本证明。若发现需要新状态平台、通用patch DSL或超出既有owner扩展，停止交取舍。

拟定可审范围：模型选当前base id；程序在可信context边界承载ref+完整SHA；错id/错occurrence/跨workstream/基准漂移拒绝。普通条款返修仍patch；目标/禁区变化需显式有据的重提案路径，保留旧义务并重新独立复评和GOAL批准。具体入口、durable write、旧格式支持/拒绝与失败前缀在设计材料中冻结，不能实施时发明。

下一步：用户决定是否采纳此设计范围；获准后补具体接线和测试设计，合并CC复审后再实施。现阶段不开始代码、不运行第六例，不宣称D10/W07整体完成。
