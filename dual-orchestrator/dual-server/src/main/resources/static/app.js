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
  maxSteps: $("maxSteps"),
  connection: $("connection"),
  count: $("count"),
  workstreams: $("workstreams"),
  selectedTitle: $("selectedTitle"),
  selectedStatus: $("selectedStatus"),
  lifecycleBadge: $("lifecycleBadge"),
  lifecycleProgress: $("lifecycleProgress"),
  gateBadge: $("gateBadge"),
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
  recoveryMarkdown: $("recoveryMarkdown"),
  recoveryPath: $("recoveryPath"),
  feedback: $("feedback"),
  resume: $("resume"),
  approve: $("approve"),
  complete: $("complete"),
  retry: $("retry"),
  revise: $("revise"),
  tick: $("tick"),
};

els.workdir.value = state.workdir;
renderWorkspaceSource();
setActionVisibility({ resume: false, approve: false, complete: false, retry: false, revise: false, tick: true });
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
  els.overviewWhy.textContent = data.whyStopped || "-";
  els.overviewNext.textContent = data.nextAction || "-";
  renderOverviewAttention(data.attention || []);
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
  if (state === "current" && workflowStatus === "WAITING_FOR_HUMAN") return "Waiting for approval";
  if (state === "current" && workflowStatus === "WAITING_FOR_RECOVERY") return "Needs recovery";
  if (state === "current" && workflowStatus === "RUNNING") return "Running";
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
    item.innerHTML = `
      <strong>${escapeHtml(node.nodeDefinitionId || "(node)")}</strong>
      <span>${escapeHtml([node.status, node.iterationId].filter(Boolean).join(" · "))}</span>
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
    els.reviewStructured.innerHTML = "";
    els.gateEvidence.innerHTML = "";
    els.reviewPacket.textContent = "";
    els.reviewGate.textContent = "";
    return;
  }
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/current?workdir=${workdirParam()}`);
    els.reviewPanel.classList.remove("hidden");
    els.reviewGate.textContent = data.gateNodeDefinitionId || "";
    renderReviewStructured(data.packet || {});
    els.reviewPacket.textContent = data.packetMarkdown || "(empty review packet)";
  } catch (error) {
    els.reviewPanel.classList.remove("hidden");
    els.reviewGate.textContent = "not available";
    els.reviewStructured.innerHTML = "";
    els.gateEvidence.innerHTML = "";
    els.reviewPacket.textContent = JSON.stringify(error, null, 2);
  }
}

async function loadRecoverySummary() {
  const status = state.status?.workflow?.status || "";
  const stopped = ["FAILED", "BLOCKED", "WAITING_FOR_RECOVERY"].includes(status);
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
  const nodes = data.nodes || [];
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
      item.innerHTML = `
        <strong>${escapeHtml(node.nodeDefinitionId || node.id || "(node)")}</strong>
        <span>${escapeHtml(node.kind || "")}</span>
        <span>${escapeHtml(node.status || "")}</span>
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

  setActionVisibility({
    resume: status === "RUNNING",
    approve: status === "WAITING_FOR_HUMAN",
    complete: status === "WAITING_FOR_HUMAN" && activeDefinition.includes("next_stage"),
    retry: status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY",
    revise: status === "WAITING_FOR_HUMAN",
    tick: status === "RUNNING" || !state.selectedId,
  });

  els.feedback.parentElement.classList.toggle("hidden", status !== "WAITING_FOR_HUMAN");
  els.revise.parentElement.classList.toggle("hidden", status !== "WAITING_FOR_HUMAN");

  if (status === "COMPLETED") {
    els.actionHint.textContent = "Workflow is completed. There are no remaining actions.";
    els.runHint.textContent = "Completed";
    return;
  }
  if (status === "WAITING_FOR_HUMAN") {
    els.actionHint.textContent = activeDefinition.includes("next_stage")
      ? "Review the current packet. Complete finishes this workstream; approve starts another iteration."
      : "Review the gate evidence, then approve to continue or request a revision.";
    els.runHint.textContent = "Blocked by human gate";
    return;
  }
  if (status === "RUNNING") {
    els.actionHint.textContent = `Workflow is running at ${activeDefinition || activeKind}. Run advances up to Max steps; Scheduler Tick advances running workstreams in this workdir.`;
    els.runHint.textContent = "Ready";
    return;
  }
  if (status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY") {
    els.actionHint.textContent = "Workflow is stopped. Fix the cause if needed, then Retry marks the active node ready to run again.";
    els.runHint.textContent = "Needs recovery";
    return;
  }
  els.runHint.textContent = state.selectedId ? "Not runnable" : "Select a workstream";
  els.actionHint.textContent = state.selectedId
    ? "No direct action is available for the current workflow state."
    : "Select a workstream to see available actions.";
}

function setActionVisibility(visible) {
  els.resume.disabled = !visible.resume;
  els.approve.classList.toggle("hidden", !visible.approve);
  els.complete.classList.toggle("hidden", !visible.complete);
  els.retry.classList.toggle("hidden", !visible.retry);
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
  els.workdir.classList.toggle("hidden");
  if (!els.workdir.classList.contains("hidden")) {
    els.workdir.focus();
  }
});
els.workdir.addEventListener("change", () => {
  state.selectedId = "";
  localStorage.removeItem("dual.selectedId");
  renderWorkspaceSource();
  refreshList();
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
$("revise").addEventListener("click", () => {
  const feedback = els.feedback.value.trim();
  postAction(`${selectedBase()}/review/revise?workdir=${workdirParam()}&run=true&maxSteps=${maxSteps()}`, { feedback });
});

refreshList();
