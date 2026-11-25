package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** 开仓成本只能选择聚合值或明细值，类型本身阻止重复扣减。 */
public sealed interface OpeningCost permits OpeningCost.Aggregate, OpeningCost.Components {

    BigDecimal amount();

    record Aggregate(BigDecimal amount) implements OpeningCost {
        public Aggregate {
            amount = requireCost(amount, "开仓聚合成本");
        }
    }

    record Components(BigDecimal spreadCost, BigDecimal spotFee, BigDecimal perpetualFee)
            implements OpeningCost {
        public Components {
            spreadCost = requireCost(spreadCost, "开仓价差成本");
            spotFee = requireCost(spotFee, "现货开仓手续费");
            perpetualFee = requireCost(perpetualFee, "合约开仓手续费");
        }

        @Override
        public BigDecimal amount() {
            return spreadCost.add(spotFee).add(perpetualFee);
        }
    }

    private static BigDecimal requireCost(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + "不能为负数");
        }
        return value;
    }
}
