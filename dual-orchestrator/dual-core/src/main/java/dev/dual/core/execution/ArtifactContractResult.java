package dev.dual.core.execution;

/**
 * Validation result for one required or optional structured artifact.
 */
public record ArtifactContractResult(
        String contractType,
        boolean required,
        boolean present,
        boolean valid,
        String path,
        String errorCode,
        String errorMessage
) {
    public ArtifactContractResult {
        contractType = contractType == null ? "" : contractType;
        path = path == null ? "" : path;
        errorCode = errorCode == null ? "" : errorCode;
        errorMessage = errorMessage == null ? "" : errorMessage;
        if (valid && !present) {
            throw new IllegalArgumentException("valid artifact contract requires present=true");
        }
    }

    public static ArtifactContractResult requiredValid(String contractType) {
        return new ArtifactContractResult(contractType, true, true, true, "", "", "");
    }

    public static ArtifactContractResult requiredMissing(String contractType) {
        return new ArtifactContractResult(
                contractType, true, false, false, "", "missing_required_artifact", "required artifact is missing"
        );
    }

    public static ArtifactContractResult optionalMissing(String contractType) {
        return new ArtifactContractResult(contractType, false, false, false, "", "", "");
    }

    public boolean satisfied() {
        return !required || (present && valid);
    }
}
