# OKX Java MODIFY Gate 只读复审处置

本次复审认可产品切片和离线材料；fresh 装配未完成，预算及真实运行未批准，不作启动资格声明。

LOW-1 已核实：历史排除清单逐项对应 11 个源码类，均声明 MySQLContainer(mysql:8.4) 和 @Testcontainers，均有本次完整回归的 XML 且零失败、错误、跳过。7 个类名含 Mybatis；另 4 个为 SyncMarketCliRunnerTest、FundingRateBackfillServiceTest、FundingRateSyncServiceTest、AnalysisRunServiceTest。计数不能只按类名推断。逐项源码/XML SHA 与计数见 evidence/mysql-suite-count-confirmation.json；它是原回归证据的核对，不是新测试轮次。

预算比较补正：1.61M 是较早的规划路径，不是完成 E2E 的实测最坏值。当前校准来自一个小型 Python CREATE 案，实际 2,077,707 token / 13 请求；新 3M 上限比该实测约高 44.4%，仍是 Java MODIFY 的规划假设，不是足够跑完的保证。见 evidence/budget-calibration.json。此前通过案的产物是 Python 文本规范化工具，不应描述为对 Dual Java 产品的修改。

下一步最小动作：复用现有 E、预算 observer 和具名授权绑定离线装配 fresh 包，验证默认未授权时零执行；不新增机制、不修改原项目。装配完成后再交预算与真实运行批准。本轮只完成 LOW 核对与材料处置，未装配、未启动原生会话或模型。
