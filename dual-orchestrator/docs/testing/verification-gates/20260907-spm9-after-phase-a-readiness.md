# SPM9：A 验证后的 E2E 恢复就绪检查

状态：FORMAL SELF-TEST PASS / ACCEPTANCE REJECT / STOPPED BEFORE MISROUTED CONTRACT COMPILE。用户授权进入下一步并评估 E2E 重启时点。
2026-09-07 正式续行至 evt71：063有效SELF_TEST_COMPLETE、065范围PASS；067拒绝的README
产品文档缺陷被填入contract_gaps，069路由至070。独立评审确认070输入缺批准proposal，
故在provider/compile之前主动停止，未执行必败节点。整体E2E未通过，不用局部测试绿替代。
宿主默认测试、live smoke与真实桌面/手机补证据见result；原始review和ledger未修改。
当前目标是判断产品任务是否具备继续条件，不开发 B、不创建第二样本、不修改产品或 ledger。

## Verification Gate

- Target：确认实际环境与产品测试状态，并据此决定 SPM9 的一次合法修复续作是否有新事实。
- Source of truth：current/gate/source sidecar/实际 workflow、批准合同与 manifest，宿主 curl 原始响应、
  原样 Maven 日志与 Surefire XML、前后 Git 产品文件摘要。模型自述不作为结果。
- Trigger precondition：SPM9 仍 evt-000053、WAITING_FOR_HUMAN、repair-053，仅允许 revise；
  Phase A 独立真实 slice PASS；检查期间无并行 provider、无代码或输入变更。
- How checked：直接读取持久化 current/gate/refs；冻结产品文件摘要；公开 Maven artifact 与 OKX curl；
  Java 25 下宿主原样 mvn test，不加 skip、不改依赖、不换测试、不修改 Maven 全局 settings。
- PASS：环境请求正常且原样测试通过、源文件不变，批准链与合法续作仍可冷读。
- FAIL：测试或编译暴露产品缺陷；记录具体错误，不通过直接修改产品来救样本。
- INVALID：网络/工具链不可用、账本状态改变、批准来源损坏、测试产生非预期源码改变；停止。
- Stop-loss：一次 Maven 执行；失败不改代码反复跑；没有新事实不执行 provider revise/retry。
- Run artifact freshness：保存实际 Java/Maven、产品文件摘要、JUnit XML、日志；不是模型自测证据，
  不能代替后续 typed self-test publication、acceptance、completion audit 与 candidate。

## 边界与下一决策

宿主 Maven 正常解析已批准依赖是本次环境准备，可能填充 ~/.m2；不冒称证明 Maven 在 Claude
沙箱内自动使用受控 proxy。A 的 curl 通网也不外推 Java HttpClient 通网。
本地 stub 测试与真实 OKX live smoke 分开；本轮先默认测试，不预先执行需要 opt-in 的应用 smoke。

SPM9 不是 terminal，合法 human revise 不需要迁移旧 ledger；若批准链完整且环境有新事实，
可在记录新运行身份后按原声明边继续，报告必须标为 fix-and-continue，不称无中断 fresh PASS。
若发现 ledger/authority 损坏，保留证据，不做 rescue；再评估 fresh，不能默认新增恢复机制。

Declared=用户批准的原合同与当前 gate；Proven=持久化链与实际测试；Consumed=既有 gate/实现/自测/验收；
Enforced=业务启动前检查与后续现有 validator；Failure=原等待态保留或走合法 repair；
Cannot-bypass=不能用宿主测试覆盖失败 sidecar、不能跳自测与验收，也不能用 A slice 代替产品完成。

一次新的无中断 fresh E2E 应等：当前阻塞已明确、已评审 harness 变更选择性收口/构建冻结、
源/effective workspace 前置与 provider health 通过。无需等待 B 或完整安全平台。
本文件不启动 provider；必要的续作 note/授权目标/新 identity 在上述检查后明确登记。

