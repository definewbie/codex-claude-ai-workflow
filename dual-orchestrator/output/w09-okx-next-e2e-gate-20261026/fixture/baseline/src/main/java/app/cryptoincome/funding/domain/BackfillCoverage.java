package app.cryptoincome.funding.domain;

/**
 * 回补区间 [from, to) 的覆盖证明:
 * COMPLETE 仅表示请求区间已由返回证据覆盖, 不代表 OKX 全部历史完整。
 */
public enum BackfillCoverage {
    IN_PROGRESS,
    COMPLETE,
    BUDGET_EXHAUSTED,
    SOURCE_EXHAUSTED
}
