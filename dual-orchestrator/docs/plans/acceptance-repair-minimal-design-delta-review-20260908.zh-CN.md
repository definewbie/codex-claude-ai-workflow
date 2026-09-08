# 验收返工最小设计：P1 Delta独立评审记录

日期：2026-09-08。对象：`acceptance-repair-minimal-design-20260907.zh-CN.md`的§4.1、§5.1、
生产面回填和测试13/14。方式：复用前轮独立评审agent Boyle，只读核对实际文本及源码；
本文件由主线程记录其最终结论。未运行测试、provider或real-case，未修改production。

## 结论

Boundary Design：READY。Implementation Entry：READY。本次限定范围无剩余P1设计阻塞。
Implementation Completion：未验证；不能据此声明实现完成、ENFORCED或PROVEN。

## 阻塞关闭

| 原问题 | 复核结论 |
|---|---|
| patch父B与批准A混用 | 直接父随修订变化，CREATE授权仍为A；未批准addition无豁免，最终合同批准才切epoch |
| 根验收/中间义务丢失 | 合法祖先链按来源累计，publication/compile exact-cover消费；READY或模型响应不能删除 |
| clarify重读被覆盖resolution | gate/node/event→当次compile→inputRefs→唯一requirement批准；禁止stable fallback |
| 消费者可见性缺口 | product-review context、两道packet调用同一resolver，分别绑定当前candidate/subject/compile |
| 部分状态泛称recovery | 明确实际拒绝、正常recovery、current合法可继续及不支持续行切片，不承诺自动修复 |

源码依据：RoutingEngine保留后继inputRefs、gate subject/source与routed事件；
人工决定保留原node result并以gate inputRefs作为完成输出。既有持久事实足以支持
限定的历史批准重建，不需要新ledger schema、binding文档或通用恢复平台。

## 六维检查

- Declared by：§4.1的双基线、来源、候选绑定及合法转换。
- Proven by：现有occurrence、refs/SHA、批准及路由事实，不是稳定projection。
- Consumed by：author、product-review context、两道packet、publication与compile。
- Enforced before：设计要求在发布、编译及后合同使用前校验；展示不授予权限。
- Failure route：断链、双匹配、篡改拒绝；非原子中断按§5.1停止，不虚构recovery。
- Cannot-bypass test：13覆盖多轮修订/覆盖后重读/消费者突变，14覆盖真实冷入口；均待实施执行。

## 实施约束

不再为READY扩大设计。实施若发现既有持久事实不足，或半状态能绕过批准进入provider，
按停止条件重判，不能用兼容补丁掩盖。仅不支持透明续行不等于需要本批开发R1平台。
F3误报维持撤回，只补现有守卫的保持性测试。按批次A→B实施，SPM9继续保留证据、不恢复。

本轮主线程校验：git diff --check通过；文档修订不构成新增测试通过声明。
