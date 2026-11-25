# 第三例 GOAL 批准请求

当前人门：`gate-node-gate-human_requirement_review-011`；允许 approve 或 revise。Codex 原独立复评已给 READY，作者返修按 CO-1 合并，当前提案 SHA `320e15b65a9c523bd0b2f33ba01b5b296e3f4c23b04e82f74d128b161867fefe`。本页是批准材料摘要，事实见原 sidecar 与 waiting-review.stdout.txt；不产生 authority。

请确认目标与准则：

- normalize_newlines(text: str) -> str 将 CRLF 与单独 CR 转 LF，其余内容及末尾有无换行保持；仅标准库。
- 原 AC-1 至 AC-3：至少八类输入与幂等断言；实际测试输入/输出、覆盖数、零 failure/error；README 导入与混合换行示例实际核对。没有新增统一真实退出码门槛。
- AC-4 明确同一函数行为；AC-5 明确实现不得越出后续获批的 EXECUTION 精确路径/操作。它们没有扩展产品目标。
- 禁止第三方依赖、网络/数据库/浏览器/后台服务、机器配置修改；不增加CLI/发布/CI或其他文本处理。
- 三个文件仍只是候选；本次 GOAL 批准不锁实施文件、不授写权限。设计和计划完成后，精确路径与 CREATE/MODIFY 再交你批准；最终candidate及产品commit责任不变。

预算实测：4调用/104635实际token，余14调用及55365 token至原止损线；约356.79秒分段runner主动窗口。已用一个正常返修循环，尚不保证预算可覆盖完整余链；未申请/增加预算，达到原条件立即停止。模型进程duration另列latest-progress，不把runner总时长冒作单run时长。

下一步：用户 approve 后通过原 review 命令登记本门决定，再进行只读设计与独立复评；若 revise，按你的具体意见走原返修。尚未代填任何批准。
