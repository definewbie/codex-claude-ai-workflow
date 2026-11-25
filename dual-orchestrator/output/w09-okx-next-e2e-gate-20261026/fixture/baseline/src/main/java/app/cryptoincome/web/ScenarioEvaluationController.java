package app.cryptoincome.web;

import app.cryptoincome.scenario.application.ScenarioEvaluationCommand;
import app.cryptoincome.scenario.application.ScenarioEvaluationService;
import app.cryptoincome.scenario.domain.ScenarioDecisionReadModel;
import app.cryptoincome.scenario.domain.ScenarioEvaluationException;
import app.cryptoincome.scenario.domain.ScenarioEvaluationFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenario-evaluations")
public class ScenarioEvaluationController {
    private final ScenarioEvaluationService service;

    public ScenarioEvaluationController(ScenarioEvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public ScenarioDecisionReadModel evaluate(@RequestBody ScenarioEvaluationCommand command) {
        return service.evaluate(command);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorView> invalidRequest(Exception error) {
        String message = error instanceof HttpMessageNotReadableException
                ? "请求JSON无法转换为有效的情景定义" : safeMessage(error, "情景请求不合法");
        return ResponseEntity.badRequest().body(new ErrorView(
                ScenarioEvaluationFailureCode.INVALID_SCENARIO_REQUEST.name(), message));
    }

    @ExceptionHandler(ScenarioEvaluationException.class)
    public ResponseEntity<ErrorView> evaluationFailure(ScenarioEvaluationException error) {
        HttpStatus status = switch (error.code()) {
            case INSUFFICIENT_PRODUCT_EVIDENCE, NO_FEASIBLE_PORTFOLIO -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_SCENARIO_REQUEST -> HttpStatus.BAD_REQUEST;
            case SCENARIO_CALCULATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String message = status == HttpStatus.INTERNAL_SERVER_ERROR
                ? "情景计算不变量失败" : safeMessage(error, "无法完成情景评估");
        return ResponseEntity.status(status).body(new ErrorView(error.code().name(), message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorView> calculationFailure(IllegalStateException error) {
        return ResponseEntity.internalServerError().body(new ErrorView(
                ScenarioEvaluationFailureCode.SCENARIO_CALCULATION_FAILED.name(), "情景计算不变量失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorView> unexpectedFailure(Exception error) {
        return ResponseEntity.internalServerError().body(new ErrorView(
                ScenarioEvaluationFailureCode.SCENARIO_CALCULATION_FAILED.name(), "情景计算不变量失败"));
    }

    private static String safeMessage(Exception error, String fallback) {
        String message = error.getMessage() == null || error.getMessage().isBlank() ? fallback : error.getMessage();
        return message.substring(0, Math.min(200, message.length()));
    }

    public record ErrorView(String code, String message) {}
}
