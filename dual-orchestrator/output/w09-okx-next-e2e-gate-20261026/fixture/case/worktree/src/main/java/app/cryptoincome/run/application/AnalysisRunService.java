package app.cryptoincome.run.application;

import app.cryptoincome.run.domain.AnalysisRun;
import app.cryptoincome.run.domain.AnalysisRunRepository;
import app.cryptoincome.run.domain.AnalysisRunType;
import app.cryptoincome.run.domain.IllegalRunTransitionException;
import app.cryptoincome.run.domain.PortfolioMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class AnalysisRunService {

    private final AnalysisRunRepository repository;

    public AnalysisRunService(AnalysisRunRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AnalysisRun create(String idempotencyKey, AnalysisRunType type, PortfolioMode mode) {
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey", 128);
        if (type == AnalysisRunType.SYNC_MARKET) {
            throw new IllegalArgumentException("SYNC_MARKET execution 必须由对应同步请求的原子 Store 创建");
        }
        return repository.createOrGet(
                normalizedKey,
                Objects.requireNonNull(type, "type 不能为空"),
                Objects.requireNonNull(mode, "mode 不能为空")
        );
    }

    @Transactional
    public AnalysisRun start(long id) {
        rejectExternallyOwnedExecution(id);
        if (!repository.transitionToRunning(id)) {
            throw new IllegalRunTransitionException(id, "RUNNING");
        }
        return getRequired(id);
    }

    @Transactional
    public AnalysisRun succeed(long id) {
        rejectExternallyOwnedExecution(id);
        if (!repository.transitionToSucceeded(id)) {
            throw new IllegalRunTransitionException(id, "SUCCEEDED");
        }
        return getRequired(id);
    }

    @Transactional
    public AnalysisRun fail(long id, String failureCode, String failureDetail) {
        rejectExternallyOwnedExecution(id);
        String normalizedCode = requireText(failureCode, "failureCode", 64);
        String normalizedDetail = requireText(failureDetail, "failureDetail", 1000);
        if (!repository.transitionToFailed(id, normalizedCode, normalizedDetail)) {
            throw new IllegalRunTransitionException(id, "FAILED");
        }
        return getRequired(id);
    }

    @Transactional(readOnly = true)
    public AnalysisRun getRequired(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("analysis_run 不存在: " + id));
    }

    @Transactional(readOnly = true)
    public AnalysisRun getRequiredByIdempotencyKey(String idempotencyKey) {
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey", 128);
        return repository.findByIdempotencyKey(normalizedKey)
                .orElseThrow(() -> new IllegalArgumentException("analysis_run 不存在: " + normalizedKey));
    }

    private void rejectExternallyOwnedExecution(long id) {
        AnalysisRun run = getRequired(id);
        if (run.syncRequestId() != null || run.backfillRequestId() != null) {
            throw new IllegalArgumentException("同步/回补 execution 的状态只能由对应请求的原子 Store 修改: " + id);
        }
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
