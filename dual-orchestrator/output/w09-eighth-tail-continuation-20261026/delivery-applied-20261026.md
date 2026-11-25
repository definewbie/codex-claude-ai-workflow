# 第八例：最终批准已消费，交付 APPLIED，待用户 commit

用户最终批准已经通过原review approve入口记录。原工作流现为COMPLETED；交付owner仍为APPLIED（未COMMITTED）。因此完整E2E尚不能宣告PASS。

原delivery merge已真实执行Git应用，程序waitFor记录actual_exit_code=0、termination_observed=true。三个文件已暂存到既有隔离target：README.md、src/normalize_text.py、tests/test_normalize_text.py，共173行新增。

候选SHA256：`5b4ba7c4993dcda7ccd7190c47edb54f74fb0b8046deb3cf1e04bc78b9ed59bb`。目标HEAD仍为base `c442fd02fb39d87481bbd940aa5f270947a72a60`；暂存树为预期 `0233afaf8755e44b6c8ab156e311ffbef6e50983`；无未暂存差异，三文件字节与已验收源码SHA一致。

累计：2,077,707 / 3,000,000 token；13 / 18请求；2054.339062 / 3,600活动秒。本次无模型调用。历史三个stop、原包、659源输入、2个JAR均保持。批准后current/gate/merge-plan/events已另存delivery-applied-evidence，未改旧快照。

## 用户下一步

以下命令只commit当前已暂存的三个文件，不需要git add：

```bash
rtk proxy /opt/homebrew/Cellar/git/2.47.0/bin/git -C /Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/target commit -m 'feat: add newline normalization'
```

目标目录：`/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator/output/w09-fixtures/eighth-20261023/target`。请完成后提供commit SHA；agent再调用原delivery finalize核验精确commit并记录COMMITTED，不凭命令成功或工作流COMPLETED代替交付事实。

## 过程边界

一次本地检查把gate状态误预期为approved，实际schema是resolved并在result.human_action存approve；该检查在交付命令之前退出、未创建apply目录。已按原schema核对并单列OPERATOR_CHECK_ERROR，不当成产品失败/先红证据，耗时保守计入累计。

当前未commit、未finalize、未扩大最终commit授权。精确来源、原Git退出观察、证据SHA见同名JSON。