## 预检结果与最小续作（待独立复核）

2026-09-07：Maven artifact 200、OKX 200/code=0/一行九字段；Java 25.0.3/Maven 3.9.10。
原样 mvn test 已通过依赖解析与 17 个主源码编译，testCompile 报 7 errors，尚未运行测试：
Boot 旧 AutoConfigureMockMvc 包/符号两条；assertThatThrownBy(boolean)；overlappingStart 未定义；
HtmlPage.executeJs；await() 被同名方法遮蔽；WebConnection.close throws 签名不符。
42 个产品文件 SHA 前后不变。结果为 ENVIRONMENT READY / PRODUCT TEST-COMPILE FAIL，
不是 A 失效，也不是旧“纯环境性缺证据”结论仍成立。
证据：`../real-case-reports/20260907-spm9-after-phase-a-readiness/`。

计划只恢复当前非终态 SPM9 的一次已声明 repair，不新建 fresh，不重试旧 execution。
独立只读确认批准链及当前状态有效后，依用户既有代理决定授权：
1. 为后续 self-test 节点批准 repo.maven.apache.org、www.okx.com，one_execution；授权本身不推进 gate。
2. 当前 gate 明确 revise：只修四个测试文件中的编译问题，保持既有技术栈、合同、设计、计划、
   精确 scope 和测试断言强度；不得 skip/删测试、改依赖版本或业务实现，不新增网络/重试机制。
   现有依赖已能解析；不为消除编译错误更换测试框架或增加依赖。
3. 用既有 --max-steps 2 只执行 context.implement_claude 与 implement.claude_implement，
   返回后先检查实际 Git 变更和编译/测试证据，再决定自测推进，不能自动跑到终态。

这一步 Target=7 条测试编译错误消除且范围不扩大；Source=新 node/run/sidecar/manifest、Git 与测试输出；
触发=当前 gate 与批准链未漂移、已评审 A 的相同 JAR/fork、独立恢复复核通过；
PASS=有效 IMPLEMENTED 且具体编译错误消除；FAIL=范围扩大、同 blocker 无新事实、测试降标；
INVALID=运行身份/批准来源/存储或启动异常；失败即停，不自动 retry，不修改账本。
provider 有持续进展不按累计时长判失败；使用 DUAL_ACPX_COMMAND_TIMEOUT_SECONDS=0，既有无进展守卫保留。
claim 仍由指定 self-test execution 消费，不能把批准挂到 implementation 冒充后者已联网。
Java/构建工具若不使用沙箱受控 proxy，应报告环境限制，不以 curl 成功替它宣称通过。

新运行 JAR cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70；
fork 2313fe2d53e573dc0227a453fd1c4cbe6107373472a2027bb7ed3e2952038b63；
CODEX_HOME=/Users/kriswang/.codex；使用 reviewed .tools/acpx-e1/bin/acpx。
这是显式记录的 fix-and-continue，不继承原 fresh Gate 的无中断声明，也不抹掉旧失败。

独立复核 READY：当前 gate/链合法，四测试文件修复范围成立；两步上限到达时 CLI 既有语义
可能返回 exit 1，必须读取 current/node/run 区分正常暂停与失败，不因此 retry。
本轮仅此两步，后续 self-test/验收须先看本轮实际结果，不由本命令自动推进。

## 续作实际结果与停止决定

已执行一次 gate053 revise 与两步续作，run-1788781080688/node056 正常结束，
ARC valid、decision=BLOCKED。CLI exit 1 是 --max-steps=2 正常停止，不是 provider failure。
current=evt-000057/RUNNING，self-test057 READY，但未启动其 provider；操作层暂停不冒称新 human gate。
grant-6135806a-3c67-49c4-8554-d8d2039a56c9 仍 PENDING，未被 implementation 消费。

