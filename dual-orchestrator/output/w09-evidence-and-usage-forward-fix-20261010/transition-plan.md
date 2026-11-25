# W09 F03/F04 前向修复边界（2026-10-10）

目的：P0 的合法空仓 intake 与 P0.5 的每请求用量归因。无新 authority、无原生补丁、零真实调用；不恢复前两例。

| 前态 / SoT | owner 与动作 | 写入 / 消费次序 | 失败切面与下一出口 |
|---|---|---|---|
| requirement + repo-map/RepositoryFactBinding | renderer 投影 product evidence_refs 语义；共享 envelope 契约同样约束 correction | 现有 prompt manifest/R01；模型仅候选 | prompt/manifest 写失败零 dispatch；不造产品文件填证据 |
| 当前 stream baseline 后的 request/result | projector 固定实际 completion 的 ACP session + request id | AcpxRunResult → handler → reader；不依赖 prompt 文本 | 无 completion 身份/重复无法关联→不可归因；不借 latest |
| completion 身份 + acpx record 对应 stream | UsageReader 唯一读取同请求 result.usage | 每 occurrence 固定→只完整可加总时聚合→run/R03/L03 | 缺 usage 等待原有有界窗口；snapshot 非请求总量；写失败沿 D06/D07 |
| intake 候选 + current repo binding | 原 validator 严格 product path 校验 | typed outcome→固定 run→发布 guard→后继 | 空 inventory 可空 refs；.agent/.git 拒绝；非空必须有 tracked fact；校验失败零 sidecar/后继 |
| 固定失败/旧记录 | 原恢复/metrics/read model 消费 | 不从投影制造 authority 或执行许可 | UNKNOWN 零自动重发；旧 binary/artifact 不当新测试证据；第三例须 fresh 冻结 Gate 和新授权 |

兄弟：主调用/correction、Claude/Codex、同 session 多 prompt、重连不同 ACP session/重复 RPC id、延迟/缺用量、快照/金额/metadata、初始/修订 proposal/reviewer/格式修正、非空 tracked inventory。

边界判据：Declared=CC裁决+既定前向修复范围；Proven=本批待执行离线回归；Consumed=原唯一 reader/validator/handler；Enforced before=采纳/发布与预算决定前，不称 OS 写前；Failure route=原 D02/D06/D07；Cannot-bypass=合法正向+非法拒绝+删除式反证。成本超出这些 owner 时停止，不新增归因服务。
