const state = {
  workdir: localStorage.getItem("dual.workdir") || "",
  selectedId: localStorage.getItem("dual.selectedId") || "",
  status: null,
  timeline: null,
};

const $ = (id) => document.getElementById(id);

const els = {
  workdir: $("workdir"),
  workspaceSummary: $("workspaceSummary"),
  workspacePath: $("workspacePath"),
  switchWorkspace: $("switchWorkspace"),
  workspaceChooser: $("workspaceChooser"),
  browseWorkspace: $("browseWorkspace"),
  refreshWorkspaceDirectories: $("refreshWorkspaceDirectories"),
  useWorkspace: $("useWorkspace"),
  closeWorkspaceChooser: $("closeWorkspaceChooser"),
  workspaceBrowser: $("workspaceBrowser"),
  maxSteps: $("maxSteps"),
  connection: $("connection"),
  count: $("count"),
  workstreams: $("workstreams"),
  selectedTitle: $("selectedTitle"),
  selectedStatus: $("selectedStatus"),
  lifecycleBadge: $("lifecycleBadge"),
  lifecycleProgress: $("lifecycleProgress"),
  gateGrid: $("gateGrid"),
  gateBadge: $("gateBadge"),
  decisionPanel: $("decisionPanel"),
  runHint: $("runHint"),
  actionHint: $("actionHint"),
  overviewCards: $("overviewCards"),
  overviewWhy: $("overviewWhy"),
  overviewNext: $("overviewNext"),
  overviewAttention: $("overviewAttention"),
  overviewTimeline: $("overviewTimeline"),
  overviewArtifacts: $("overviewArtifacts"),
  statusView: $("statusView"),
  timelineView: $("timelineView"),
  artifactSummary: $("artifactSummary"),
  responseView: $("responseView"),
  reviewPanel: $("reviewPanel"),
  reviewStructured: $("reviewStructured"),
  gateEvidence: $("gateEvidence"),
  reviewPacket: $("reviewPacket"),
  reviewGate: $("reviewGate"),
  recoveryPanel: $("recoveryPanel"),
  recoveryActions: $("recoveryActions"),
  recoveryMarkdown: $("recoveryMarkdown"),
  recoveryPath: $("recoveryPath"),
  feedback: $("feedback"),
  resume: $("resume"),
  approve: $("approve"),
  complete: $("complete"),
  retry: $("retry"),
  closeMistakenIteration: $("closeMistakenIteration"),
  revise: $("revise"),
  tick: $("tick"),
};

els.workdir.value = state.workdir;
renderWorkspaceSource();
setActionVisibility({ resume: false, approve: false, complete: false, retry: false, closeMistakenIteration: false, revise: false, tick: true });
els.runHint.textContent = "Select a workstream";

function setConnection(text, kind = "muted") {
  els.connection.textContent = text;
  els.connection.className = `badge ${kind}`;
}

function setResponse(value) {
  els.responseView.textContent = JSON.stringify(value, null, 2);
}

function renderWorkspaceSource() {
  const workdir = els.workdir.value.trim();
  els.workspaceSummary.textContent = workdir ? "Selected workspace" : "Server current directory";
  els.workspacePath.textContent = workdir || "Leave empty to use the server process workdir.";
}

async function browseWorkspace(path = els.workdir.value.trim()) {
  els.workspaceBrowser.textContent = "Loading directories...";
  try {
    const data = await request(`/api/workspaces/browse?path=${encodeURIComponent(path || "")}`);
    renderWorkspaceBrowser(data);
  } catch (error) {
    els.workspaceBrowser.textContent = error.message || JSON.stringify(error, null, 2);
  }
}

function renderWorkspaceBrowser(data) {
  const directories = data.directories || [];
  els.workdir.value = data.path || els.workdir.value;
  els.workspaceBrowser.innerHTML = `
    <div class="workspace-browser-header">
      <strong>${escapeHtml(data.path || "(current directory)")}</strong>
      <span>${escapeHtml(directories.length ? `${directories.length} directories` : "No child directories")}</span>
    </div>
  `;
  if (data.parent) {
    els.workspaceBrowser.appendChild(workspaceDirectoryButton("..", data.parent));
  }
  for (const directory of directories) {
    els.workspaceBrowser.appendChild(workspaceDirectoryButton(directory.name, directory.path));
  }
}

function workspaceDirectoryButton(label, path) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "workspace-directory";
  button.textContent = label;
  button.title = path;
  button.addEventListener("click", () => browseWorkspace(path));
  return button;
}

async function applyWorkspacePath() {
  state.workdir = els.workdir.value.trim();
  localStorage.setItem("dual.workdir", state.workdir);
  state.selectedId = "";
  localStorage.removeItem("dual.selectedId");
  renderWorkspaceSource();
  els.workspaceChooser.classList.add("hidden");
  await refreshList();
}

