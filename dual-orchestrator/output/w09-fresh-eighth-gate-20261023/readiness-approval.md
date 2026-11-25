# 第八例：验收环境声明待确认

## 当前实际进展

需求批准已写入原账本，GOAL epoch 1 已形成。设计经一轮语义返修后由另一客户端复评通过，计划也已独立复评通过。当前工作流为 WAITING_FOR_HUMAN，门为 `gate-node-gate-human_plan_approval-021`，程序路由原因是 `VERIFICATION_READINESS_REQUIRED`（计划尚缺 environment/feasibility 的有效引用）。这是预设的合法补齐入口，不是执行失败或最终验收通过。

累计 563,916 token / 8 次请求；活动执行时间 913.668 秒（人工等待不计入）。下一段若补计划引用再复评，仍须重新核验储备；本次算术为已用 563,916 + 剩余主链/计划重入/一次修正储备 775,000 = 1,338,916，低于 1,700,000；请求数 8 + 储备 6 = 14，低于 18。预算允许不产生业务授权。

## 请用户确认的具体事项

同意以当前隔离工作区及已固定的 Python 3.13 / unittest 标准库环境作为 AC-1/2/3 的验收执行前提；授权通过现有 `record-readiness` 入口登记三条独立声明（各条精确复制已审计划的 method），再经当前 `revise` 入口仅补齐计划中的声明引用、交原独立 reviewer 复评。

声明只说明 operator 认可这套环境和执行安排可供使用。它不证明原生客户端有实际工具执行能力，更不证明测试已经运行或成功。后续仍必须取得真实工具观察；不可用/缺证据按原规则停止，不改成模型自述或人工替代。

三份 JSON 在 `readiness-drafts/AC-1.json`、`AC-2.json`、`AC-3.json`，均为**待确认草稿**，未向 ledger 提交。登记前会将 statement 中“待用户确认”标识替换为这次真实用户确认的表述，并重新核验当前 subject、GOAL 与环境依赖字节。

## 保留的精确实现提案

- CREATE `src/normalize_text.py`：纯函数 CRLF/孤立 CR → LF，其余不变。
- CREATE `tests/test_normalize_text.py`：九类输入、11 个测试方法，包含幂等性。
- CREATE `README.md`：导入、准确混合换行例子及测试方法。

AC-1/2 需实际执行 unittest、保留 stdout/stderr，输出数量/零失败与源码对应。AC-3 需实际读取源码和 README 核对或运行示例。无需独立实测退出码的已批准要求不变。当前产品树仍为空，无产品写入授权。

## 下一步与边界

用户确认上述声明与补齐动作后：原 CLI 登记 → 原 revise 路由 → 作者补引用 → 独立计划复评 → 程序重验 readiness → 提交当前 EXECUTION 精确文件批准。**当前门不是最终文件写入批准**；本次环境确认不代替后续实施、产品验收或交付批准，亦不授权 commit。所有原 Gate 预算、期限、储备和停止规则保持。

## 证据与控制链

SoT 为原 `workflow/current.json`、当前 gate、GOAL 合同及固定 R03/run/occurrence；本文和 checkpoint 只投影，不产生批准。声明 owner 为原 VerificationReadiness/L04；后续 validateBindings、编译和 dispatch 唯一强制消费引用。缺声明不能取得合格 EXECUTION；实际证据不足不能成功采纳。正常补齐不走恢复旧例、不手改 ledger、不启动新 case。

当前节点、完整引用及 SHA、用量与储备输入/结果在 `readiness-gate-checkpoint.json`；当前账本历史快照在 `readiness-pause-ledger-sha256.json`。冻结源码/发行/配置再次核验通过，旧历史包 239 项 SHA 保持。本次新增 provider 调用为零；只是前面已完成八请求的只读复核与准备材料。
