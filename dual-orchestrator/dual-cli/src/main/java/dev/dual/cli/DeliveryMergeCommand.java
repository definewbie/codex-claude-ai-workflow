package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.DeliveryMergePlan;
import dev.dual.ledger.file.DeliveryMergePlanWriter;
import dev.dual.ledger.file.DeliveryMergeStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "merge", description = "Apply an accepted worktree diff back to the original repo.", mixinStandardHelpOptions = true)
public final class DeliveryMergeCommand implements Callable<Integer> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Option(names = "--workdir", description = "Source worktree directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--target", required = true, description = "Original repository that receives the accepted diff.")
    Path target;

    @Parameters(index = "0")
    String workstreamId;

    private final GitPatchMergeService gitPatchMergeService;
    private final DeliveryMergePlanWriter planWriter;

    public DeliveryMergeCommand() {
        this(new GitPatchMergeService(), new DeliveryMergePlanWriter());
    }

    DeliveryMergeCommand(GitPatchMergeService gitPatchMergeService, DeliveryMergePlanWriter planWriter) {
        this.gitPatchMergeService = gitPatchMergeService;
        this.planWriter = planWriter;
    }

    @Override
    public Integer call() throws Exception {
        var sourceWorktree = workdir.toAbsolutePath().normalize();
        var targetRepo = target.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(sourceWorktree, new DualId(workstreamId));
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "delivery merge"))) {
            try {
                var acceptanceDecision = verifyReadyForDelivery(paths);
                var preview = gitPatchMergeService.preview(sourceWorktree, targetRepo);
                writePreview(paths, preview);
                var readyPlan = DeliveryMergePlan.create(
                        workstreamId,
                        sourceWorktree.toString(),
                        targetRepo.toString(),
                        acceptanceDecision,
                        preview.changedFiles(),
                        preview.excludedFiles(),
                        DeliveryMergeStatus.READY,
                        ""
                );
                planWriter.write(paths, readyPlan);

                var apply = gitPatchMergeService.apply(targetRepo, preview.patch());
                Files.writeString(paths.deliveryApplyStdoutLog(), apply.stdout(), StandardCharsets.UTF_8);
                Files.writeString(paths.deliveryApplyStderrLog(), apply.stderr(), StandardCharsets.UTF_8);
                if (!apply.succeeded()) {
                    var failed = readyPlan.withStatus(DeliveryMergeStatus.FAILED, apply.stderr().stripTrailing());
                    planWriter.write(paths, failed);
                    System.err.println("Delivery merge failed while applying patch.");
                    System.err.println("stderr: " + paths.deliveryApplyStderrLog());
                    return 1;
                }

                var applied = readyPlan.withStatus(DeliveryMergeStatus.APPLIED, "");
                planWriter.write(paths, applied);
                var diffStat = safeDiffStat(targetRepo);
                System.out.println("Delivery merge applied");
                System.out.println("Target repo: " + targetRepo);
                System.out.println("Merge plan: " + paths.deliveryMergePlanJson());
                System.out.println();
                System.out.println("Changed files:");
                for (var file : preview.changedFiles()) {
                    System.out.println("  " + file);
                }
                if (!preview.excludedFiles().isEmpty()) {
                    System.out.println();
                    System.out.println("Excluded files:");
                    for (var file : preview.excludedFiles()) {
                        System.out.println("  " + file);
                    }
                }
                System.out.println();
                System.out.println("Diff stat:");
                System.out.println(diffStat.isBlank() ? "  (empty)" : diffStat);
                System.out.println();
                System.out.println("Next:");
                System.out.println("  cd " + targetRepo);
                System.out.println("  git diff --cached");
                System.out.println("  git commit -m \"feat: dual delivery " + workstreamId + "\"");
                return 0;
            } catch (Exception e) {
                writeFailedPlan(paths, sourceWorktree, targetRepo, e.getMessage());
                System.err.println("Delivery merge failed: " + e.getMessage());
                return 1;
            }
        }
    }

    private String verifyReadyForDelivery(WorkstreamPaths paths) throws Exception {
        var current = readMap(paths.workflowCurrentJson());
        var workflowStatus = text(current.get("status"));
        if (!"COMPLETED".equals(workflowStatus)) {
            throw new IllegalStateException("workstream is not completed: " + workflowStatus);
        }
        var completionAudit = readMap(paths.completionAuditJson());
        var auditStatus = text(completionAudit.get("status"));
        if (!"ready".equalsIgnoreCase(auditStatus)) {
            throw new IllegalStateException("completion audit is not ready: " + auditStatus);
        }
        var decision = acceptanceDecision(paths);
        if (!"ACCEPT".equals(decision) && !"ACCEPT_WITH_NOTES".equals(decision)) {
            throw new IllegalStateException("final acceptance does not allow delivery merge: " + decision);
        }
        return decision;
    }

    private String acceptanceDecision(WorkstreamPaths paths) throws Exception {
        var decisions = new java.util.ArrayList<String>();
        if (Files.isDirectory(paths.runsDir())) {
            try (var stream = Files.walk(paths.runsDir(), 4)) {
                var contracts = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("acceptance-codex-review.contract.json"))
                        .sorted()
                        .toList();
                for (var contract : contracts) {
                    var json = readMap(contract);
                    var decision = text(json.get("decision"));
                    if (!decision.isBlank()) {
                        decisions.add(decision);
                    }
                }
            }
        }
        if (decisions.isEmpty()) {
            throw new IllegalStateException("final acceptance decision not found");
        }
        return decisions.get(decisions.size() - 1);
    }

    private void writePreview(WorkstreamPaths paths, GitPatchMergeService.MergePreview preview) throws Exception {
        Files.createDirectories(paths.deliveryDir());
        Files.writeString(paths.deliveryMergePatch(), preview.patch(), StandardCharsets.UTF_8);
        Files.writeString(paths.deliveryChangedFilesTxt(), String.join(System.lineSeparator(), preview.changedFiles())
                + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(paths.deliveryExcludedFilesTxt(), String.join(System.lineSeparator(), preview.excludedFiles())
                + (preview.excludedFiles().isEmpty() ? "" : System.lineSeparator()), StandardCharsets.UTF_8);
    }

    private void writeFailedPlan(WorkstreamPaths paths, Path sourceWorktree, Path targetRepo, String reason) throws Exception {
        Files.createDirectories(paths.deliveryDir());
        var plan = DeliveryMergePlan.create(
                workstreamId,
                sourceWorktree.toString(),
                targetRepo.toString(),
                "",
                List.of(),
                List.of(),
                DeliveryMergeStatus.FAILED,
                reason
        );
        planWriter.write(paths, plan);
    }

    private String safeDiffStat(Path targetRepo) {
        try {
            return gitPatchMergeService.diffStat(targetRepo);
        } catch (Exception e) {
            return "(diff stat unavailable after successful apply: " + e.getMessage() + ")";
        }
    }

    private Map<String, Object> readMap(Path path) throws Exception {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