function workdirParam() {
  const workdir = els.workdir.value.trim();
  localStorage.setItem("dual.workdir", workdir);
  state.workdir = workdir;
  renderWorkspaceSource();
  return encodeURIComponent(workdir);
}

async function request(path, options = {}) {
  setConnection("Loading", "warn");
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  if (!response.ok) {
    setConnection("Error", "bad");
    throw body;
  }
  setConnection("Ready", "ok");
  return body;
}

async function refreshList() {
  try {
    const data = await request(`/api/workstreams?workdir=${workdirParam()}`);
    const items = data.workstreams || [];
    els.count.textContent = String(items.length);
    if (!state.selectedId && items.length) {
      state.selectedId = items[0].workstreamId;
      localStorage.setItem("dual.selectedId", state.selectedId);
    }
    renderWorkstreams(items);
    setResponse(data);
    if (state.selectedId && items.some((item) => item.workstreamId === state.selectedId)) {
      await loadSelected();
    }
  } catch (error) {
    setResponse(error);
  }
}

function renderWorkstreams(items) {
  els.workstreams.innerHTML = "";
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "subtle";
    empty.textContent = "No workstreams found for this workdir.";
    els.workstreams.appendChild(empty);
    return;
  }
  for (const item of items) {
    const button = document.createElement("button");
    button.className = `workstream ${item.workstreamId === state.selectedId ? "active" : ""}`;
    button.innerHTML = `
      <strong>${escapeHtml(item.workstreamId || "(unknown)")}</strong>
      <span>${escapeHtml(item.workflowStatus || item.status || "(no status)")}</span>
      <span>${escapeHtml(item.title || "")}</span>
    `;
    button.addEventListener("click", async () => {
      state.selectedId = item.workstreamId;
      localStorage.setItem("dual.selectedId", state.selectedId);
      renderWorkstreams(items);
      await loadSelected();
    });
    els.workstreams.appendChild(button);
  }
}

async function loadSelected() {
  if (!state.selectedId) {
    return;
  }
  await loadStatus();
  await Promise.all([loadOverview(), loadTimeline(), loadReviewPacket(), loadRecoverySummary()]);
}

async function loadStatus() {
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/status?workdir=${workdirParam()}`);
    state.status = data;
    els.selectedTitle.textContent = state.selectedId;
    const workflow = data.workflow || {};
    setStatusBadge(workflow.status || "-");
    updateActions(data);
    renderArtifactSummary(data.artifactContracts || []);
    els.statusView.textContent = JSON.stringify(data, null, 2);
    setResponse(data);
  } catch (error) {
    setResponse(error);
  }
}

async function loadTimeline() {
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/timeline?workdir=${workdirParam()}`);
    state.timeline = data;
    renderTimeline(data);
    if (state.overview) {
      renderLifecycleProgress(state.overview);
    }
  } catch (error) {
    els.timelineView.textContent = JSON.stringify(error, null, 2);
  }
}

