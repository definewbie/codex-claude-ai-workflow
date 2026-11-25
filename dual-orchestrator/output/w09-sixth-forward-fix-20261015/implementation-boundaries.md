# 有界实施：第六例 F1/F2

目标：P0 修正合法返修的指令/诊断接线；P0.5 修正可读正文被误判为协议损坏。仅未来运行有效，不救第六例。无原生/provider 调用、无第七例、无提交。

## SoT / owner
- Definition：AgentResultEnvelope 的封闭 record 是字段语法唯一来源；提示投影从同 record 枚举。parser 的 typed diagnostic 仅诊断，不制造路由权威。
- Instance/Execution：沿原 R01 固定基准、R02 intent、唯一 outcome、R03 固定完成/L03 采纳；不改变批准、身份、scope 或新重试资格。
- 用量：wire 是协议语法 owner；reader 只消费当前调用完成或既有严格共享出口。原始正文不是协议，成功解析的协议形状若坏则不可用。固定 occurrence 才是预算消费者的事实，不回写历史。

## Transition / 失败切面
| 前态 / 触发 | owner / 顺序 | 后态 / 唯一消费 | 失败与重启 |
|---|---|---|---|
| 合法返修资格 | 既有 resolver 同次 capture → R01/L claim → dispatch 重核 → R02 | 作者获得既有 full/patch 契约 | 无 claim/身份漂移：零 dispatch；写前缀沿 D06 |
| 模型返回未知字段 | strict parser 拒绝并保留字段/合法字段集 | acquirer 仅一次 deny-all 修正 | 修正再错仍失败；不新增调用、不改模型正文 |
| 修正完成 | 原 parser/validator → 唯一 outcome → sidecar/merger（仅合格）→ R03/L03 | 原复评、人门、compile | 失败零发布；外部 UNKNOWN 沿 D07；冷读只读固定字节 |
| 当前 transcript 可读正文混合帧 | wire 区分非帧与已解析 RPC → reader 身份/重复/完整量核验 | 固定 occurrence → aggregate → operator 核预算 | 真实形状坏帧/错身份/部分量不借历史；无当前 result 才走原严格共享出口 |
| 冷读/历史追加 | 固定 R03 优先，调用日志 SHA 仍须一致 | 同一 read model /预算观测 | 不从新 reader 回填旧 run；历史 FAIL/UNAVAILABLE 不变 |

## 验证与停止
先红：full revision 字段枚举、未知字段诊断、正文与完成混合日志；再正反成对（两客户端、主/修正、共享回退、坏帧/错身份/重复/部分量、一次修正上限）。复用既有 R01/R03/冷 JVM/兄弟套件；不为诊断新增写协议。旧材料只读重放分类与归因，数字只属新分析，不替旧账本。observer 仅复制为未来候选同步修正同一误判，旧脚本不动。

不新增服务、schema、锁、补偿/兼容平台。若需要这些或新原生实验即停交用户；本轮只自查，独立 CC 另行评审。
