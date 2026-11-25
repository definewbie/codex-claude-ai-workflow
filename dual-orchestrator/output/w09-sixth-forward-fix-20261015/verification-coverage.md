# 验证分层

| 层 | 实际证明 | 不外推 |
|---|---|---|
| 旧生产先红 | runtime 两项：缺 full 枚举、unknown 降 INVALID_JSON；adapter 两项：正文误杀当前/共享 | 最初测试错误重载不计先红；未把最终所有新增测试称作逐项先红 |
| 最终聚焦 | 7 suites /108项；完整合法/拒绝、一次修正、两客户端、坏/错/重/部分与快照、原 projector | 离线协议/ScriptedTransport，不是实际模型行为 |
| observer候选 | 12项，含固定完成/日志 SHA/身份/聚合/迟到共享/正文/坏帧 | operator只读核对，不是生产预算强制点；未当次Gate冻结 |
| 第六例原字节只读 | 4 occurrence /两个未知response仍拒；新reader正确归因四次，诊断70801 | 不回写51231/UNAVAILABLE，不恢复第六例，不称新真实运行 |
| 本批全模块单轮 | 见 test-results.json 及 reactor-reports，完整源码前后身份；承接既有ledger/前缀/冷JVM/四变体/两入口 | 首轮中断无收口效力；没有为新增诊断穷举每个写点 |
| 冻结发行 | CLI/server副本、各内嵌模块/自身字节、资源、六只读CLI入口 | package跳测试不另计回归；未运行客户端/installer/provider |

## Cannot-bypass / 正反成对

- 未知 response 不可被 parser接受；成功修正只能返回封闭记录，仍经原validator；同错第二次只失败，不再第三调用。
- full分支字段来自record；patch有限表达与allowedForms仍由原resolver/资格/发布链控制，提示不授资格。
- 正文多行括号与{id,text}合法，同时已解析坏RPC结构必须明确不可用；当前错误不能借历史成功，空当前才严格唯一回退。
- 错请求/会话/stop、重复完成、负/小数/溢出/部分token与snapshot原常驻反例保留；历史追加不能撤销固定调用。
- 固定完成与当前日志SHA有观测器反例；新分析结果与历史事实分离，old live/snapshot/材料manifest前后核同。

## 六维

Declared by：用户有界实施授权、CC F1/F2与原封闭/归因契约。
Proven by：上述分层凭证；活客户端遵守新提示与后半主链未证。
Consumed by：唯一parser→acquirer→原validator/outcome/R03；唯一wire→reader→固定occurrence/aggregate→原metrics/observer。
Enforced before：未知字段在候选采纳/sidecar前拒；不可归因/部分量在完整总量采纳前拒。预算派发仍由当次operator Gate核，不冒称新生产guard。
Failure route：原唯一outcome/D06/D07/一次correction，UNKNOWN不转许可；旧例保持FAIL/WAITING_FOR_RECOVERY。
Cannot-bypass test：上述常驻正反、4项旧生产先红、108聚焦、完整回归与原字节重放共同支持；不是独立CC评审。