async function loadOverview() {
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/overview?workdir=${workdirParam()}`);
    state.overview = data;
    renderOverview(data);
    if (state.status) {
      updateActions(state.status);
    }
  } catch (error) {
    renderOverviewError(error);
  }
}

function renderOverview(data) {
  const workflow = data.workflow || {};
  const acceptance = data.acceptance || {};
  const metrics = data.metrics || {};
  renderLifecycleProgress(data);
  renderOverviewCards([
    ["Agent runs", metrics.agentRunCount ?? "-"],
    ["Nodes", metrics.nodeCount ?? "-"],
    ["Gates", metrics.gateCount ?? "-"],
    ["Repairs", acceptance.repairLoopCount ?? 0],
    ["Duration", durationLabel(metrics.totalNodeDurationMs)],
    ["Confidence", acceptance.verificationConfidence || "UNKNOWN"],
  ]);
  els.overviewWhy.textContent = friendlyWhyStopped(data);
  els.overviewNext.textContent = friendlyNextAction(data);
  renderOverviewAttention(derivedAttention(data));
  renderOverviewTimeline(data.recentTimeline || []);
  renderOverviewArtifacts(data.artifacts || {});
}

function renderOverviewError(error) {
  els.overviewCards.innerHTML = "";
  els.lifecycleProgress.innerHTML = "";
  els.lifecycleBadge.textContent = "Unavailable";
  els.overviewWhy.textContent = "Overview is not available.";
  els.overviewNext.textContent = JSON.stringify(error);
  els.overviewAttention.innerHTML = "";
  els.overviewTimeline.innerHTML = "";
  els.overviewArtifacts.innerHTML = "";
}

function renderLifecycleProgress(data) {
  const workflow = data.workflow || {};
  const nodes = state.timeline?.nodes || data.recentTimeline || [];
  const currentNode = workflow.currentNode || "";
  const status = workflow.status || "";
  els.lifecycleBadge.textContent = lifecycleBadgeText(status, currentNode);
  els.lifecycleBadge.className = `badge ${statusBadgeKind(status)}`;

  const stageDefs = [
    ["Intake", "intake", "Requirement intake"],
    ["Contract", "contract", "Task contract"],
    ["Design", "design", "Architecture review"],
    ["Plan", "plan", "Implementation plan"],
    ["Implement", "implement", "Claude code changes"],
    ["Self-test", "self_test", "Developer evidence"],
    ["Acceptance", "acceptance", "Codex verifier"],
    ["Delivery", "delivery", "Report / commit / PR"],
  ];
  const completedStages = new Set();
  for (const node of nodes) {
    if (node.status === "COMPLETED") {
      completedStages.add(stageIdFromNode(node.nodeDefinitionId || ""));
    }
  }
  const currentStage = stageIdFromNode(currentNode);

  els.lifecycleProgress.innerHTML = "";
  for (const [label, stageId, detail] of stageDefs) {
    const item = document.createElement("article");
    const state = stageId === currentStage
      ? "current"
      : completedStages.has(stageId)
        ? "done"
        : "pending";
    item.className = `stage ${state}`;
    item.innerHTML = `
      <strong>${escapeHtml(label)}</strong>
      <span>${escapeHtml(stageDetail(stageId, detail, state, status))}</span>
    `;
    els.lifecycleProgress.appendChild(item);
  }
}

function stageIdFromNode(nodeId) {
  if (!nodeId) return "";
  if (nodeId.includes("requirement") || nodeId.includes("intake")) return "intake";
  if (nodeId.includes("contract")) return "contract";
  if (nodeId.includes("design") || nodeId.includes("arch")) return "design";
  if (nodeId.includes("plan")) return "plan";
  if (nodeId.includes("self_test") || nodeId.includes("self-test")) return "self_test";
  if (nodeId.includes("acceptance")) return "acceptance";
  if (nodeId.includes("implement")) return "implement";
  if (nodeId.includes("delivery") || nodeId.includes("report") || nodeId.includes("next_stage")) return "delivery";
  const prefix = nodeId.split(".")[0].replace("gate-", "");
  if (["intake", "contract", "design", "plan"].includes(prefix)) return prefix;
  return prefix;
}

function stageDetail(stageId, fallback, state, workflowStatus) {
  if (state === "current" && workflowStatus === "WAITING_FOR_HUMAN") return "等待人工确认";
  if (state === "current" && workflowStatus === "WAITING_FOR_RECOVERY") return "需要恢复";
  if (state === "current" && workflowStatus === "RUNNING") return "运行中";
  if (state === "done") return "Completed";
  return fallback;
}

function lifecycleBadgeText(status, currentNode) {
  if (!status) return "No workstream";
  if (status === "WAITING_FOR_HUMAN") return `${stageLabel(stageIdFromNode(currentNode))} gate waiting`;
  return status;
}

function stageLabel(stageId) {
  return {
    intake: "Intake",
    contract: "Contract",
    design: "Design",
    plan: "Plan",
    implement: "Implement",
    self_test: "Self-test",
    acceptance: "Acceptance",
    delivery: "Delivery",
  }[stageId] || "Workflow";
}

function renderOverviewCards(items) {
  els.overviewCards.innerHTML = "";
  for (const [label, value] of items) {
    const card = document.createElement("section");
    card.className = "overview-card";
    card.innerHTML = `
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    `;
    els.overviewCards.appendChild(card);
  }
}

function friendlyWhyStopped(data) {
  const workflow = data.workflow || {};
  const status = workflow.status || "";
  const currentNode = workflow.currentNode || "";
  if (status === "WAITING_FOR_HUMAN") {
    if (isNextStageGate(currentNode)) {
      return "本阶段已经完成，正在等待你决定：结束这个 workstream，还是进入下一轮。";
    }
    return "等待人工审查：请先阅读 Gate Evidence / Review Packet，再批准继续或要求修改。";
  }
  if (status === "WAITING_FOR_RECOVERY") {
    return `需要恢复：${currentNode || "当前节点"} 失败或需要人工处理。请先查看失败摘要，再决定是否 Retry。`;
  }
  if (status === "FAILED" || status === "BLOCKED") {
    return `流程已停止：${currentNode || "当前节点"} 无法继续。请查看 recovery 信息。`;
  }
  if (status === "RUNNING") {
    const stale = staleRunningSignal(data);
    if (stale) {
      return `状态可能不一致：${stale}`;
    }
    return currentNode ? `正在执行：${currentNode}` : "正在执行 workflow。";
  }
  if (status === "COMPLETED") {
    return "workflow 已完成。";
  }
  return data.whyStopped || "-";
}

function friendlyNextAction(data) {
  const workflow = data.workflow || {};
  const status = workflow.status || "";
  const currentNode = workflow.currentNode || "";
  if (status === "WAITING_FOR_HUMAN") {
    if (isNextStageGate(currentNode)) {
      return "建议：如果本阶段已满足目标，点击“完成 workstream”。后续阶段应通过新的明确入口启动。";
    }
    return "建议：阅读证据后点击“批准并继续”，或填写反馈并点击“请求修改”。";
  }
  if (status === "WAITING_FOR_RECOVERY" || status === "FAILED" || status === "BLOCKED") {
    return "建议：先查看 Recovery Summary，理解失败原因；确认外部问题已解决后再重试。";
  }
  if (status === "RUNNING") {
    return "建议：等待当前节点结束，或刷新查看最新状态。";
  }
  if (status === "COMPLETED") {
    return "建议：查看最终报告、handoff 和交付产物。";
  }
  return data.nextAction || "-";
}

function derivedAttention(data) {
  const items = [...(data.attention || [])];
  const stale = staleRunningSignal(data);
  if (stale) {
    items.unshift({
      severity: "critical",
      title: "Running state looks stale",
      detail: stale,
      command: `dualctl recovery show --workdir "${data.workspace || ""}" ${data.workstreamId || state.selectedId}`,
    });
  }
  return items;
}

function staleRunningSignal(data) {
  const workflow = data.workflow || {};
  if (workflow.status !== "RUNNING") {
    return "";
  }
  const currentNode = workflow.currentNode || "";
  const latest = (data.latestAgentRuns || [])[0] || {};
  const failureAvailable = Boolean(data.artifacts?.currentFailure?.available);
  if (latest.status === "failed" && (!currentNode || latest.nodeId === currentNode)) {
    return `The active node is ${currentNode || "unknown"}, but the latest agent run failed. Inspect recovery before continuing.`;
  }
  if (failureAvailable) {
    return `The workflow is marked RUNNING, but a current failure summary exists. Inspect recovery before continuing.`;
  }
  return "";
}

function isStaleRunningOverview() {
  return state.overview ? Boolean(staleRunningSignal(state.overview)) : false;
}

function durationLabel(value) {
  if (value === null || value === undefined || value === "") {
    return "-";
  }
  const ms = Number(value);
  if (!Number.isFinite(ms)) {
    return String(value);
  }
  if (ms < 1000) {
    return `${ms} ms`;
  }
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) {
    return `${seconds} s`;
  }
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  return remainder ? `${minutes}m ${remainder}s` : `${minutes}m`;
}

function renderOverviewAttention(items) {
  els.overviewAttention.innerHTML = "";
  if (!items.length) {
    els.overviewAttention.textContent = "No attention items.";
    return;
  }
  for (const item of items) {
    const row = document.createElement("article");
    row.className = `attention-item ${attentionClass(item.severity)}`;
    row.innerHTML = `
      <div>
        <strong>${escapeHtml(item.title || "Attention")}</strong>
        <p>${escapeHtml(item.detail || "")}</p>
        ${item.command ? `<code>${escapeHtml(item.command)}</code>` : ""}
      </div>
    `;
    els.overviewAttention.appendChild(row);
  }
}

function renderOverviewTimeline(nodes) {
  els.overviewTimeline.innerHTML = "";
  if (!nodes.length) {
    els.overviewTimeline.textContent = "No timeline yet.";
    return;
  }
  for (const node of nodes.slice(-6)) {
    const item = document.createElement("div");
    item.className = "mini-item";
    const label = node.displayName || node.nodeDefinitionId || "(node)";
    item.innerHTML = `
      <strong>${escapeHtml(label)}</strong>
      <span>${escapeHtml([node.status, node.iterationId, timeRangeLabel(node)].filter(Boolean).join(" · "))}</span>
    `;
    els.overviewTimeline.appendChild(item);
  }
}

function renderOverviewArtifacts(artifacts) {
  els.overviewArtifacts.innerHTML = "";
  const entries = Object.entries(artifacts);
  if (!entries.length) {
    els.overviewArtifacts.textContent = "No artifacts yet.";
    return;
  }
  for (const [key, artifact] of entries) {
    const item = document.createElement("div");
    item.className = `mini-item ${artifact.available ? "" : "muted-item"}`;
    item.innerHTML = `
      <strong>${escapeHtml(labelize(key))}</strong>
      <span>${escapeHtml(artifact.available ? artifact.path : "missing")}</span>
    `;
    els.overviewArtifacts.appendChild(item);
  }
}

async function loadReviewPacket() {
  const status = state.status?.workflow?.status || "";
  if (status !== "WAITING_FOR_HUMAN") {
    els.reviewPanel.classList.add("hidden");
    els.gateGrid.classList.add("gate-grid-single");
    els.decisionPanel.classList.add("hidden");
    els.reviewStructured.innerHTML = "";
    els.gateEvidence.innerHTML = "";
    els.reviewPacket.textContent = "";
    els.reviewGate.textContent = "";
    return;
  }
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/current?workdir=${workdirParam()}`);
    els.reviewPanel.classList.remove("hidden");
    els.gateGrid.classList.remove("gate-grid-single");
    els.decisionPanel.classList.remove("hidden");
    els.reviewGate.textContent = data.gateNodeDefinitionId || "";
    renderReviewStructured(data.packet || {});
    els.reviewPacket.textContent = data.packetMarkdown || "(empty review packet)";
  } catch (error) {
    els.reviewPanel.classList.remove("hidden");
    els.gateGrid.classList.remove("gate-grid-single");
    els.decisionPanel.classList.remove("hidden");
    els.reviewGate.textContent = "not available";
    els.reviewStructured.innerHTML = "";
    els.gateEvidence.innerHTML = "";
    els.reviewPacket.textContent = JSON.stringify(error, null, 2);
  }
}

