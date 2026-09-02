# E1 Sandbox Effect After Owner Fixture

该 fixture 从 R3 复制网络与文件系统矩阵，但删除旧 owner 契约中的 proxy value/userinfo 读取。proxy 是 Claude sandbox 的
transport projection，Dual 只记录变量名的 `present/absent`，不读取或保存值。

`e1-sandbox-contract-probe.test.sh` 同时验证普通 canary 与 URL-shaped canary：二者都必须完成同一矩阵、不得以 73 退出，
也不得把原值写入证据。exit 73 只保留给三个 Anthropic provider credential。

`E1SandboxProbeLauncher.java.txt` 是执行期 launcher 的冻结源码；它调用 production policy writer 和 transport，不拥有 network
判定。WebSearch 模式保留 production review profile 的工具与 kind 集合，只把本验证 occurrence 的 prompt retry 收紧为 0。
执行前必须从本 checkpoint 源码编译并核对 SHA。
