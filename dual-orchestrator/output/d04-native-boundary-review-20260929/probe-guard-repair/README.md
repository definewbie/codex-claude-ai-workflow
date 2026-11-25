# 后续诊断探针修正（未启动 native）

旧运行源文件/事件不改。write-guard.mjs 中的同一函数嵌入 prepared-future-probe.py 的 Code Mode payload；5 项故障注入检查验证：合法拒绝可继续、首个/后续写成功均零后继、未知结果/transport exception 均不继续。外层也补目标文件变化轮询停止。

仅准备可审查修正并完成离线测试，本轮不运行 future probe。若需要运行，仍须新 Gate/预算，不能把本次旧 Gate 全部机械化追认通过。prepared driver 创建全新目录，拒绝覆盖；脚本成功语法与 helper 行为不等于真实中断时延已验证。
