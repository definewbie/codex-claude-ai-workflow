# B04 验证过程分类

最终凭证只有 test-results.json（稳定源码单轮 clean reactor）与 native-verification.json（受维护源码全量离线check）。以下保留过程，不能用早期绿灯替代最终凭证。

- native-red.log：初版4个红例，1个read夹具却标题edit（测试设计错，INVALID）；另外3个行使缺失exact选项/fallback/身份拒绝能力。native-focused-2、native-host-tests-2是后续切片绿，不作最终凭证。
- native-host-build.log / native-format.log：错误脚本名，INVALID；旧dist-test上的8绿不能证明当时新源码，随后实际build:test纠正。
- native-ipc-tests.log：沙箱EPERM，INVALID；允许本地模拟IPC后才取得真实socket证据。没有以降断言规避。
- native-sdk-peer早期EACCES：模拟agent argv未带node；host extract回调丢this：修正测试绑定；均不作能力红证。nativeCLI拒绝exit5原生语义，旧exit0期待修正，未放宽生产规则。
- native-check.log：1001/1005，4失败是新增可选字段未启用时仍投影false/undefined影响旧公共对象shape；修为仅显式启用才添加字段，既有断言原样保留。native-check-final.log 1005全绿；coverage 143是子集重跑，不加总。
- java-transport早期日志：本地Python换行/字段大小写、human查询时机、失败结果synthetic command的测试期待错误；不得全称生产先红。期间自查补真实L短BUSY的有界等待，不重跑prompt。java-boundaries-2是切片绿。
- java-business-first：测试引用不存在枚举，INVALID。java-business-2：批准已到独立验收，deny断言误要求所有outputRefs为空；实际允许诊断引用，改为无business sidecar且无产品变更/独立验收。不是允许失败产物被采纳。
- java-business-3 / java-cli-first：两个Maven任务误并用target，前者NoClassDefFound INVALID；后者JSON长整型节点类型比较错误。后续串行clean java-business-cli-final 3项全绿，不借并发结果。
- java-writes-recovery：初版fixture写不存在的BLOCKING_ADAPTER_FAILURE，INVALID。继而误认为blocking必产生NON_RECOVERABLE；实际resolver将阻断signal记为RECOVERABLE_ADAPTER_FAILURE，当前native reason映射本就正确。使用真实resolver输出后证实这一点；据误判加的重复分支撤回，仅保留真实产物消费回归，不称生产修复。最初recovery-counterproof移除重复分支仍绿，正是其不必要性的反证；最终反证移除真正的native reason映射，要求实际流程断言失败。

- interrupted-redundancy-reactor-final.log：最终核验中断后撤掉上述冗余分支，INVALID，不能作当前源码凭证。重新串行clean才作为最终凭证。
- java-live-busy-red.log：新增两项合法反例，6 项中 2 FAIL。C 新提交后 send 前本地锁忙，原实现丢掉唯一 fresh payload（本应一次发送却为零）；send 后观测记录锁忙被通用等待吞掉（本应 UNKNOWN 却未产生 typed 失败）。java-live-busy-stream-green.log 证明修正后 transport 6 项和 server controller 30 项均绿。修正只把 fresh payload 留在原存活调用内；IPC 开始后任何锁忙均 UNKNOWN，不重新提交、不重发、不恢复队列。此为真实生产先红后绿，与下述夹具错误分立。
- reactor-stream-cleanup-fail-test-results.json：完整单轮 1,699 tests / 204 suites，0 assertion failure、1 server cleanup error，输入无漂移，判 FAIL 而非全绿。原因是 SSE 测试裸调用 emitter.complete 没有 Servlet completion 回调，后台轮询线程继续建立 TempDir 下文件；测试 finally 改为关闭其实际 owned stream 并等待 executor 终止。不改 SSE 生产语义、不弱化断言。聚焦 server 30 项已通过，整批凭证仍须修后一次完整 clean。
- reactor-first.log：早期任务被中断，INVALID，无完成凭证。最终只消费 reactor-final.log/test-results.json。
- counterproof：隔离control/mutant副本；去掉fresh C区别使真实重复消费断言失败，EXPECTED_DUPLICATE_C_FAILURE；生产文件未改，source SHA仍一致。不是实时取消/真实provider反证。
- whitelist/format：普通Java/TS源无diff whitespace异常；维护的unified patch中空上下文行是单个空格，外层git diff --check将它报尾空格，不能剥掉补丁语法。干净上游apply-check和字节比对通过。

全部运行是本地Java/模拟ACP/临时Unixsocket、Node VM renderer；native/provider/真实E2E均为0，未启agent、未安装、未提交。源内容未稳定的轮次从不称当前实现单轮全绿。
