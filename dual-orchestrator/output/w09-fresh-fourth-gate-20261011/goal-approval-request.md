# 第四例：当前需求批准请求

状态 WAITING_FOR_HUMAN；当前门 gate-node-gate-human_requirement_review-011，workstream ws-20261011T044656Z-BMDJ。

目标：标准库Python函数normalize_newlines(text: str) -> str，仅把CRLF和单独CR转为LF，保留其他字符、空串、已有LF、末尾有无换行；不做文件批处理或CLI产品。

验收：至少八类输入（空串、LF、CRLF、CR、混合、Unicode、末尾有/无换行、连续空行）的明确期望与幂等；真实运行unittest、案例输入/实际结果/期望对应、覆盖数与零failure/error；README导入与混合换行例子经实际读取/执行核对。工具无须独立测量真实退出码，实际输入输出及测试证据仍必须取得。

禁止：第三方依赖、网络、数据库、浏览器、后台服务、机器配置或程序控制目录修改。

建议src/normalize_text.py、tests/test_normalize_text.py、README.md仍仅候选；设计/计划形成具体路径及CREATE/MODIFY，首次实现前单独批准EXECUTION。此次批准不授产品写入或最终交付。

作者初稿→Codex指出证据措辞/候选路径边界→作者修订→CodexREADY_FOR_PRODUCT_REVIEW，当前零must_fix。完整材料：[提案](goal-candidate.json)、[独立复评](goal-independent-review.json)、[门及材料身份](goal-approval-subject.json)、[当前read model](waiting-review.stdout.txt)。

累计4次真实请求103724token，主动275.131s；剩余额度1596276token/14次，继续仍每段重核尾链/修正储备。无产品变更，完整主流程尚未证明。

请批准或提出修改。收到当前需求批准后：按原CLI记录该门决定→原编译GOAL→只读设计/独立复评/计划；业务门与精确执行范围不借预算授权跳过。
