package dev.dual.core.contract;

import java.util.ArrayList;
import java.util.List;

public final class TaskContractValidator {

    private TaskContractValidator() {}

    public static List<String> validate(TaskContract contract) {
        List<String> errors = new ArrayList<>();

        // objective must not be blank (compact constructor already enforces, but defensive)
        if (contract.objective() == null || contract.objective().isBlank()) {
            errors.add("objective must not be blank");
        }

        // scope must not be null
        if (contract.scope() == null) {
            errors.add("scope must not be null");
        }

        // READY_FOR_REVIEW requires non-empty acceptanceCriteria
        if (contract.status() == ContractStatus.READY_FOR_REVIEW) {
            if (contract.acceptanceCriteria().isEmpty()) {
                errors.add("READY_FOR_REVIEW requires non-empty acceptanceCriteria");
            }
        }

        // NEEDS_CLARIFICATION requires non-empty humanQuestions
        if (contract.status() == ContractStatus.NEEDS_CLARIFICATION) {
            if (contract.humanQuestions().isEmpty()) {
                errors.add("NEEDS_CLARIFICATION requires non-empty humanQuestions");
            }
        }

        return errors;
    }
}
