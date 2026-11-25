# 第八例续作停止记录（2026-10-26）

结论：Gate FAIL（STOP_LOSS_TOKEN_LIMIT_EXCEEDED）；领域保持 RUNNING，实现 SUCCESS，自测 READY。

本轮已通过原 Java 入口记录用户对当前三个 CREATE 文件的 EXECUTION 批准，完成实现上下文与真实实现。实现进程 exit=0，唯一 outcome=SUCCESS/completed，无格式修正。三个产物已经固定，但自测节点、独立验收和交付均未运行，不据此声明产品验收通过。

## 累计账目（不重置）

| 项目 | 已用 | 上限 |
|---|---:|---:|
| token（含 cachedRead） | 1,767,028 | 1,700,000 |
| 请求 | 11 | 18 |
| 活动秒（含收尾） | 1798.449159 | 3,600 |

本次实现请求 918,786 = input 93,888 + output 37,634 + cachedRead 787,264 + cachedWrite 0，EXACT_REQUEST_MATCH。超原 token 线 67,028，因此零下一派发。派发前已用 848,242，尾链与修正储备 600,000；该规则是完成粒度下的储备准入，不承诺拦截单请求内部实际 token 总量。

事实：实现阶段原规划额度 200,000；本次实测 918,786，缓存读取占主要部分。未证：内部各轮为何产生这些缓存读取，不凭汇总推导具体 SDK/模型根因。

## 计费与身份边界

本次固定 usage.providerId 为空，model 原值为 `in`，billingMode 为空；providerModelIdentity 是 unknown/low/pricing_unknown。真实 token 和逐请求归因仍完整。SDK 金额 USD 1.803922 单独保留，不能据此声称订阅实际支付额，也不能借上一次 provider/model 填补本次记录。该异常留待只读定位，不回写历史事实。

## 停止与保全

新 stop-required.json 保持原字节，两个历史 stop 也保持 SHA。具名 Claude 会话经原通道关闭并 show 确认 closed；成功读取当前进程表后，本次已记录的 Java、acpx prompt 和会话主 PID 均不在场。该观察不等于任意后代全停保证。

原包、前续作包、659 源输入、2 个冻结 JAR、原历史运行文件核对不变。新停止账本另存 stopped-ledger-snapshot-20261026，逐文件 SHA 核对；三个产品文件同时留快照。不改领域状态，不提交、不清理工作区。

## 下一步最小动作

先对本次实现成本和 provider/model 元数据做有界离线只读核对，给出剩余自测/独立验收所需预算及其测量边界，再由用户决定是否增额续作。现不自动提高上限、不启动任何模型调用、不重试、不新开 case；最终交付批准和 commit 仍留给用户。

精确账目、来源 SHA 和证据链接见同名 JSON；冻结清单见 stopped-ledger-manifest-20261026.json。
