# B03 主流程优先检查点（2026-10-07）

状态：IN_PROGRESS。P0 主流程可靠性；此切片不关闭 B03/W01/W02/W03/W08，不解除真实 NO-GO。目标是按用户已接受的首版规则推进常见任务，避免局部声明精细化挤占主流程接线；“80%”没有被测量，不声称成功率。

## 范围和现有 owner

未改生产领域代码。两个测试类补边界反证，旧 smoke 与活动文档退役/迁移，共享 emitter 只加用途限制说明。前轮 CC LOW-1/LOW-2 与用户决定见 [处置](cc-review-disposition.md)；实际身份缺口见 [入口核对](native-identity-gap.md)。既有 dirty 改动未回滚，增量不混称全部工作区变更。

Definition：批准合同与冻结 node.config 拥有声明。Instance：现有人工门及 execution occurrence 拥有批准。Execution：当前 session/prompt/request/工具观察由 adapter 转 typed signal，经既有 R02/R03 固定。模型正文不拥有 permission assessment，projection 不产生资格。

## 转换和失败切面

| 前态/触发 | owner / 顺序 / authoritative 后态 | 消费、拒绝和恢复 |
|---|---|---|
| 当前 prompt；两个权限请求排队 | projector 逐条登记 request→按各 RPC id 消费实际 option kind；B 先 deny、A 后 allow | health 按观测顺序消费；A 的批准不清除 B 的阻断，不移用 operation；重复 B reply 无新决定。此处零 IPC 回复副作用，C/X 不在该测试范围 |
| 同 id 两个不同请求，或后继 prompt 重用旧 id | projector 保守标关联不确定，UNKNOWN/no OperationRef | health 产生 PERMISSION_BLOCKED，不可作为非必需证明；独立新 id 的合法关联仍可达。不是完整重启去重协议的证明 |
| 合同含两项必需义务，但仅一项声明；执行前真正批准 | 真实编译→候选可见→人批准→inline/worker 固定执行观察→唯一 permission assessment | 局部声明不能豁免另一义务；UNKNOWN/permission_blocked，无新成功 sidecar，WAITING_FOR_RECOVERY。既有完整声明与独立证据正向序列保留；运行中补声明入口没有被证明 |
| 任意旧 smoke 调用，包括 help/all/manual/skip-build | 最前脚本入口给具名 RETIRED，exit 2 | build/工作区创建/批准/native/provider 均不进入；退出不能被报 PASS。活动指南改用离线全模块回归；原始脚本/教程保留为历史 bytes |
| native 版本/配置展示看似相同 | config/launch 真实 owner 已存在，但尚无完整实际内容身份输出 | 未增加 Java 解析器或伪 identity；W03 producer→R01/dispatch/recovery 待接，不以现有版本常量授同版恢复资格 |

本轮没有新增 durable write。权限结论的 R02/R03 固定、R03/L03 后失败、冷 JVM 读取沿前轮真实离线序列与共享 D06 用例；新三个协议方法不冒称写失败/冷启动/原生 capability 已证。已有恢复出口按原 D07，UNKNOWN 不自动重发。

## 验证

最终 [verification.json](verification.json)：单轮 `clean test` exit 0，1,631 tests / 198 suites / 0 failures / 0 errors / 0 skipped；637 个构建/测试输入前后 SHA 相同。原始 XML 已归档 reactor-reports。前两次 protocol-focused 是测试作者错误（不存在的 accessor，错误的事件消费顺序），均保存失败日志，不包装为生产反证先红。protocol-focused-3 与 permission-focused 是聚焦检查，不替代最终整轮。

旧入口验证：两个脚本 × 四组参数，空目录与命令 sentinel，均 exit 2/零外部命令；[原始结果](smoke-retirement-verification.json)。共享 emitter 的 intake/large-stream 回归仍由现有 Maven 测试执行，不以其自测/ACCEPT 文本证明工具执行。

## 自查与六维限度

作者自查：本次离线切片 PASS；整批仍 IN_PROGRESS，原生身份 producer 未完成，不能给总体收口 PASS。此自查不冒充独立评审。

- Declared by：D02 精确关联、用户首版全量声明决定、CC LOW-1/2，W08 当前证据纪律。
- Proven by：当前协议/真实离线批准序列及脚本退休 sentinel 检查；原生接口 NOT RUN。
- Consumed by：projector→health→既有 typed signal；PermissionEvidence→sole resolver/publication。新用例不为 C/X 创建发送资格。
- Enforced before：结论在成功采纳/发布前被拒，脚本退休在任意副作用前退出；不能解释成 OS 写前保护。
- Failure route：permission_blocked 与模型 JSON 原因仍分立；未知身份保留未竟项，历史失败不擦除。
- Cannot-bypass：重复/重用 id、并发分项决定、局部声明与完整正向对照。没有宣称逐入口完整 native 桥接已证。

首版规则按用户明确决定保留，后续精细化须以真实反复阻塞和收益成本为依据。需要校正的原评审论断是“补全声明重批入口已可用”，不是借此重开局部声明设计。W03/native identity 具体 producer 与其既有消费者仍需接线；语义冻结不能代替缺失 producer。未覆盖 C/X、浏览器 runtime、目标 Git 交付、真实 provider、历史样本恢复。

## 增量身份和材料

[实际批前基线](before-source.json)覆盖 src/resources/POM、scripts/docs，637 个构建/测试输入（含 emitter）另有最终运行前 SHA。README、旧 smoke 根计划与 progress 的增量比较基准按本次记录的唯一文本改动逆向还原，见 before-additional-reconstructed.json；它们**不冒称开工前 SHA 实测**。原始退役脚本和教程为退役前直接复制。

[文件清单](changed-files.json)及 [增量](incremental.patch)相对上述基线；测试输入身份前后对照、模块计数、归档 XML 和 raw logs 均供复核。主工作清单 §14 是进度索引，本材料不是另一份工作清单。


运行过程中因 quiet 日志暂未返回，曾只读核对本次 Maven 子进程及其模块：确认仍在 acpx 模块执行，最后自然退出 0。线程 attach 未获得栈，日志保留，不作为任何边界证明；没有修改、终止 JVM 或调整测试断言/权限。进程查看限于验证诊断，未转入 OS/安全机制工作。

评审组织：本次 LOW 补证与规则处置纳入 B03 合并判断，不要求为该策略再开一轮设计评审；提示词随材料备用，不自动调用 CC 或开启独立 agent。原生实际身份具体接线完成前不交 B03 收口 PASS。
