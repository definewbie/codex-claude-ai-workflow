# 三类边界验证清单：交付与自查

2026-09-29。本轮按用户明确要求产出 [验证清单](../../docs/testing/20260929-d04-three-boundary-verification-checklist.zh-CN.md)，只细化必要验收，不运行测试/探针或修改产品。清单 N/S/D 是验证索引，工作状态仍由现有 W03/W04/W05/W06/W08 与主清单维护。

## 评审处置

接收用户带回的 §8.21 设计 PASS，保持能力未证、D04 未冻结。LOW-1 已加入 D1–D3：顶层收尾后、验证中及发布间隙的迟到写入/源码漂移，旧证据拒绝与精确历史复用成对。LOW-2 加入只读与 implement 两角色，但将原生 policy 禁止的副作用前拒绝，与精确业务 scope 变更后拒绝采纳分列，不自动扩大为逐文件 OS 写前拦截。INFO 的 Claude 后代文案不作为实测证明，沿用已记录的证据边界。

## 核验依据与范围

本轮定点读取 ArtifactValidateHandler、EvidenceCollection、ExternalVerificationEvidence 及前轮 Claude 证据处置，ref/SHA/行段见 source-checks.json。确认 scope 路径消费实际变更；EvidenceCollection guard 不适用时直接执行。因此两者不能泛化为所有执行面/所有验收均已闭合。没有全量源码审计、没有重新运行既有测试。

本轮使用 boundary-first-engineering 与 architecture-governance-review：先区分 native 权限、领域 scope、源码/证据 identity 的 owner，再修订验收层次；自查实际消费者、失败切面、停止/恢复、冷启动与 sibling，最后回查现状边界。结论为**验证设计待复审，能力 DESIGN GAP**，不是独立评审 PASS。

## 一致性与六维

- Declared by：R3 I3、详设 §8.21–§8.22 与用户要求；不撤销批准/scope，不隐增实现阶段逐文件写前拦截。
- Proven by：现有代码定点依据与旧 native 父执行切片；新增用例均 NOT RUN，不填造子任务实证。
- Consumed by：N 由引擎工具入口消费有效 policy；S 由 ChangeScopeEvaluator/ArtifactValidateHandler 及后继消费合同与实际变更；D 由实际验证/发布/交付入口消费源码和证据身份。
- Enforced before：N 对具体禁止副作用；S 对不合规成果正常采纳/继续；D 对错基准证据采纳。缺批准的 dispatch 另有前置。对象不可混写。
- Failure route：原始权限/违规变更/证据基准失配及持久化不明分开；不盲重发、不自动回滚，不把节点 COMPLETED 当业务通过。
- Cannot-bypass：N1–N3/S1–S2/D1–D3 的正反流程、关键写间失败、冷启动与相关角色/执行入口待验证。移除检查不能靠后层另一种拒绝掩盖该边界失守；合法流程不能一并封死。

当前清单、主文 §8.5/§8.21/§8.22、§13 冻结行、主清单 T07/T09/T14 与当前入口已同步。历史报告/受审包不改判，旧 manifest 的 live 文档引用由本轮 before 快照解析。仅新增一份测试设计文档、修改四份现有文档并保存材料；其他 baseline 文件保持原字节。

没有新 Gate/预算、native/provider/agent 或实施授权。本轮未运行任何原生实验或产品测试。建议对本轮分层澄清做一次简短 CC 材料复审，尤其检查是否意外降低 scope 验收或扩大原生强制范围；[提示词](cc-review.txt)。
