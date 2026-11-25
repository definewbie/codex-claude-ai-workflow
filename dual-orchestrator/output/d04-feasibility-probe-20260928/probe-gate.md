# D04 离线可行性探针 Gate

- Target: 在现有 acpx 宿主处理器中同时保留文件读取、拒绝文件写入和 terminal 请求；另验证 terminal capability=false 能否独立阻止请求。此探针不证明模型能到达读取接口。
- Source of truth: 未改动 AcpClient 实际处理器返回/错误、临时文件内容和进程标记；结果保存在 host-probe.json。
- Trigger precondition: 不创建 provider/session、不 connect、不调用模型；只实例化现有 AcpClient 并直接调用其处理器。所有文件/命令目标在新建临时目录。读写/执行模式明确指定，无用户配置或凭据读取。
- How precondition is checked before run: 已读取构造器、handler、FileSystemHandlers、TerminalManager；构造器仅装配本地处理器。探针断言非交互环境；命令固定为本机 Node 向临时目录写固定标记，不接收外部命令。
- PASS: approve-reads/nonInteractive=deny 下全量/分段读取准确，修改/创建/terminal 请求 PermissionDenied 且目标未变；能力标志的单独对照按实际行为记录，不把反例算 D04 通过。
- FAIL: 合法读取失败，拒绝模式下出现任何写入/terminal 标记；立即停止，不继续真实探测。
- INVALID: 源码身份变化、导入/环境错误、交互环境、证据缺失；不能说明产品行为通过。
- Stop-loss: 首个意外异常退出；总时限 30 秒；无升级权限、无安装、无真实 provider。确认需要 adapter 修改或原生模型工具验证时停止，不擅自进入实施。
- Run artifact freshness: 直接导入本地 reviewed acpx 源码，运行前后保存相关源码 SHA；不使用旧 jar 或复制实现。诊断程序只在 output 中，不是产品测试源码。

此探针直接调用真实 handler，未经过 ACP wire 或 provider，结论限定于宿主处理器。approve-all 对照仅作用于临时样例，不代表允许用于只读角色。
