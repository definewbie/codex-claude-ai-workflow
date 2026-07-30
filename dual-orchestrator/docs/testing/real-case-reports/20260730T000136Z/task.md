# T6 中等复杂度 Real Case：补录后的最近已完成治疗定位

## 目标

在现有补录、治疗记录详情和报告上传流程基础上，收口一个真实业务缺口：

补录保存成功后，“最近一次已完成治疗”必须由后端在当前治疗方案内权威选择，
前端不得自行按日期、标题、备注或跨方案数据推断。用户确认后进入对应治疗记录
详情页，并继续使用该记录的 `cycleId` 打开报告上传流程。

## 当前已存在且必须保留

- 治疗页无记录时已有“先把过去的治疗补进来”引导；
- 补录页保存成功后已有下一步提示；
- 治疗记录详情已有“上传报告”入口；
- 详情和上传链路已经使用 `cycleId`；
- 既有 `event_id` 上传路径不能被破坏。

本任务不是重做这些页面，也不要求为了满足数量修改所有允许文件。

## 必须实现的行为

1. 后端提供 plan-scoped 的“最近一次已完成治疗记录”查询。
2. 只选择当前用户有权访问的当前治疗方案内、状态为 `completed` 的记录。
3. 排序必须确定且可测试：优先实际完成日期，其次计划日期、周期序号和稳定 ID；
   不得跨治疗方案选择。
4. 当前方案没有已完成记录时，返回明确的 typed HTTP 结果，前端显示“尚无已完成
   记录”的引导，不显示错误跳转。
5. 网络或非预期服务失败必须与“没有已完成记录”区分，前端显示失败提示。
6. 找到记录时，前端进入 `/pages/treatment-record/index` 并传递后端返回记录的
   `cycleId`。
7. 保存或跳转失败时不能向用户暗示报告已经关联成功。

## Workspace Change Boundary

只允许修改以下文件，实际修改必须是该集合的子集：

```text
backend/app/api/chemo.py
backend/tests/test_chemo.py
miniprogram/src/api/chemo.ts
miniprogram/src/api/request.ts
miniprogram/src/pages/record/index.vue
miniprogram/src/pages/retro/index.vue
miniprogram/src/pages/treatment-record/index.vue
```

禁止修改：

- backend enum、migration、model 或 schema；
- `TreatmentEvent.status`；
- 上述集合外的任何文件；
- 目标仓库提交历史。

## 业务安全边界

- 不做治疗建议、用药建议或能否治疗判断；
- 不自动修改治疗事件状态或重排治疗日程；
- 不使用 title、date、notes 或 doctor_notes 猜测业务关系；
- 不把结构化关联写进 notes/doctor_notes；
- 不引入规则引擎、聚合平台、第二套报告关联模型或通用状态机；
- 不新增“正常”“合格”“已通过”“可以治疗”“可以化疗”“安全”等暗示性结论。

## 验收

1. Backend full baseline 与新增测试全部通过：

```bash
/private/tmp/dual-t6-cancer-clinic-venv/bin/pytest -q
```

2. 小程序构建通过。可使用已固定的本机依赖：

```bash
PATH=/Users/kriswang/work/projects/ai/cancer-clinic/miniprogram/node_modules/.bin:$PATH npm run build:mp-weixin
```

3. 后端测试至少覆盖：
   - 当前方案多个 completed 记录的确定性排序；
   - 无 completed 记录；
   - 不跨方案；
   - 无权访问的方案；
   - 实际日期缺失时的 fallback。
4. 前端明确区分 found / none / unexpected error。
5. change-scope evaluation 为 PASS，Git diff 无无关重构。
6. 自测报告必须列出修改文件、选择规则、测试/build 命令和结果、超范围检查。
