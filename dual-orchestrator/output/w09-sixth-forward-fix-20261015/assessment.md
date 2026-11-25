# W09 第六例 F1/F2 有界前向修复与发行候选

状态：IMPLEMENTED / OFFLINE_VERIFIED / FROZEN_CANDIDATE / AWAITING_AFFECTED_CC / SEVENTH_NOT_RUN。

用户授权：修复、成对回归、旧材料只读对照、重冻发行与合并CC提示；控制ROI，成本扩围即停。**不恢复第六例，不启动第七例，不提交。** 原第六例 FAIL、WAITING_FOR_RECOVERY/返修FAILED以及旧UNAVAILABLE保持。

## 改了什么

F1：原 record 是字段唯一语法来源。允许 full 的返修提示补 RevisionResponse 封闭枚举；严格 parser 将未知键保留为 UNKNOWN_FIELD（字段名、该对象合法字段集、位置），errorCode仍 AGENT_RESULT_PARSE_FAILED。acquirer把此 typed 诊断交给原唯一一次 deny-all 修正，不复制未知值/raw，不自动删键，不增加尝试或批准。

F2：唯一wire语法owner增混合stdout识别入口，复用原解码。多行正文碎片与单行{id,text}条款不算RPC；成功解析的协议形状按结构校验，真坏结构明确INVALID_CURRENT_TRANSCRIPT。原当前完成身份/重复/非负完整用量/错误检查及严格唯一共享出口保留；共享NDJSON原parse入口与权限/工具消费者不迁移。

增量：**五个原生产类、四个原测试类、两份当前规划文档**。无新生产类/schema/服务/锁/DSL/原生patch/通用纠错平台。observer同型误判在复制出的未来候选修正，旧脚本不改；它不是生产guard，也未组装成当次第七例。

## 证据与过程

- 先红4项：full返修缺枚举、unknown降INVALID_JSON、正文误杀当前完成、正文误杀严格共享出口。runtime29项中2失败、adapter11项中2失败，分两次旧生产执行，不加总成一次套件。初次错误重载属测试作者编译错误，独立保留。
- 最终聚焦7 suites /108项，含初次/patch/full、严格未知字段、合法一次修正与重复同错仍失败、两客户端正文/真实形状坏帧/错身份/重复/部分量/快照/迟到共享及原投影；operator候选12项另列。
- 旧第六例4个真实occurrence只读重放：前三值8659/27428/15144不变；修正19570可由新reader走原严格共享出口精确归因。新分析合计70801只属案头对照，不能替代旧51231部分量/UNAVAILABLE/预算authority。两份原始未知response产物在新parser下仍严格拒绝，具体诊断正确。未修改任何旧raw、prompt、run、R03或实例。
- 候选第一次历史重放抓到{id,text}条款误判；补成对后修正。第二次自查抓到数字2.0被强转协议版本字符串，显式类型核验并补反例。两轮全回归主动中断（exit130），均不作当前源码凭证；最终结果只取新一轮。
- 最终单轮 clean：1813 tests / 210 suites / 0 failure / 0 error / 0 skipped，8模块，exit0；659源码输入运行前后不变，单轮凭证见test-results.json/reactor-reports。不是1805旧绿灯，也不拼两次中断轮。
- CLI/server冻结副本、各六内嵌项目模块/自身字节、14资源、6只读CLI检查均通过；package使用跳测试，不另算回归。发行完整SHA见release-identity.json，native/config只核未变字节，未启动或宣称fresh实际配置。

旧材料保护见historical-preservation.json：第六包342条目、上一实施包1929条目及旧live191权威文件前后SHA一致（lock排除）。源码659输入在最终全回归前后及打包后精确一致。旧target JAR是授权构建目录，本次重建；历史冻结副本保持，不能说旧target JAR保留。

## 边界判断与未证

[消费者/transition](consumer-and-transition-map.md)、[验证分层/六维](verification-coverage.md)、[自查复盘](review-and-reflection.md)。新增诊断与分类在原采纳/完整总量判断前强制消费，属具名离线链已验证；未改R01/R02/R03/L03/C/X或D06/D07次序。既有前缀、冷JVM、四变体/双入口在本批全模块回归承接，不宣称为两个局部增量穷举所有写点组合。

**未证**：模型收到新提示/诊断后实际遵守程度；真实full发布/merger/lineage/新复评/当前人门/GOAL和后半主链；下一Gate实际装配/配置/工具/observer/预算。历史只读不是新运行。没有token节省/成功率承诺，不关闭整体W07/W08/W09。

当前是作者自查，不是独立CC通过。成本仍限既有owner与已有契约，不需要新平台或底层能力实验；未触发新的ROI方向取舍。

**下一步动作**：交[合并CC只读复审提示](cc-review-prompt.txt)，分别判断修复证据、发行候选与未证范围。通过后，再由用户决定fresh第七例当次Gate/预算/真实运行；当前不启动。受影响前置见[affected-gate](affected-gate.md)。
