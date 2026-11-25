# 普通文件范围偏离的执行处置

状态：部分修正；不代表“人工批准保留新增文件”已完成。后续完整目标见
`../../plans/20260909-human-reviewed-scope-additions-minimal-design.zh-CN.md`。
本页仅修正后续 Gate/operator 处置，不变更 production 权限或合同。

## 目标与边界

隔离工作区中出现普通清单外源文件，不再仅凭路径差异取消正在进行的 implementation。
保留精确 TaskContract.scope，由既有 Git scope 校验产生 typed evidence，并进入人工修复门。
本规则不适用于保护区写入、工作区外未经授权写入、破坏用户数据、只读 profile 成功写入/执行、
authority 漂移等独立违规；这些情况仍按对应安全边界立即停止。

## 执行与决定

1. 发现普通范围外文件时记录实际路径与工具结果，不删除文件、不自动扩大 scope，也不仅因此 cancel。
2. implementation 正常结束后，由既有 artifact validation 比较实际 Git 变更与 compiled TaskContract。
   若期间独立发生运行故障，按其真实分类处理，不伪造 scope 校验结果。
3. CHANGE_SCOPE_VIOLATION 必须沿声明边进入 human implementation repair gate；不得当作 PASS、
   不得在未解决差异时推进验收/交付。缺少声明边或证据不可用仍 fail-closed。
4. 人工依据代码用途、职责和 immutable scope evidence 作决定。当前 repair gate 仅支持带 note 的 revise，
   其含义是修回原授权范围，而不是批准新增文件。不得把 approve 或自然语言 note 当作 scope grant。
5. 若用户决定保留额外文件，暂停在授权决定处，另走合法合同范围变更及其批准流程；本次不新增此入口，
   不假定现有 repair gate 具备该能力。禁止自动接受“模型认为合理”的解释。
6. 新 iteration 修复后重新执行 Git scope 校验；同一差异无新证据连续两轮仍按既有止损处理。

## 验证口径

- 普通额外文件本身不判完整 E2E FAIL；真实校验、人工修复及再次校验属于允许的自然路径。
- 未经解决便继续交付、绕过人工 note、证据漂移或重复无进展触发止损才判相应 FAIL。
- 仅在自然样本实际经过此路径后才能声明该 real-case repair 路径 PROVEN；离线测试不替代它。
- 不改变已冻结样本结论：FZ8K 仍为先前 Gate 下的 OPERATOR BOUNDARY STOP，取消后的
  AGENT_RESULT_MISSING 是派生结果。本规则不自动授权其 retry，也不追改该样本为 PASS。

## 闭环与自查

Declared by compiled TaskContract.scope 与 workflow 声明边；Proven by Git occurrence evaluation ref/SHA；
Consumed by artifact validation 与 human repair gate；Enforced before 正常下游验收/交付；
Failure route 为 typed scope violation -> human revise -> implementation -> 再校验；
Cannot-bypass 复用 scope producer lineage/SHA 拒绝测试、缺声明边验证和 note 必填序列测试。

本轮只修改操作政策和 active plan，不修改 schema、workflow、adapter、模型层或 sandbox，
不启动 provider、不增加 agent、不恢复样本。准确能力声明：复用已有后置 enforcement，
不声明逐文件写前隔离，也不声明新增文件批准能力。
