package app.cryptoincome.funding.domain;

import java.time.Instant;

public class FundingBackfillCursorStalledException extends RuntimeException {

    public FundingBackfillCursorStalledException(int pageNo, Instant expectedBefore, Instant actualOldest) {
        super("回补游标未向过去推进: 第 " + pageNo + " 页 after=" + expectedBefore
                + " 但最旧记录=" + actualOldest);
    }
}