42 个前置产品文件复算，仅四个授权测试文件变化。七条编译错误消除，未改 pom/业务源码/断言强度。
UI 同一 js() 辅助方法另去掉错误括号包装，恢复真实脚本执行；独立评审认为技术上合理，
但超出“仅七条编译诊断”最窄措辞。本轮接受并明确登记此小幅范围偏差，不称零偏差。

普通沙箱 mvn -o test 为 43/1 failure/17 errors；已有 Byte Buddy 以 -javaagent 启动后，
完整套件仍为 43/1 failure/1 error/0 skipped。定向通过不覆盖完整失败，离线参数与 javaagent
变体不冒称默认 mvn test 全绿。剩余为 rateLimitIsCategorized 消息断言碰撞与调度测试整套超时。
Mockito attach socket 超时是观测事实，其具体 sandbox 归因不由错误正文单独证明。

独立结束评审：四文件 delta 与原始 Edit 反推前置 SHA 一致，无删除/弱化测试；停止在自测前。
Agent 清理了 target，Surefire XML 未保留；仅归档 run/stream/transcript 工具输出，不声称 XML 已归档。
详细结果见 ../real-case-reports/20260907-spm9-after-phase-a-readiness/README.zh-CN.md。

下一步先明确两处测试的窄修复范围和 Mockito 验证方式，再通过既有流程推进；
不为了把状态送回 gate 而盲跑重复自测、不手改 ledger。当前仍可继续 fix-and-continue E2E，
不需要等待 B。新的无中断 fresh 则待 harness 选择性收口、构建冻结与前置检查通过，当前不另建样本。

## 用户继续后的最小推进（2026-09-07）

用户再次授权进入下一步。两项剩余问题已读源码确认：429 测试的正文短语与固定类别消息碰撞；
调度异常测试在抛出 HTTP 之前的阶段等待 HTTP 日志增长，观察目标不成立。
宿主原样 `mvn -B -ntp test -Dtest=CandlestickSyncServiceTest` 为 11/0/0/0，未加 agent，
日志归档为 host-mockito-test.log；该对照仅证明宿主测试类可运行，不外推全部产品通过。

当前 self-test057 READY、无 open gate，review decide 不能直接应用第二个修复 note。
最小合法路径为：self-test 消费已有 implementation BLOCKED 及实际工具证据 → typed self-test
publication → decision → 原有修复 gate；不手写报告/账本、不新增跳转机制、不直接在外部改产品。

- Target：得到当前产品的有效自测结论并进入既有下一节点，使剩余两测试修复有合法入口。
- Source of truth：新 run/sidecar/self-test decision/current/gate 与 execution grant，实际工具输出。
- Precondition：evt57、self-test057 READY、implementation056 有效 BLOCKED；现有 grant PENDING；
  JAR cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70 未变，原 reviewed fork。
- Check：启动前读取上述文件/复算 JAR，宿主 Mockito 对照已通过；不改 provider/model/输入。
- PASS：自测如实包含剩余失败及环境条件，发布并经声明边进入下一状态；不是产品 PASS。
- FAIL：虚报全绿、擅自修业务/弱化测试、grant 越目标消费、同 blocker 无新证据继续循环。
- INVALID：身份不匹配、批准链损坏、启动或证据持久化错误；立即停止，不自动 retry。
- Stop-loss：run --max-steps 3，仅一次 self-test provider 及至多两个机械节点；无论结果都先检查，
  不自动进入下次实现或验收。运行进展不以耗时判失败，TIMEOUT_SECONDS=0，既有无进展守卫保留。
- Freshness：继承本修复续作的明确 JAR/fork 身份与新 occurrence；不冒称无中断 fresh 样本。

后续 gate 若成立，修复限定两测试（保留不泄漏/异常后继续调度的断言目的），不改主源码/依赖版本。
Mockito 如需长期默认可运行的配置，须先核对批准 pom/测试政策并在该 gate note 点名；
不得把 host 成功当成沙箱普通 mvn test 成功，也不为此增建 harness 权限层。

