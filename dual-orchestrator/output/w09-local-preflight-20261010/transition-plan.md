# G05 本地准备：执行前边界

只建立新鲜目标和本地实例，不执行模型/原生客户端，不编写产品代码。

| 原状态/事实 owner | 动作与写序 | 下游消费 | 失败处置 |
|---|---|---|---|
| 父目录不存在；文件系统 | 原子创建父目录→target→Git init→空基准提交 | GitWorkspaceService.inspect | 任一步失败保留现场；不覆盖/删除/重复 init |
| target clean/base HEAD；Git | 冻结 CLI start（不带 --run）→worktree→原 ledger initialize→操作员索引 | 原 readSession/status；后续合法 run 尚未授权 | 中断先核 worktree/账本/pending，不重发 start、不手补账本 |
| 原 definition/instance/current/runtime identity | status/trace 只读；metadata 与宿主条件核验 | Gate 运行前核对，不产生执行批准/readiness | 身份/配置异常归前置 INVALID；零 provider |

局部 registry 使用既有 DUAL_OPERATOR_REGISTRY 指向本次临时目录；它只是入口索引，不改变 authority。初始空提交禁用 hook/签名外部动作，仅为新 target 建立 base；不修改全局 Git 配置。

重启/失败时保留事实，先读后判断；不将本地 start 或现有 metadata 当作真实客户端能力。初始化完整性正常只证明本次前缀，不外推所有写入故障。
