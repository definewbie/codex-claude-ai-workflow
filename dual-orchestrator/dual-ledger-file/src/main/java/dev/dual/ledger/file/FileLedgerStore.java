package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.domain.TimelineEvent;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileLedgerStore {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final ObjectMapper jsonlObjectMapper;

    public FileLedgerStore() {
        this(new AtomicJsonWriter());
    }

    FileLedgerStore(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        this.jsonlObjectMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public void initializeWorkstream(WorkstreamPaths paths, Workstream workstream, Workspace workspace, String requirement)
            throws IOException {
        initializeWorkstream(paths, workstream, workspace, requirement, WorkstreamSettingsDocument.of("auto"));
    }

    public void initializeWorkstream(
            WorkstreamPaths paths,
            Workstream workstream,
            Workspace workspace,
            String requirement,
            WorkstreamSettingsDocument settings
    ) throws IOException {
        Files.createDirectories(paths.workstreamDir().resolve("current"));
        Files.createDirectories(paths.workstreamDir().resolve("stages").resolve("01-design").resolve("iterations").resolve("001"));
        Files.createDirectories(paths.workstreamDir().resolve("artifacts"));
        Files.createDirectories(paths.workstreamDir().resolve("checkpoints"));
        Files.createDirectories(paths.workstreamDir().resolve("reports"));
        Files.createDirectories(paths.runsDir());

        jsonWriter.write(paths.workstreamJson(), WorkstreamDocument.from(workstream));
        jsonWriter.write(paths.settingsJson(), settings);
        var workspaceDocument = WorkspaceDocument.from(workspace);
        var stageDocument = StageDocument.initialDesign(workstream.workstreamId().value(), workspace.workspaceId().value());
        var iterationDocument = IterationDocument.initialDesign();
        var checkpointDocument = CheckpointDocument.initial(workstream.workstreamId().value(), workspace.workspaceId().value());

        jsonWriter.write(paths.workspaceJson(workspace.workspaceId()), workspaceDocument);
        jsonWriter.write(paths.designStageJson(), stageDocument);
        jsonWriter.write(paths.currentStageJson(), stageDocument);
        jsonWriter.write(paths.designIterationJson("001"), iterationDocument);
        jsonWriter.write(paths.currentIterationJson(), iterationDocument);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve("checkpoint-initial.json"), checkpointDocument);
        jsonWriter.write(paths.currentCheckpointJson(), checkpointDocument);
        Files.writeString(paths.requirementMarkdown(), requirement, StandardCharsets.UTF_8);
        appendTimeline(paths, TimelineEvent.of(
                workstream.workstreamId(),
                workspace.workspaceId(),
                "orchestrator",
                "workstream_created",
                "created design-loop workstream"
        ));
    }

    public WorkstreamStatusView loadStatus(Path workspaceRoot, String workstreamId) throws IOException {
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var workstream = objectMapper.readValue(paths.workstreamJson().toFile(), WorkstreamDocument.class);
        var workspace = objectMapper.readValue(
                paths.workspaceJson(new DualId(workstream.primaryWorkspaceId())).toFile(),
                WorkspaceDocument.class
        );
        var stage = objectMapper.readValue(paths.currentStageJson().toFile(), StageDocument.class);
        var iteration = objectMapper.readValue(paths.currentIterationJson().toFile(), IterationDocument.class);
        var checkpoint = objectMapper.readValue(paths.currentCheckpointJson().toFile(), CheckpointDocument.class);
        return new WorkstreamStatusView(paths.workstreamDir(), workstream, workspace, stage, iteration, checkpoint);
    }

    public void appendTimeline(WorkstreamPaths paths, TimelineEvent event) throws IOException {
        Files.createDirectories(paths.timelineJsonl().getParent());
        var line = jsonlObjectMapper.writeValueAsString(TimelineEventDocument.from(event)) + System.lineSeparator();
        Files.writeString(
                paths.timelineJsonl(),
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
