package app.cryptoincome.scenario.domain;

public class ScenarioEvaluationException extends RuntimeException {
    private final ScenarioEvaluationFailureCode code;

    public ScenarioEvaluationException(ScenarioEvaluationFailureCode code, String message) {
        super(message);
        if (code == null) throw new IllegalArgumentException("情景评估失败代码不能为空");
        this.code = code;
    }

    public ScenarioEvaluationFailureCode code() {
        return code;
    }
}
