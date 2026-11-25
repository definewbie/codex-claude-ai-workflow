# 当前离线验证入口（2026-10-07）

旧 `scripts/smoke-test.sh` 与 `scripts/semantic-boundary-smoke.sh` 已显式退役，任意参数均退出 2，不构建、不创建工作区、不批准 gate、不调用 native/provider。旧教程与脚本原始字节保存在 `output/lifecycle-batch-03-20260930/mainline-20261007/`，仅作历史材料。

原因：旧 whole-lifecycle fixture 以模型正文模拟自测/ACCEPT，不能产生 D05 要求的程序观察；旧门顺序也不作为新阶段合同的证明。继续维护另一套成功材料会重新引入证据漂移。没有通过放宽生产校验来维持旧脚本绿。

当前入口（项目目录）：

```bash
rtk proxy mvn -q clean test
```

- `PermissionEvidenceSequenceTest`：真实离线批准、程序文件观察、四变体及 worker、固定完成冷 JVM 读取；声明通道严格匹配，部分声明保守停止。ACP 帧为合成协议。
- `PhasedContractContinuationTest`：GOAL/EXECUTION 分期、作者修订与独立复评、新批准前禁止实现、退出码要求弱化后的重批。
- `AcceptanceRepairSequenceTest` / `VerificationEvidenceSequenceTest`：离线返修、验证证据生产/消费与拒绝。
- `AgentRunCommitProtocolTest` / ledger 序列：固定写入、失败前缀与恢复。须按各测试实际范围解读，不能推成全部外部窗口已证。

此入口替代旧离线 smoke 推荐，不证明 native/provider 行为、浏览器操作、目标 Git apply/commit 或真实任务成功率。真实运行保持 NO-GO，须先完成对应清单、技术 Gate 与用户批准；`--skip-build` 或旧报告不能绕过。

`emit-strict-agent-result.py` 仍供离线 intake/large-stream 协议测试使用；其模型成功正文不是工具执行证据。历史 PASS 记录保留历史适用版本，不用于当前准入。
