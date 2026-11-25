package app.cryptoincome.funding.domain;

/**
 * 一次追加入库的三种统计口径, 不得混用:
 * newGlobalFacts —— 真正新增到 funding_rate_fact 的全局事实数;
 * newObservationLinks —— 本次新增的 run→fact 观察关系数;
 * duplicateObservationLinks —— 当前 run 内重复的观察关系数。
 */
public record AppendResult(
        int newGlobalFacts,
        int newObservationLinks,
        int duplicateObservationLinks
) {
}
