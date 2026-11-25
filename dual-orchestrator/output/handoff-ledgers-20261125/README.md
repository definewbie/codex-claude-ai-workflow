# 五份历史 workstream 原始归档

每份压缩包对应请求中的一个原始目录。原样包含隐藏文件、空目录和失败 run 的现存输出。归档后逐项与原目录复核了相对路径、文件类型、权限位及全部文件的 SHA-256。运行日志和原始输出保留当时的内容。

| 样本与下载 | 解包后的账本根，相对于解包目录 | 全包文件 | 事件 | run |
|---|---|---:|---:|---:|
| [GCTW](ws-20260911T231749Z-GCTW.tar.gz) | `ws-20260911T231749Z-GCTW/.agent/workstreams/ws-20260911T231749Z-GCTW/` | 546 | 120 | 13 |
| [KPPZ](ws-20260912T081920Z-KPPZ.tar.gz) | `ws-20260912T081920Z-KPPZ/.agent/workstreams/ws-20260912T081920Z-KPPZ/` | 241 | 49 | 5 |
| [6NX5](ws-20260913T004742Z-6NX5.tar.gz) | `ws-20260913T004742Z-6NX5/.agent/workstreams/ws-20260913T004742Z-6NX5/` | 208 | 44 | 4 |
| [7SNA](ws-20261026T142203Z-7SNA.tar.gz) | `ws-20261026T142203Z-7SNA/` | 512 | 87 | 9 |
| [9YYF](ws-20261023T005431Z-9YYF.tar.gz) | `ws-20261023T005431Z-9YYF/` | 768 | 149 | 13 |

每个账本根下都有 `events.jsonl`、`workflow/`、`nodes/`、`gates/` 和 `runs/`。44 个 run 各自保留 `run.json`、`prompts/` 和 `outputs/` 中的原始文件。前三份是完整工作区，其中的 `.git` 是原机 worktree 的指针文件。后两份从原机的 `.agent/workstreams/ws-…/` 开始归档。

## 目标仓库提交

来源为各样本账本根下的 `workspaces/<ID>.json` 第 12 行 `baseCommit`。每个基线 SHA 均已在其记录的目标 Git 仓库验证为提交对象。

| 样本 | 记录的目标仓库 | 基线提交 | 工作区原始记录 |
|---|---|---|---|
| GCTW | okx-btc-k-line-demo | `c84807946493a603c789ee40c6a4c615aa846c3b` | `workspaces/wsp-20260911T231749Z-SAN5.json` |
| KPPZ | okx-btc-k-line-demo | `c84807946493a603c789ee40c6a4c615aa846c3b` | `workspaces/wsp-20260912T081920Z-H7FM.json` |
| 6NX5 | okx-btc-k-line-demo | `c84807946493a603c789ee40c6a4c615aa846c3b` | `workspaces/wsp-20260913T004742Z-95Y9.json` |
| 7SNA | W09 OKX 案例的 fixture/case/target | `7c5facc37538b717b061bbc53da7f0c2a5bbffa0` | `workspaces/wsp-20261026T142203Z-AVDV.json` |
| 9YYF | eighth-20261023/target 换行处理案例 | `c442fd02fb39d87481bbd940aa5f270947a72a60` | `workspaces/wsp-20261023T005431Z-29TT.json` |

9YYF 另有交付提交 `f8ec3d41cbe1cac554a78c3131372af434440691`，见该样本 `events.jsonl` 第 149 行的 `delivery_committed` 及 `delivery/merge-plan.json`。

## 下载后校验

在此目录执行。第一步校验压缩包，最后一步逐文件核对解包内容。需要目标源码时，请在对应目标仓库检出上表中的基线提交。

```sh
shasum -a 256 -c SHA256SUMS
mkdir -p extracted
for archive in ws-*.tar.gz; do tar -xzf "$archive" -C extracted; done
(cd extracted && shasum -a 256 -c ../FILES.sha256)
```
