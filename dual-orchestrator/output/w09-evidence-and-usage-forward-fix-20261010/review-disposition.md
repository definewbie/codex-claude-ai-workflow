# 第二次真实 Gate CC 裁决接收与前向修复

用户带回独立评审：FAIL分类成立；F03 validator语义成立、修提示词；F04按completion请求身份归因、无关联不借latest。本文件接收评审，不冒充另一次独立审查。

本批沿原owner/既定修复范围推进P0/P0.5，不改变GOAL/EXECUTION/最终批准、scope、权限、证据或交付标准；无版本pin、native patch、额外agent或真实调用。第三例仍待本批CC受影响复审及用户新授权。

F03：共享AgentResultPromptContract覆盖intake初始/patch/reviewer，并由原acquirer送入deny-all correction；删除作者模板的“additional durable references remain allowed”歧义。RepositoryFactBinding与产品路径仍分立，validator未改。

F04：AcpxPromptCompletion增加实际ACP sessionId，由原current-stream projector生产，原3参数构造只表达缺该观测，不造身份。原reader只消费acpx record对应stream+completion ACP session/request；没有文本匹配或latest生产回退。重复同session/id无法唯一归属→UNAVAILABLE，不发明世代/追踪平台。异ACP session合法重用id可独立归属。同session/id重复的精细消歧不扩成当前任务。

金额仍是接口成本观察，既有costDelta算法不升级为实际订阅账单；snapshot不加总。关联缺失/歧义/缺usage有具名状态，聚合要求每occurrence均为同provider/model/billing的非累计请求量，否则UNAVAILABLE，各次原值仍保存在acquisition。未重写前两次失败账本或错误metrics。

验证过程：red两项是真实旧实现断言反证；focused-01包含旧fixture断言错误（手工transport显式供应EXACT_PROMPT_MATCH，不能要求其变为reader的新归因），且运行期继续加测试，不能代表最终源码；focused-02编译失败为作者漏改全限定Path签名，非真实Gate故障；均留原日志。focused-03及最终clean须各自独立归类，不拼接旧绿色。
