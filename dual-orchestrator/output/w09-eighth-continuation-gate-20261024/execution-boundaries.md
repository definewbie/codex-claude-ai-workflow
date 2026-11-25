# Fresh 第八例 Gate：离线装配边界

当前用户只授权离线装配已受审进程核验修正，补记实际SDK程序身份，再交批准。**模型请求、原生会话创建、业务推进、真实运行均未授权。** 可以创建全新空Git fixture并通过冻结CLI的start（无--run）生成原生领域实例；不手写current/gate/run或恢复旧例。

本次推进P0启动前提可信性及P0.5原用量/预算核对的准入；产品功能、prompt、schema、状态机和发行不变。Definition为原terminal-delivery及已校准预算；Instance仅由原start生成；Execution为零run/session/occurrence；observer及进程guard是operator核对，不产生业务authority。

| 前态 / owner | 动作、durable顺序 | 后态 / 唯一消费者 | 失败、重放与外部边界 |
|---|---|---|---|
| 历史七例停止、修正及初始化观察有固定材料 | 先保存历史输入身份，核659产品输入/JAR及已选装配 | 可进行离线准备；尚无执行许可 | 漂移不可静默接纳；不改旧材料、stop-required或usage |
| 当前运行授权false / operator | 实际runner应在读取实例/创建step/启动Java前拒绝 | 只留下拒绝测试证据 | 冷启动同样拒绝；不能复用第七例true授权 |
| 进程表可读，已知旧PID缺席，具名旧session已关闭 | 准备入口消费同一受审guard；之后才创建空target、原start无--run、固定初始ledger | 新current/初始context READY，零批准/run；未来原runtime消费 | EPERM/UNKNOWN/PRESENT均零fixture创建；失败保留、不原地重建 |
| 新实例初始化完成 / 原ledger owner | 只读准入入口再次消费guard、核冻结初始账本/Git/装配/预算 | OFFLINE_READY_AWAITING_USER；预算ALLOW不授许可 | 缺路径/新文件/pending/漂移即NO-GO，不假造替代实例 |
| 将来用户明确批准本次运行 | 立即只读重核；每步runner仍先核授权/stop/冻结输入及旧执行guard，再走原Java入口 | 原R01/R02/R03/L03及C/X/D07/D09链 | 本包未执行；UNKNOWN不重发，人门只消费当前subject |
| 将来当前GOAL/EXECUTION/最终人门 | 用户分别决定，原owner固定谱系 | 合法实现/验收/交付资格 | 本Gate运行许可不代填业务批准或用户commit |

准备、只读重核及逐步runner共用一份process_check.py，不复制判据。原58个旧PID加第七例两个runner及诊断两个具名PID，共62个作为旧执行输入；18个旧会话含诊断session，以真实关闭metadata核验。PID复用保守返回PRESENT，不临场豁免。

SDK身份只补充上次观察的实际子进程path/SHA/package及证据来源，纳入本包字节重核。PATH中的个人claude不是它的替身；本包不强行切换SDK、不锁版本，也不把静态字节等同未来实际选中。后续真实R01/兼容性和原typed拒绝仍有约束力。

fixture放在本项目output下持久隔离目录，避免等待批准期间依赖/tmp寿命；Git target/worktree仍是独立空产品树，不复制Dual源码。位置变化显式记录，不改产品需求；父项目说明不能成为产品已批准scope或证据。

六维目标：Declared=用户离线授权；Proven=实际false拒绝、受审guard的正反/冷启动/消费者拒绝、真实空fixture与原start、只读重核、12 observer及21预算对照；Consumed=上述三个operator入口共享guard，业务仍归原runtime；Enforced before=目录/派发前的operator检查和原业务guard分别说明；Failure route=UNKNOWN/漂移/权限不足即停止；Cannot-bypass=未授权入口零step、guard拒绝零后续、SDK假身份拒绝。未来真实主流程仍NOT_RUN。

闭环按Analyze→Assemble→Review→Reflect→Analyze执行。只做现有脚本最小迁移，不新增守护服务、权限/认证平台、遥测框架、模型调用或调参实验。新旧包不互相兼容解释：旧停止包只读，本包授权case必须FRESH_EIGHTH；旧余额及授权无效。