### 正式自测后的两文件修复

self-test057/run-1788783955100 已合法发布 BLOCKED（ARC valid），decision058 经声明边打开
repair059（evt59/WAITING_FOR_HUMAN、唯一 action=revise）。没有人工写 sidecar 或跳过决策。
grant 已 CLAIMED 到 self-test057，不能复用到下一 execution。全量运行一次42/43、一次41/43，
均保留客户端确定性失败；后一轮 XML 已由 operator 另存，不能把偶发调度通过当作闭合。

下一 provider 仍使用同一 JAR/fork，仅一次 revise --run --max-steps 2：context + implementation。
Target：只修 OkxMarketDataClientTest 与 SyncSchedulerTest 的已确认观测缺陷，完整测试全绿且
不改主源码/pom/依赖/测试数量与目的；原四文件限制仅对此轮两文件明确替换，不改 TaskContract。
生产前提为当前 gate059 open、source sidecar BLOCKED、两文件在已批准合同与计划内（packet 实读）。
PASS：两文件 diff + 完整动态证据无失败/跳过；FAIL：测试降标、超范围、失败仍在；INVALID：
启动/身份/存储错误。每次观察实际结果，不从模型正文假定成功；不自动 retry/验收/commit。

Mockito 只使用已存在的 agent 运行参数做沙箱验证，不改 pom；operator 随后执行宿主原样
mvn test 与显式 mvn verify -Plive-smoke，保留日志/XML与相同产品摘要。宿主与沙箱结论分开。
出现新范围或 live 环境失败即记录停止，不新建代理/配置平台，不重新开启 B。

执行结果：run-1788785003648/node062 的有效 IMPLEMENTED 已被路由至 self-test063 READY（evt63）。
两文件 diff 与独立逆向 Edit 基线一致，无断言弱化；独立评审 PASS。沙箱两轮43/0/0/0。
operator 随后宿主原样 mvn test 为43/0/0/0，mvn verify -Plive-smoke 为 BUILD SUCCESS、live1/0/0/0；
原始日志与7+1 XML已归档，产品摘要除授权两文件外无变。本轮修复及验证 PASS。
尚未运行 self-test063 或最终验收，不称产品 E2E 终态 PASS。无需等待 B，可以继续既有正式验证链。

## 正式自测与验收续行授权

用户明确要求继续正式测试与验收；Claude quota 耗尽时暂停通知用户切换底层模型，
不将额度问题当产品缺陷，不自动反复 retry，不手改失败状态；切换后依据实际账本选择动作。

- Target：当前修复后的产品经正式 self-test、scope validation、acceptance 与 human gate，
  最终交付止于 accepted candidate，不 apply/commit 到目标仓库。
- Source：既有新 run/sidecar/current/gate、Git scope、测试输出和 completion audit/candidate。
- Precondition/check：evt63/self-test063 READY、062 有效 IMPLEMENTED、独立修复评审 PASS；
  JAR cbbe03d801f7541fcc29aaa46a3a77fa6d830fb0f0fa67184fea8ce4c8294f70 已复算；
  宿主43/0/0/0与live1/0/0/0原始报告仍在，产品摘要未漂移。既有grant已消费，不静默复用。
- PASS：typed 自测和验收充分、范围有效、后续必要 gate 依据证据裁定；单个节点通过不算整体PASS。
- FAIL：真实质量/范围失败或虚报证据，先检查，不自动修复扩大范围；INVALID：身份/启动/存储异常。
- Stop-loss：逐节点推进，provider阶段每次max-steps=1；额度耗尽立刻暂停并通知用户，
  不自动切模型、不反复重试、不因仍有进展的耗时终止；禁止autorun与新样本。
- Freshness：继承已冻结JAR/fork，使用新的occurrence；本样本仍为fix-and-continue。

若验收不充分，只按既有合法路由处理，不以宿主记录覆盖失败sidecar，也不冒称沙箱直连已证明。
