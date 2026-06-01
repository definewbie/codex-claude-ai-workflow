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
  statusView: $("statusView"),
  timelineView: $("timelineView"),
  responseView: $("responseView"),
  feedback: $("feedback"),
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
  await Promise.all([loadStatus(), loadTimeline()]);
}

async function loadStatus() {
  try {
    const data = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/status?workdir=${workdirParam()}`);
    state.status = data;
    els.selectedTitle.textContent = state.selectedId;
    const workflow = data.workflow || {};
    setStatusBadge(workflow.status || "-");
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
