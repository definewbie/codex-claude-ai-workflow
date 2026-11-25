# B06 实施记录（IN_PROGRESS）

用户明确授权冻结 W06 工作包实施及离线验证。P0：批准成果→目标应用→人工实际 commit→确认；中断后固定本地字节、人工裁定或历史核验，不重发未知 Git。source of truth 为原最后 acceptance/candidate 与 DeliveryMergePlan.operation+原 L07 events；view 只消费。完整 transition 与每窗及七项验证沿 ../wiring-candidate/protocol.md §2–§8 和 ../review-acceptance-20261009/disposition.md，不另造协议。

顺序：先补正反回归/保存 red→Git有限 lifecycle+严格目标观察→service intent/process/结束/裁定/幂等→CLI/API和query同代view→两UI和文档→整链/前缀/冷JVM/共享目标反证→稳定单轮回归→作者自查与CC批末。

范围见 approved-scope.json，含原24条件路径、具名CLI CREATE和三个既有兄弟测试；与已批准W06含义一致，未扩领域/权限。若既有harness不能有界承载新测试，只能先记录具名文件与用途、复核范围后编辑。没有额外agent、installer/native/provider、生产目标Git或提交授权。

遇到新authority/新服务/全进程树控制/无法有界条目身份或明显成本偏差，停止交证据。单切片绿不收口，旧B05 1731与新凭证分开。Analyze/Modify/Review/Reflect记录随证据追加。