async function loadRecoverySummary() {
  const status = state.status?.workflow?.status || "";
  const stopped = ["FAILED", "BLOCKED", "WAITING_FOR_RECOVERY", "RUNNING"].includes(status);
  if (!stopped) {
    els.recoveryPanel.classList.add("hidden");
    els.recoveryMarkdown.textContent = "";
    els.recoveryPath.textContent = "";
    return;
  }
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/recovery/current?workdir=${workdirParam()}`);
    if (!data.available) {
      els.recoveryPanel.classList.add("hidden");
      els.recoveryMarkdown.textContent = "";
      els.recoveryPath.textContent = "";
      return;
    }
    els.recoveryPanel.classList.remove("hidden");
    els.recoveryPath.textContent = data.recoveryPath || "";
    els.recoveryMarkdown.textContent = data.markdown || "";
  } catch (error) {
    els.recoveryPanel.classList.remove("hidden");
    els.recoveryPath.textContent = "not available";
    els.recoveryMarkdown.textContent = JSON.stringify(error, null, 2);
  }
}

function renderReviewStructured(packet) {
  els.reviewStructured.innerHTML = "";
  els.gateEvidence.innerHTML = "";
  if (!packet || !Object.keys(packet).length) {
    const empty = document.createElement("p");
    empty.className = "subtle";
    empty.textContent = "Structured packet data is not available. Read the raw packet below.";
    els.reviewStructured.appendChild(empty);
    return;
  }

  const header = document.createElement("section");
  header.className = "review-card review-card-primary";
  const decision = packet.latestAgentDecision || {};
  const contract = packet.taskContract || {};
  const guidance = packet.operatorGuidance || {};
  header.innerHTML = `
    <div class="review-card-head">
      <div>
        <span class="eyebrow">Review focus</span>
        <h3>${escapeHtml(contract.objective || packet.gateNodeDefinitionId || "Human review")}</h3>
      </div>
      ${guidance.recommendedAction ? `<span class="badge ${actionBadgeKind(guidance.recommendedAction)}">${escapeHtml(guidance.recommendedAction)}</span>` : ""}
    </div>
    ${guidance.reason ? `<p><strong>Recommended:</strong> ${escapeHtml(guidance.reason)}</p>` : ""}
    ${decision.summary ? `<p>${escapeHtml(decision.summary)}</p>` : ""}
    ${reviewList("Suggested Checks", guidance.suggestedChecks)}
    ${reviewList("Must Fix", decision.mustFix)}
  `;
  els.reviewStructured.appendChild(header);

  appendGateEvidenceSection("Task Contract", [
    reviewKeyValue("Status", contract.status),
    reviewList("Included Scope", contract.includedScope),
    reviewList("Excluded Scope", contract.excludedScope),
    reviewList("Acceptance Criteria", contract.acceptanceCriteria),
    reviewList("Test Requirements", contract.testRequirements),
    reviewList("Constraints", contract.constraints),
  ]);

  appendGateEvidenceSection("Agent Review Evidence", [
    reviewKeyValue("Decision", decision.decision),
    reviewKeyValue("Summary", decision.summary),
    reviewList("Must Fix", decision.mustFix),
    reviewList("Open Risks", decision.openRisks),
    reviewList("Missing Evidence", decision.missingEvidence),
  ]);

  const evidence = packet.gitEvidence || {};
  appendGateEvidenceSection("Git Evidence", [
    reviewList("Changed Files", evidence.changedFiles),
    reviewPre("Diff Stat", evidence.diffStat),
    reviewPre("Status", evidence.statusShort),
  ]);

  const delivery = packet.delivery || {};
  appendGateEvidenceSection("Delivery Artifacts", [
    reviewKeyValue("PR Body", delivery.prBodyPath),
    reviewKeyValue("Commit Message", delivery.commitMessagePath),
  ]);
}

function appendReviewSection(title, rows) {
  const content = rows.filter(Boolean).join("");
  if (!content) return;
  const section = document.createElement("section");
  section.className = "review-card";
  section.innerHTML = `<h3>${escapeHtml(title)}</h3>${content}`;
  els.reviewStructured.appendChild(section);
}

function appendGateEvidenceSection(title, rows) {
  const content = rows.filter(Boolean).join("");
  if (!content) return;
  const section = document.createElement("section");
  section.className = "review-card";
  section.innerHTML = `<h3>${escapeHtml(title)}</h3>${content}`;
  els.gateEvidence.appendChild(section);
}

function reviewKeyValue(label, value) {
  if (!value) return "";
  return `
    <div class="review-row">
      <span>${escapeHtml(label)}</span>
      <p>${escapeHtml(value)}</p>
    </div>
  `;
}

function reviewList(label, value) {
  const values = Array.isArray(value) ? value.filter(Boolean) : value ? [value] : [];
  if (!values.length) return "";
  return `
    <div class="review-row">
      <span>${escapeHtml(label)}</span>
      <ul>${values.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
    </div>
  `;
}

function reviewPre(label, value) {
  if (!value) return "";
  return `
    <div class="review-row review-row-block">
      <span>${escapeHtml(label)}</span>
      <pre>${escapeHtml(value)}</pre>
    </div>
  `;
}

function decisionBadgeKind(decision) {
  if (decision.includes("NOTES") || decision.includes("REVISION")) return "warn";
  if (decision.includes("ACCEPT") || decision.includes("READY")) return "ok";
  if (decision.includes("REJECT") || decision.includes("BLOCKED")) return "bad";
  return "muted";
}

function actionBadgeKind(action) {
  if (action === "approve" || action === "complete") return "ok";
  if (action === "revise") return "warn";
  return "muted";
}

function renderArtifactSummary(contracts) {
  els.artifactSummary.innerHTML = "";
  if (!contracts.length) {
    const empty = document.createElement("p");
    empty.className = "subtle";
    empty.textContent = "No agent artifact contract sidecars yet.";
    els.artifactSummary.appendChild(empty);
    return;
  }
  for (const contract of contracts.slice(0, 8)) {
    const item = document.createElement("article");
    item.className = "artifact-item";
    item.innerHTML = `
      <div class="artifact-head">
        <strong>${escapeHtml(contract.node_id || "(agent output)")}</strong>
        <span class="badge muted">${escapeHtml(contract.decision || "-")}</span>
      </div>
      <p>${escapeHtml(contract.summary || "")}</p>
      ${artifactLine("Must Fix", contract.must_fix)}
      ${artifactLine("Open Risks", contract.open_risks)}
      ${artifactLine("Changed Files", contract.changed_files)}
      ${artifactLine("Verification", contract.verification_summary)}
      <span class="artifact-ref">${escapeHtml(contract.sidecar_ref || "")}</span>
    `;
    els.artifactSummary.appendChild(item);
  }
}

function artifactLine(label, value) {
  const values = Array.isArray(value) ? value.filter(Boolean) : value ? [value] : [];
  if (!values.length) return "";
  return `<div class="artifact-line"><span>${escapeHtml(label)}</span><p>${escapeHtml(values.join("; "))}</p></div>`;
}

function renderTimeline(data) {
  const nodes = [...(data.nodes || [])].sort(compareTimelineNodes);
  els.timelineView.innerHTML = "";
  if (!nodes.length) {
    els.timelineView.textContent = JSON.stringify(data, null, 2);
    return;
  }
  const groups = groupByIteration(nodes);
  for (const [iterationId, items] of groups) {
    const group = document.createElement("article");
    group.className = "iteration";
    group.innerHTML = `
      <div class="iteration-header">
        <strong>${escapeHtml(iterationId || "iteration")}</strong>
        <span class="badge muted">${escapeHtml(items.length)} nodes</span>
      </div>
    `;
    for (const node of items) {
      const item = document.createElement("div");
      item.className = "timeline-item";
      const label = node.displayName || node.nodeDefinitionId || node.id || "(node)";
      item.innerHTML = `
        <strong>${escapeHtml(label)}</strong>
        <span>${escapeHtml(node.kind || "")}</span>
        <span>${escapeHtml(node.status || "")}</span>
        <span>${escapeHtml(timeRangeLabel(node) || "-")}</span>
        <span>${escapeHtml(durationLabel(node.durationMs))}</span>
      `;
      group.appendChild(item);
    }
    els.timelineView.appendChild(group);
  }
}

function groupByIteration(nodes) {
  const groups = new Map();
  for (const node of nodes) {
    const key = node.iterationId || "iteration";
    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key).push(node);
  }
  return groups;
}

function compareTimelineNodes(left, right) {
  const leftTime = timelineTimestamp(left);
  const rightTime = timelineTimestamp(right);
  if (leftTime !== rightTime) {
    return leftTime - rightTime;
  }
  return String(left.nodeInstanceId || left.nodeDefinitionId || "")
    .localeCompare(String(right.nodeInstanceId || right.nodeDefinitionId || ""));
}

function timelineTimestamp(node) {
  for (const value of [node.startedAt, node.finishedAt, node.updatedAt, node.createdAt]) {
    if (!value) {
      continue;
    }
    const time = new Date(value).getTime();
    if (!Number.isNaN(time)) {
      return time;
    }
  }
  return Number.MAX_SAFE_INTEGER;
}

function timeRangeLabel(node) {
  const started = formatTime(node.startedAt || node.createdAt);
  const finished = formatTime(node.finishedAt || node.updatedAt);
  if (started && finished && started !== finished) {
    return `${started} - ${finished}`;
  }
  return started || finished || "";
}

function formatTime(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return date.toLocaleString([], {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function setStatusBadge(status) {
  els.selectedStatus.textContent = status;
  els.selectedStatus.className = `badge ${statusBadgeKind(status)}`;
  els.gateBadge.textContent = status || "-";
  els.gateBadge.className = `badge ${statusBadgeKind(status)}`;
}

function statusBadgeKind(status) {
  if (status === "COMPLETED") return "ok";
  if (status === "WAITING_FOR_HUMAN" || status === "RUNNING") return "warn";
  if (status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY") return "bad";
  return "muted";
}

function updateActions(data) {
  const workflow = data.workflow || {};
  const status = workflow.status || "";
  const activeNode = (workflow.activeNodes || [])[0] || {};
  const activeKind = activeNode.kind || "";
  const activeDefinition = activeNode.nodeDefinitionId || "";
  const nextStageGate = activeDefinition.includes("next_stage");

  setActionVisibility({
    resume: status === "RUNNING" && !isStaleRunningOverview(),
    approve: status === "WAITING_FOR_HUMAN" && !nextStageGate,
    complete: status === "WAITING_FOR_HUMAN" && nextStageGate,
    retry: status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY",
    closeMistakenIteration: canCloseMistakenIteration(),
    revise: status === "WAITING_FOR_HUMAN",
    tick: status === "RUNNING" || !state.selectedId,
  });
  applyDecisionLabels(status, nextStageGate);

  els.feedback.parentElement.classList.toggle("hidden", status !== "WAITING_FOR_HUMAN");
  els.revise.parentElement.classList.toggle("hidden", status !== "WAITING_FOR_HUMAN");

  if (status === "COMPLETED") {
    els.actionHint.textContent = "Workflow 已完成，没有后续动作。";
    els.runHint.textContent = "已完成";
    return;
  }
  if (status === "WAITING_FOR_HUMAN") {
    els.actionHint.textContent = nextStageGate
      ? "本阶段已完成。请完成 workstream；后续阶段应通过新的明确入口启动。"
      : "请先阅读 Gate Evidence，再批准继续或请求修改。";
    els.runHint.textContent = nextStageGate ? "本阶段已完成" : "等待人工审查";
    return;
  }
  if (status === "RUNNING") {
    if (isStaleRunningOverview()) {
      els.actionHint.textContent = `状态显示正在运行 ${activeDefinition || activeKind}，但 recovery 证据显示可能已卡住。请先查看失败摘要。`;
      els.runHint.textContent = "检查 recovery";
      return;
    }
    els.actionHint.textContent = `正在执行 ${activeDefinition || activeKind}。等待节点结束后刷新状态。`;
    els.runHint.textContent = "运行中";
    return;
  }
  if (status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY") {
    els.actionHint.textContent = canCloseMistakenIteration()
      ? "检测到终态后误开的下一轮。确认上一轮已完成后，可以关闭误开的下一轮。"
      : "Workflow 需要恢复。先查看失败摘要，确认原因和外部条件后，再重试当前节点。";
    els.runHint.textContent = "需要恢复";
    return;
  }
  els.runHint.textContent = state.selectedId ? "不可直接运行" : "选择 workstream";
  els.actionHint.textContent = state.selectedId
    ? "当前 workflow 状态没有可直接执行的动作。"
    : "选择一个 workstream 后查看可用动作。";
}

function applyDecisionLabels(status, nextStageGate) {
  els.approve.textContent = "批准并继续";
  els.complete.textContent = nextStageGate ? "完成 workstream" : "结束 workstream";
  els.complete.classList.toggle("danger", !nextStageGate);
  els.complete.classList.remove("secondary");
  els.approve.classList.remove("secondary");
}

function isNextStageGate(nodeId) {
  return String(nodeId || "").includes("next_stage");
}

function canCloseMistakenIteration() {
  return state.overview?.recoveryAssessment?.mistakenIterationCloseable === true;
}

function setActionVisibility(visible) {
  els.resume.disabled = !visible.resume;
  els.approve.classList.toggle("hidden", !visible.approve);
  els.complete.classList.toggle("hidden", !visible.complete);
  els.retry.classList.toggle("hidden", !visible.retry);
  els.closeMistakenIteration.classList.toggle("hidden", !visible.closeMistakenIteration);
  els.recoveryActions.classList.toggle("hidden", !visible.closeMistakenIteration);
  els.revise.classList.toggle("hidden", !visible.revise);
  els.tick.disabled = !visible.tick;
}

async function postAction(action, body) {
  if (!state.selectedId) {
    setResponse({ error: "No workstream selected" });
    return;
  }
  try {
    const data = await request(action, {
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    setResponse(data);
    await loadSelected();
    await refreshList();
  } catch (error) {
    setResponse(error);
  }
}

function maxSteps() {
  return Number.parseInt(els.maxSteps.value, 10) || 1;
}

function selectedBase() {
  return `/api/workstreams/${encodeURIComponent(state.selectedId)}`;
}

function labelize(value) {
  return String(value)
    .replace(/([A-Z])/g, " $1")
    .replace(/^./, (letter) => letter.toUpperCase());
}

function attentionClass(severity) {
  if (severity === "critical") return "attention-critical";
  if (severity === "warning") return "attention-warning";
  if (severity === "ok") return "attention-ok";
  return "attention-info";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

$("refresh").addEventListener("click", refreshList);
els.switchWorkspace.addEventListener("click", () => {
  els.workspaceChooser.classList.toggle("hidden");
  if (!els.workspaceChooser.classList.contains("hidden")) {
    els.workdir.focus();
    browseWorkspace();
  }
});
els.workdir.addEventListener("change", () => {
  browseWorkspace();
});
els.browseWorkspace.addEventListener("click", () => browseWorkspace());
els.refreshWorkspaceDirectories.addEventListener("click", () => browseWorkspace());
els.useWorkspace.addEventListener("click", applyWorkspacePath);
els.closeWorkspaceChooser.addEventListener("click", () => els.workspaceChooser.classList.add("hidden"));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    els.workspaceChooser.classList.add("hidden");
  }
});
$("tick").addEventListener("click", async () => {
  try {
    const data = await request(`/api/scheduler/tick?workdir=${workdirParam()}&maxWorkstreams=10&maxStepsPerWorkstream=${maxSteps()}`, {
      method: "POST",
    });
    setResponse(data);
    await refreshList();
  } catch (error) {
    setResponse(error);
  }
});

$("resume").addEventListener("click", () => postAction(`${selectedBase()}/resume?workdir=${workdirParam()}&maxSteps=${maxSteps()}`));
$("approve").addEventListener("click", () => postAction(`${selectedBase()}/review/approve?workdir=${workdirParam()}&run=true&maxSteps=${maxSteps()}`));
$("complete").addEventListener("click", () => postAction(`${selectedBase()}/review/complete?workdir=${workdirParam()}`, { note: "completed from operator panel" }));
$("retry").addEventListener("click", () => postAction(`${selectedBase()}/retry?workdir=${workdirParam()}`, { note: "retry from operator panel" }));
$("closeMistakenIteration").addEventListener("click", () => postAction(
  `${selectedBase()}/recovery/close-mistaken-iteration?workdir=${workdirParam()}`,
  { note: "closed mistaken iteration from operator panel" }
));
$("revise").addEventListener("click", () => {
  const feedback = els.feedback.value.trim();
  postAction(`${selectedBase()}/review/revise?workdir=${workdirParam()}&run=true&maxSteps=${maxSteps()}`, { feedback });
});

refreshList();
