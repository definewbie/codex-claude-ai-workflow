# 当前 GOAL 需求批准请求

工作流：ws-20261026T142203Z-7SNA  
当前人门：gate-node-gate-human_requirement_review-006  
独立复评：READY_FOR_PRODUCT_REVIEW；无 must-fix。

批准的产品目标：在既有 assess-income phase-one 中增加“90天累计资金费率追平阈值”，以相同口径的简单赚币收入、完整已知成本和对冲名义金额计算。服务唯一计算，typed结果承载，CLI只展示。

验收重点：(180+20)/10000 的标准样例为2%；0.019/0.020/0.021对应增量-10/0/+10；BigDecimal与除不尽精度；缺成本、未确认条款、无历史结算、开放期末保持不可用；原建议/上限/风险不变，相关与完整回归通过。不交易、不新接外部服务、不扩Web。

本批准只建立GOAL需求依据，不授实现写权限。当前提案列出的约6文件仍是候选，后续设计/计划后需单独批准精确EXECUTION合同；交付和commit仍由用户决定。

真实用量与当前subject/复评ref+SHA见 goal-gate-progress.json。当前累计253,090 token、2请求；等待期间不新增派发，不将人等待计入活动时间，不创建实验停止标记，也不改领域账本。
