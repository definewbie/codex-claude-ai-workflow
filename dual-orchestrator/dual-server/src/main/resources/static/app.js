const state = {
  workdir: localStorage.getItem("dual.workdir") || "",
  selectedId: localStorage.getItem("dual.selectedId") || "",
  status: null,
  timeline: null,
};

const $ = (id) => document.getElementById(id);

const els = {
  workdir: $("workdir"),
  maxSteps: $("maxSteps"),
  connection: $("connection"),
  count: $("count"),
  workstreams: $("workstreams"),
  selectedTitle: $("selectedTitle"),
  selectedStatus: $("selectedStatus"),
  actionHint: $("actionHint"),
  statusView: $("statusView"),
  timelineView: $("timelineView"),
  responseView: $("responseView"),
  reviewPanel: $("reviewPanel"),
  reviewPacket: $("reviewPacket"),
  reviewGate: $("reviewGate"),
  feedback: $("feedback"),
  resume: $("resume"),
  approve: $("approve"),
  complete: $("complete"),
  retry: $("retry"),
  revise: $("revise"),
  tick: $("tick"),
};

els.workdir.value = state.workdir;

function setConnection(text, kind = "muted") {
  els.connection.textContent = text;
  els.connection.className = `badge ${kind}`;
}

function setResponse(value) {
  els.responseView.textContent = JSON.stringify(value, null, 2);
}

function workdirParam() {
  const workdir = els.workdir.value.trim();
  localStorage.setItem("dual.workdir", workdir);
  return encodeURIComponent(workdir || ".");
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
  await Promise.all([loadTimeline(), loadReviewPacket()]);
}

async function loadStatus() {
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/status?workdir=${workdirParam()}`);
    state.status = data;
    els.selectedTitle.textContent = state.selectedId;
    const workflow = data.workflow || {};
    setStatusBadge(workflow.status || "-");
    updateActions(data);
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
  } catch (error) {
    els.timelineView.textContent = JSON.stringify(error, null, 2);
  }
}

async function loadReviewPacket() {
  const status = state.status?.workflow?.status || "";
  if (status !== "WAITING_FOR_HUMAN") {
    els.reviewPanel.classList.add("hidden");
    els.reviewPacket.textContent = "";
    els.reviewGate.textContent = "";
    return;
  }
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/current?workdir=${workdirParam()}`);
    els.reviewPanel.classList.remove("hidden");
    els.reviewGate.textContent = data.gateNodeDefinitionId || "";
    els.reviewPacket.textContent = data.packetMarkdown || "(empty review packet)";
  } catch (error) {
    els.reviewPanel.classList.remove("hidden");
    els.reviewGate.textContent = "not available";
    els.reviewPacket.textContent = JSON.stringify(error, null, 2);
  }
}

function renderTimeline(data) {
  const nodes = data.nodes || [];
  els.timelineView.innerHTML = "";
  if (!nodes.length) {
    els.timelineView.textContent = JSON.stringify(data, null, 2);
    return;
  }
  for (const node of nodes) {
    const item = document.createElement("div");
    item.className = "timeline-item";
    item.innerHTML = `
      <strong>${escapeHtml(node.nodeDefinitionId || node.id || "(node)")}</strong>
      <span>${escapeHtml(node.status || "")} ${escapeHtml(node.kind || "")}</span>
      <span>${escapeHtml(node.iterationId || "")}</span>
    `;
    els.timelineView.appendChild(item);
  }
}

function setStatusBadge(status) {
  els.selectedStatus.textContent = status;
  let kind = "muted";
  if (status === "COMPLETED") kind = "ok";
  if (status === "WAITING_FOR_HUMAN" || status === "RUNNING") kind = "warn";
  if (status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY") kind = "bad";
  els.selectedStatus.className = `badge ${kind}`;
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
    return;
  }
  if (status === "WAITING_FOR_HUMAN") {
    els.actionHint.textContent = activeDefinition.includes("next_stage")
      ? "Read the review packet below. Complete finishes this workstream; Approve + Run starts another iteration."
      : "Read the review packet below. Approve + Run continues; Revise + Run sends concrete feedback back into the loop.";
    return;
  }
  if (status === "RUNNING") {
    els.actionHint.textContent = `Workflow is running at ${activeDefinition || activeKind}. Resume advances up to Max steps; Scheduler Tick advances running workstreams in this workdir.`;
    return;
  }
  if (status === "FAILED" || status === "BLOCKED" || status === "WAITING_FOR_RECOVERY") {
    els.actionHint.textContent = "Workflow is stopped. Fix the cause if needed, then Retry marks the active node ready to run again.";
    return;
  }
  els.actionHint.textContent = state.selectedId
    ? "No direct action is available for the current workflow state."
    : "Select a workstream to see available actions.";
}

function setActionVisibility(visible) {
  els.resume.classList.toggle("hidden", !visible.resume);
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

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

$("refresh").addEventListener("click", refreshList);
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
