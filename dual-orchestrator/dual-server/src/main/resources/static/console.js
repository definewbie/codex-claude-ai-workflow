const state = {
  workdir: "",
  selectedProjectId: localStorage.getItem("dual.console.selectedProjectId") || "",
  projects: [],
  selectedId: localStorage.getItem("dual.console.selectedId") || "",
  view: localStorage.getItem("dual.console.view") || "operator",
  workstreams: [],
  status: null,
  overview: null,
  timeline: null,
  review: null,
  recovery: null,
  stream: null,
  streamUrl: "",
  selectedStage: localStorage.getItem("dual.console.selectedStage") || "",
  selectedRound: localStorage.getItem("dual.console.selectedRound") || "",
  selectedFlowActor: "",
  taskPage: 1,
};

const $ = (id) => document.getElementById(id);

const FLOW_LAYOUT = {
  width: 1320,
  height: 390,
  y: 216,
  nodeTop: 158,
  nodeWidth: 220,
  nodeHeight: 132,
  start: { left: 38, width: 46 },
  nodes: {
    claude: 120,
    router: 390,
    codex: 660,
    human: 1060,
  },
  gateway: { left: 955, width: 58 },
};

const els = {
  connection: $("connection"),
  operatorTab: $("operatorTab"),
  managementTab: $("managementTab"),
  operatorView: $("operatorView"),
  managementView: $("managementView"),
  workspacePath: $("workspacePath"),
  projectSelect: $("projectSelect"),
  workspaceState: $("workspaceState"),
  workspaceHint: $("workspaceHint"),
  openWorkspaceBtn: $("openWorkspaceBtn"),
  refreshWorkspaceBtn: $("refreshWorkspaceBtn"),
  taskCount: $("taskCount"),
  taskList: $("taskList"),
  prevTaskPage: $("prevTaskPage"),
  nextTaskPage: $("nextTaskPage"),
  taskPageInfo: $("taskPageInfo"),
  taskTitle: $("taskTitle"),
  taskSub: $("taskSub"),
  taskPill: $("taskPill"),
  stages: $("stages"),
  stageDetail: $("stageDetail"),
  collaborationFlow: $("collaborationFlow"),
  loopBadge: $("loopBadge"),
  primaryCard: $("primaryCard"),
  primaryEyebrow: $("primaryEyebrow"),
  primaryTitle: $("primaryTitle"),
  primaryWhy: $("primaryWhy"),
  primaryNext: $("primaryNext"),
  revisionBox: $("revisionBox"),
  revisionFeedback: $("revisionFeedback"),
  primaryActions: $("primaryActions"),
  tokenSummary: $("tokenSummary"),
  evidence: $("evidence"),
  loopHistory: $("loopHistory"),
  rcaSymptom: $("rcaSymptom"),
  rcaImpact: $("rcaImpact"),
  rcaHypothesis: $("rcaHypothesis"),
  rcaTimeline: $("rcaTimeline"),
  rcaEvidence: $("rcaEvidence"),
  diagnosticCommand: $("diagnosticCommand"),
  copyCommand: $("copyCommand"),
  openOldPanel: $("openOldPanel"),
  rcaVerify: $("rcaVerify"),
  tokenGrid: $("tokenGrid"),
  tokenRunBreakdown: $("tokenRunBreakdown"),
  tokenBudgetBar: $("tokenBudgetBar"),
  rawFacts: $("rawFacts"),
  newTaskBtn: $("newTaskBtn"),
  newTaskModal: $("newTaskModal"),
  closeModal: $("closeModal"),
  closeModalBottom: $("closeModalBottom"),
  newTaskName: $("newTaskName"),
  newTaskRepo: $("newTaskRepo"),
  newTaskDescription: $("newTaskDescription"),
  newTaskLanguage: $("newTaskLanguage"),
  newTaskMode: $("newTaskMode"),
  newTaskLifecycle: $("newTaskLifecycle"),
  newTaskMaxSteps: $("newTaskMaxSteps"),
  newTaskCommand: $("newTaskCommand"),
  copyNewTaskCommand: $("copyNewTaskCommand"),
};

const STAGES = [
  ["intake", "需求", "澄清目标"],
  ["contract", "契约", "锁定边界"],
  ["design", "设计", "架构评审"],
  ["plan", "计划", "实现路线"],
  ["implement", "实现", "代码改动"],
  ["self_test", "自测", "开发证据"],
  ["acceptance", "验收", "外部验证"],
  ["delivery", "交付", "合并/报告"],
];

function workdirParam() {
  localStorage.setItem("dual.console.workdir", state.workdir);
  return encodeURIComponent(state.workdir);
}

function selectedWorkdirParam() {
  const task = selectedTask();
  return encodeURIComponent(task?.effectiveWorkdir || state.workdir);
}

function setConnection(text, kind = "ok") {
  els.connection.className = `conn ${kind}`;
  els.connection.innerHTML = `<i></i>${escapeHtml(text)}`;
}

async function request(path, options = {}) {
  setConnection("同步中", "warn");
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
    setConnection("请求失败", "bad");
    throw body;
  }
  setConnection("实时同步", "ok");
  return body;
}

async function refreshList() {
  renderWorkspace();
  if (!state.workdir) {
    state.workstreams = [];
    state.selectedId = "";
    localStorage.removeItem("dual.console.selectedId");
    els.taskCount.textContent = "0";
    renderTaskList();
    renderEmpty();
    setConnection("请选择仓库", "warn");
    return;
  }
  try {
    const data = await request(`/api/workstreams?workdir=${workdirParam()}`);
    state.workstreams = data.workstreams || [];
    els.taskCount.textContent = String(state.workstreams.length);
    if (!state.selectedId && state.workstreams.length) {
      state.selectedId = state.workstreams[0].workstreamId || "";
      localStorage.setItem("dual.console.selectedId", state.selectedId);
    }
    renderTaskList();
    if (state.selectedId && state.workstreams.some((item) => item.workstreamId === state.selectedId)) {
      await loadSelected();
    } else {
      renderEmpty();
    }
  } catch (error) {
    renderFailure("任务列表加载失败", error.message || JSON.stringify(error));
  }
}

async function loadWorkspaceRegistry() {
  try {
    const data = await request("/api/workspaces");
    state.projects = data.projects || [];
    if (!state.selectedProjectId && data.lastProjectId) {
      state.selectedProjectId = data.lastProjectId;
    }
    const selected = state.projects.find((project) => project.projectId === state.selectedProjectId)
      || state.projects[0]
      || null;
    if (selected) {
      selectProject(selected, false);
    }
    renderWorkspace();
  } catch (error) {
    state.projects = [];
    renderWorkspace();
    renderFailure("项目列表加载失败", error.message || JSON.stringify(error));
  }
}

function renderTaskList() {
  els.taskList.innerHTML = "";
  const pageSize = 10;
  const pageCount = Math.max(1, Math.ceil(state.workstreams.length / pageSize));
  state.taskPage = Math.min(Math.max(1, state.taskPage || 1), pageCount);
  if (els.taskPageInfo) {
    els.taskPageInfo.textContent = `${state.taskPage} / ${pageCount} · 每页 ${pageSize} 个`;
  }
  if (els.prevTaskPage) {
    els.prevTaskPage.disabled = state.taskPage <= 1;
  }
  if (els.nextTaskPage) {
    els.nextTaskPage.disabled = state.taskPage >= pageCount;
  }
  if (!state.workstreams.length) {
    const message = state.workdir
      ? "当前仓库还没有任务。可以新建任务，或确认旁边的 agent-runs 目录是否存在。"
      : "先在上方输入本地 Git 仓库根目录，再打开任务池。";
    els.taskList.innerHTML = `<p class="empty">${escapeHtml(message)}</p>`;
    return;
  }
  const start = (state.taskPage - 1) * pageSize;
  for (const item of state.workstreams.slice(start, start + pageSize)) {
    const status = item.workflowStatus || item.status || "-";
    const button = document.createElement("button");
    button.type = "button";
    button.className = `task ${item.workstreamId === state.selectedId ? "active" : ""}`;
    button.innerHTML = `
      <span class="t">${escapeHtml(item.title || item.workstreamId || "未命名任务")}</span>
      <span class="m"><i class="dot ${statusKind(status)}"></i>${escapeHtml(statusLabel(status))} · ${escapeHtml(timeLabel(item.updatedAt) || shortId(item.workstreamId))}</span>
    `;
    button.addEventListener("click", async () => {
      state.selectedId = item.workstreamId || "";
      localStorage.setItem("dual.console.selectedId", state.selectedId);
      renderTaskList();
      await loadSelected();
    });
    els.taskList.appendChild(button);
  }
}

async function loadSelected() {
  if (!state.selectedId) {
    renderEmpty();
    return;
  }
  try {
    const base = `/api/workstreams/${encodeURIComponent(state.selectedId)}`;
    const [status, overview, timeline] = await Promise.all([
      request(`${base}/status?workdir=${selectedWorkdirParam()}`),
      request(`${base}/overview?workdir=${selectedWorkdirParam()}`),
      request(`${base}/timeline?workdir=${selectedWorkdirParam()}`),
    ]);
    state.status = status;
    state.overview = overview;
    state.timeline = timeline;
    await Promise.all([loadCurrentReview(), loadCurrentRecovery()]);
    renderAll();
    connectProgressStream();
  } catch (error) {
    renderFailure("任务加载失败", error.message || JSON.stringify(error));
  }
}

async function loadCurrentReview() {
  state.review = null;
  const workflowStatus = state.overview?.workflow?.status || state.status?.workflow?.status || "";
  if (workflowStatus !== "WAITING_FOR_HUMAN") {
    return;
  }
  try {
    state.review = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/current?workdir=${selectedWorkdirParam()}`);
  } catch {
    state.review = null;
  }
}

async function loadCurrentRecovery() {
  state.recovery = null;
  const workflowStatus = state.overview?.workflow?.status || state.status?.workflow?.status || "";
  if (!["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED", "RUNNING"].includes(workflowStatus)) {
    return;
  }
  try {
    const recovery = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/recovery/current?workdir=${selectedWorkdirParam()}`);
    state.recovery = recovery.available ? recovery : null;
  } catch {
    state.recovery = null;
  }
}

function connectProgressStream() {
  if (!state.selectedId || !window.EventSource) {
    return;
  }
  const url = `/api/workstreams/${encodeURIComponent(state.selectedId)}/stream?workdir=${selectedWorkdirParam()}&intervalMs=2500`;
  if (state.stream && state.streamUrl === url) {
    return;
  }
  disconnectProgressStream();
  const source = new EventSource(url);
  source.addEventListener("open", () => setConnection("实时同步", "ok"));
  source.addEventListener("overview", (event) => {
    try {
      const overview = JSON.parse(event.data);
      if (overview.workstreamId === state.selectedId) {
        state.overview = overview;
        renderAll();
      }
      setConnection("实时同步", "ok");
    } catch (error) {
      setConnection("同步异常", "bad");
    }
  });
  source.addEventListener("heartbeat", () => setConnection("实时同步", "ok"));
  source.addEventListener("error", () => setConnection("重连中", "warn"));
  state.stream = source;
  state.streamUrl = url;
}

function disconnectProgressStream() {
  if (state.stream) {
    state.stream.close();
    state.stream = null;
    state.streamUrl = "";
  }
}

function renderAll() {
  const task = selectedTask();
  const overview = state.overview || {};
  const workflow = overview.workflow || state.status?.workflow || {};
  const status = workflow.status || task?.workflowStatus || task?.status || "-";
  const title = task?.title || state.selectedId || "未命名任务";
  const updated = workflow.updatedAt || task?.updatedAt || "";

  els.taskTitle.textContent = title;
  els.taskSub.innerHTML = `<b>${escapeHtml(state.selectedId)}</b> · ${escapeHtml(timeLabel(updated) || "时间未知")}`;
  els.taskPill.className = `pill ${statusKind(status)}`;
  els.taskPill.textContent = statusLabel(status);

  renderLifecycle();
  renderPrimary(status);
  renderEvidence(status);
  renderLoopHistory();
  renderRca(status);
}

function renderLifecycle() {
  const overview = state.overview || {};
  const workflow = overview.workflow || {};
  const nodes = timelineNodes();
  const currentStage = stageIdFromNode(workflow.currentNode || "");
  const byStage = nodesByStage(nodes);
  if (!state.selectedStage || !STAGES.some(([id]) => id === state.selectedStage)) {
    state.selectedStage = currentStage || firstNonEmptyStage(byStage) || "intake";
  }
  els.stages.innerHTML = "";
  for (const [id, label, sub] of STAGES) {
    const stageNodes = byStage.get(id) || [];
    const stageState = stageStateFor(id, stageNodes, currentStage);
    const roles = roleCounts(stageNodes);
    const item = document.createElement("button");
    item.type = "button";
    item.className = `stage ${stageState}`;
    item.setAttribute("aria-pressed", id === state.selectedStage ? "true" : "false");
    item.addEventListener("click", () => {
      state.selectedStage = id;
      state.selectedRound = "";
      state.selectedFlowActor = defaultActorForStage(id);
      localStorage.setItem("dual.console.selectedStage", id);
      renderLifecycle();
      renderSelectionPanels();
    });
    const number = STAGES.findIndex(([stageId]) => stageId === id) + 1;
    item.innerHTML = `
      <div class="node">${stageState === "done" ? "✓" : number}</div>
      <div class="lab">${escapeHtml(label)}</div>
      <div class="sub">${escapeHtml(stageSubtitle(id, stageState))}</div>
      <div class="roles">${escapeHtml(roles)}</div>
    `;
    els.stages.appendChild(item);
  }
  els.loopBadge.className = "small-pill warn";
  els.loopBadge.textContent = loopLabel(overview);
  renderStageDetail(byStage.get(state.selectedStage) || [], currentStage);
  renderCollaborationFlow(nodes);
}

function currentWorkflowStatus() {
  const task = selectedTask();
  const overview = state.overview || {};
  const workflow = overview.workflow || state.status?.workflow || {};
  return workflow.status || task?.workflowStatus || task?.status || "-";
}

function renderSelectionPanels() {
  renderEvidence(currentWorkflowStatus());
  renderLoopHistory();
}

function renderPrimary(status) {
  const overview = state.overview || {};
  const why = overview.whyStopped || "Attention read model unavailable. Rebuild projections or refresh the workstream.";
  const next = overview.nextAction || "Refresh after projections are rebuilt.";
  const kind = primaryKind(status);

  els.primaryCard.className = `primary-card ${kind}`;
  els.primaryEyebrow.textContent = primaryEyebrow(status);
  els.primaryTitle.textContent = primaryTitle(status);
  els.primaryWhy.textContent = why;
  els.primaryNext.textContent = stripCommand(next);
  renderRevisionBox(status);
  renderPrimaryActions(status);
}

function renderRevisionBox(status) {
  if (!els.revisionBox) return;
  const show = status === "WAITING_FOR_HUMAN";
  els.revisionBox.hidden = !show;
  if (!show && els.revisionFeedback) {
    els.revisionFeedback.value = "";
  }
}

function renderPrimaryActions(status) {
  els.primaryActions.innerHTML = "";
  const actions = allowedActions(status);
  for (const action of actions) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `btn ${action.kind}`;
    button.textContent = action.label;
    button.addEventListener("click", action.onClick);
    els.primaryActions.appendChild(button);
  }
}

function renderEvidence(status) {
  const context = selectedCollaborationContext();
  if (context) {
    if (els.tokenSummary) {
      els.tokenSummary.innerHTML = tokenCard(tokenSummary());
    }
    els.evidence.innerHTML = selectedContextEvidenceCard(context);
    return;
  }
  const packet = state.review?.packet || {};
  const decision = packet.latestAgentDecision || {};
  const guidance = packet.operatorGuidance || {};
  const contract = packet.taskContract || {};
  const tokens = tokenSummary();

  let left;
  if (status === "WAITING_FOR_HUMAN") {
    left = evidenceCard("当前人闸证据", "基于 review packet 和结构化 sidecar。", [
      ["评审目标", contract.objective || state.review?.gateNodeDefinitionId || "Human review"],
      ["建议动作", guidance.recommendedAction || "-"],
      ["Codex 结论", decision.decision || "-"],
      ["原因", guidance.reason || decision.summary || "-"],
    ], [
      ...(asList(decision.mustFix).map((item) => `Must Fix：${item}`)),
      ...(asList(guidance.suggestedChecks).map((item) => `建议检查：${item}`)),
    ]);
  } else if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    const assessment = state.overview?.recoveryAssessment || state.recovery?.assessment || {};
    left = evidenceCard("恢复摘要", "运行时恢复，不等于业务验收失败。", [
      ["失败类型", assessment.classification || assessment.category || status],
      ["恢复状态", assessment.readiness || "-"],
      ["原因", assessment.reason || state.recovery?.message || "-"],
      ["建议", assessment.nextAction || "-"],
    ], recoveryEvidence());
  } else if (status === "RUNNING") {
    left = evidenceCard("当前执行摘要", "运行中只展示观察信息，避免误触发第二次 execution。", [
      ["当前节点", state.overview?.workflow?.currentNode || "-"],
      ["最近进展", latestTimelineText()],
      ["Agent task", latestAgentTaskText()],
      ["建议", "等待系统到达下一个人闸或恢复状态"],
    ], ["运行中不展示 approve/retry 作为主动作。"]);
  } else if (status === "COMPLETED") {
    left = deliveryEvidenceCard();
  } else {
    left = evidenceCard("当前状态", "尚无可操作状态。", [["状态", status]], []);
  }

  if (els.tokenSummary) {
    els.tokenSummary.innerHTML = tokenCard(tokens);
  }
  els.evidence.innerHTML = left;
}

function renderLoopHistory() {
  const stageNodes = selectedStageNodes();
  const rounds = collaborationRounds(stageNodes);
  const items = rounds.length ? rounds.map((round, index) => {
    const selected = round.id === state.selectedRound;
    return {
      round: `第 ${index + 1} 次`,
      title: selected ? `当前查看：${round.title}` : round.title,
      detail: round.detail,
      pill: round.decision || round.pill,
      kind: selected ? "info" : round.kind === "pass" ? "ok" : round.kind === "reject" || round.kind === "recovery" ? "bad" : "warn",
    };
  }) : [];
  if (!items.length) {
    const [, stageLabel] = STAGES.find(([id]) => id === state.selectedStage) || ["", "当前"];
    items.push({
      round: "当前",
      title: `${stageLabel}阶段暂无轮次证据`,
      detail: "请选择上方已有证据的流水线阶段；这里只展示该阶段内的协作轮次、修复和打回历史。",
      pill: "EMPTY",
      kind: "info",
    });
  }
  els.loopHistory.innerHTML = items.map(loopItem).join("");
}

function renderCollaborationFlow(nodes) {
  if (!els.collaborationFlow) return;
  const selectedStage = state.selectedStage || firstNonEmptyStage(nodesByStage(nodes)) || "intake";
  const stageNodes = nodes.filter((node) => stageOfNode(node) === selectedStage);
  const rounds = collaborationRounds(stageNodes);
  const [, stageLabel, stageSub] = STAGES.find(([id]) => id === selectedStage) || ["", "阶段", ""];
  if (!rounds.length) {
    els.collaborationFlow.innerHTML = `
      <div class="flow-head">
        <div>
          <h3>双 Agent 协作流</h3>
          <div class="cap">${escapeHtml(stageLabel)}阶段还没有 timeline 节点。上方流水线概览不是线性进度条，而是阶段覆盖情况。</div>
        </div>
      </div>
      <div class="flow-empty">暂无可展示的 timeline 节点。</div>
    `;
    return;
  }
  if (!state.selectedRound || !rounds.some((round) => round.id === state.selectedRound)) {
    state.selectedRound = rounds.at(-1).id;
  }
  const selected = rounds.find((round) => round.id === state.selectedRound) || rounds.at(-1);
  const activeClass = selected.kind === "reject" ? "reject" : selected.kind === "pass" ? "pass" : selected.kind === "recovery" ? "recovery" : "revise";
  const showLoop = ["reject", "revise", "recovery"].includes(selected.kind);
  const selectedActor = state.selectedFlowActor || "stage";
  const selectedDetail = flowDetail(selected, selectedActor, stageNodes, stageLabel, stageSub);
  renderStageDetail(stageNodes, stageIdFromNode(state.overview?.workflow?.currentNode || ""));
  els.collaborationFlow.innerHTML = `
    <div class="flow-head">
      <div>
        <h3>双 Agent 协作流 · ${escapeHtml(stageLabel)}</h3>
        <div class="cap">Claude Code 负责产出，Codex 负责审查；人负责关卡确认；Orchestrator 是证据包和规则路由轨道，不是第三个创作 Agent。当前阶段共 ${rounds.length} 次协作，正在查看 ${escapeHtml(selected.shortLabel)}。</div>
      </div>
      <div class="round-tabs" role="tablist" aria-label="迭代轮次">
        ${rounds.map((round) => `
          <button type="button" class="${round.id === selected.id ? "active" : ""} ${round.kind}" data-round-id="${escapeHtml(round.id)}">
            ${escapeHtml(round.shortLabel)}
          </button>
        `).join("")}
      </div>
    </div>
    ${flowCanvas(selected, selectedActor, showLoop, activeClass)}
    <div class="flow-inspector">
      ${flowInspector(selectedDetail)}
    </div>
  `;
  els.collaborationFlow.querySelectorAll("[data-round-id]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedRound = button.getAttribute("data-round-id") || "";
      state.selectedFlowActor = "";
      localStorage.setItem("dual.console.selectedRound", state.selectedRound);
      renderCollaborationFlow(nodes);
      renderSelectionPanels();
    });
  });
  els.collaborationFlow.querySelectorAll("[data-flow-actor]").forEach((button) => {
    button.addEventListener("click", () => {
      state.selectedFlowActor = button.getAttribute("data-flow-actor") || "";
      renderCollaborationFlow(nodes);
      renderSelectionPanels();
    });
  });
}

function flowNode(kind, icon, title, role, content, style, selectedActor) {
  return `
    <button type="button" class="flow-node ${kind} ${selectedActor === kind ? "selected" : ""}" style="${style}" data-flow-actor="${escapeHtml(kind)}">
      <div class="flow-node-head">
        <div class="flow-avatar ${kind}">${escapeHtml(icon)}</div>
        <div><strong>${escapeHtml(title)}</strong><small>${escapeHtml(role)}</small></div>
      </div>
      <p>${escapeHtml(content.summary)}</p>
      <span class="flow-chip ${content.kind || kind}">${escapeHtml(content.label)}</span>
    </button>
  `;
}

function flowCanvas(selected, selectedActor, showLoop, activeClass) {
  const layout = FLOW_LAYOUT;
  return `
    <div class="flow-canvas ${activeClass}" style="--flow-width:${layout.width}px;--flow-height:${layout.height}px;--flow-node-width:${layout.nodeWidth}px">
      ${flowSvg(selected, showLoop)}
      <div class="flow-start" style="${flowBoxStyle(layout.start.left, layout.y - layout.start.width / 2)}">需<span>开始</span></div>
      ${flowNode("claude", "C", "Claude Code", "产出 / 修复", selected.claude, flowBoxStyle(layout.nodes.claude, layout.nodeTop), selectedActor)}
      ${flowNode("router", "R", "证据包 / 规则路由", "Orchestrator 轨道", selected.orchestrator, flowBoxStyle(layout.nodes.router, layout.nodeTop), selectedActor)}
      ${flowNode("codex", "X", "Codex", "审查 / 验收", selected.codex, flowBoxStyle(layout.nodes.codex, layout.nodeTop), selectedActor)}
      <div class="flow-gateway ${selected.kind}" style="${flowBoxStyle(layout.gateway.left, layout.y - layout.gateway.width / 2)}">
        <span>通过?</span>
      </div>
      ${flowNode("human", "H", "Human Gate", "人工拍板", selected.human, flowBoxStyle(layout.nodes.human, layout.nodeTop), selectedActor)}
      <div class="flow-flag ${selected.kind}" style="${flowBoxStyle(layout.nodes.router + 100, 40)}">${escapeHtml(selected.title)} · ${escapeHtml(selected.decision || selected.pill)}</div>
    </div>
  `;
}

function flowSvg(selected, showLoop) {
  const layout = FLOW_LAYOUT;
  const right = (key) => layout.nodes[key] + layout.nodeWidth;
  const line = (from, to, klass = "done", marker = "flowArrowOk") =>
    `<path class="flow-edge ${klass}" d="M${from},${layout.y} L${to},${layout.y}" marker-end="url(#${marker})"></path>`;
  const passClass = selected.kind === "pass" ? "done" : "muted";
  const passMarker = selected.kind === "pass" ? "flowArrowOk" : "flowArrow";
  const loopPath = `M${layout.gateway.left + layout.gateway.width / 2},${layout.y - 32} C${layout.nodes.codex + 220},64 ${layout.nodes.router + 80},58 ${layout.nodes.claude + 88},148`;
  return `
    <svg class="flow-svg" viewBox="0 0 ${layout.width} ${layout.height}" preserveAspectRatio="xMinYMin meet" aria-hidden="true">
      <defs>
        <marker id="flowArrow" markerWidth="9" markerHeight="9" refX="6" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" fill="#b9c4d5"></path>
        </marker>
        <marker id="flowArrowOk" markerWidth="9" markerHeight="9" refX="6" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" fill="#88d49f"></path>
        </marker>
        <marker id="flowArrowBad" markerWidth="9" markerHeight="9" refX="6" refY="3" orient="auto">
          <path d="M0,0 L6,3 L0,6 Z" fill="#ef8d87"></path>
        </marker>
      </defs>
      ${line(layout.start.left + layout.start.width, layout.nodes.claude)}
      ${line(right("claude"), layout.nodes.router)}
      ${line(right("router"), layout.nodes.codex)}
      ${line(right("codex"), layout.gateway.left, passClass, passMarker)}
      ${line(layout.gateway.left + layout.gateway.width, layout.nodes.human, passClass, passMarker)}
      <path class="flow-edge ${showLoop ? "reject" : "muted"}" d="${loopPath}" marker-end="url(#flowArrowBad)"></path>
      <text x="${layout.gateway.left + layout.gateway.width + 10}" y="${layout.y - 20}" class="flow-label ok">通过</text>
      <text x="${layout.nodes.router + 210}" y="66" class="flow-label bad">否 · 打回修复</text>
    </svg>
  `;
}

function flowBoxStyle(left, top) {
  return `left:${left}px;top:${top}px`;
}

function flowFact(label, content) {
  return `
    <div class="flow-fact">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(content.summary)}</strong>
    </div>
  `;
}

function flowInspector(detail) {
  const rows = detail.facts || [];
  const primary = rows[0]?.[1]?.summary || detail.detail || "-";
  const conclusion = rows[1]?.[1]?.summary || rows[0]?.[1]?.label || "-";
  const boundary = rows[2]?.[1]?.summary || detail.detail || "-";
  const next = rows[3]?.[1]?.summary || detail.detail || "-";
  return `
    <div class="flow-inspector-doc">
      <h3>${escapeHtml(detail.title)}</h3>
      <p class="flow-inspector-role">${escapeHtml(flowInspectorRole(detail.title))}</p>
      ${flowInspectorSection("本轮发生了什么", primary)}
      ${flowInspectorSection("本轮结论", conclusion)}
      ${flowInspectorSection("边界说明", boundary)}
      ${flowInspectorSection("为什么进入下一步", next)}
    </div>
  `;
}

function flowInspectorSection(title, content) {
  return `
    <section>
      <h4>${escapeHtml(title)}</h4>
      <p>${escapeHtml(content || "-")}</p>
    </section>
  `;
}

function flowInspectorRole(title) {
  if (title.includes("Claude")) return "产出/修复者";
  if (title.includes("Codex")) return "审查/验收者";
  if (title.includes("Human")) return "人工拍板";
  if (title.includes("Orchestrator")) return "证据包 / 规则路由";
  return "阶段视图";
}

function collaborationRounds(nodes) {
  const byIteration = new Map();
  for (const node of nodes) {
    const id = node.iterationId || "iter-unknown";
    if (!byIteration.has(id)) byIteration.set(id, []);
    byIteration.get(id).push(node);
  }
  return [...byIteration.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([iterationId, items], index) => collaborationRound(iterationId, items, index));
}

function collaborationRound(iterationId, items, index) {
  const sorted = [...items].sort(compareNodes);
  const decisions = sorted
    .map((node) => node.result?.decision || node.result?.stage_outcome || node.result?.category || "")
    .filter(Boolean);
  const decision = decisions.find((value) => ["REJECT", "NEEDS_REVISION", "ACCEPT_WITH_NOTES", "ACCEPT", "PASS", "BLOCKED"].includes(value)) || decisions[0] || "";
  const failed = sorted.some((node) => ["FAILED", "BLOCKED"].includes(node.status));
  const rejected = ["REJECT", "NEEDS_REPAIR", "REJECT_CONTRACT_GAP"].includes(decision);
  const revision = ["NEEDS_REVISION", "BLOCKED"].includes(decision);
  const accepted = ["ACCEPT", "ACCEPT_WITH_NOTES", "PASS", "APPROVE"].includes(decision);
  const kind = failed ? "recovery" : rejected ? "reject" : revision ? "revise" : accepted ? "pass" : "base";
  const roundNumber = index + 1;
  const title = kind === "reject"
    ? `第 ${roundNumber} 次：Codex 拒收`
    : kind === "revise"
      ? `第 ${roundNumber} 次：Codex 要求修订`
      : kind === "recovery"
        ? `第 ${roundNumber} 次：运行时恢复`
        : kind === "pass"
          ? `第 ${roundNumber} 次：审查通过`
          : `第 ${roundNumber} 次：执行中`;
  return {
    id: iterationId,
    ordinal: roundNumber,
    shortLabel: `第 ${roundNumber} 次 · ${decision || (kind === "base" ? "BASE" : kind.toUpperCase())}`,
    title,
    kind,
    pill: decision || kind.toUpperCase(),
    decision,
    detail: collaborationDetail(kind, decision, sorted),
    claude: actorFlowContent("claude", sorted),
    orchestrator: actorFlowContent("orchestrator", sorted),
    codex: actorFlowContent("codex", sorted),
    human: actorFlowContent("human", sorted),
    nodes: sorted,
  };
}

function roundNumberFromIteration(iterationId, index) {
  const match = String(iterationId || "").match(/(\d+)/);
  return match ? String(Number(match[1])) : String(index + 1);
}

function collaborationDetail(kind, decision, nodes) {
  const latest = nodes.at(-1);
  if (kind === "reject") return "这是语义修复轮：Codex 不接受当前产出，Orchestrator 把反馈打回给 Claude Code。";
  if (kind === "revise") return "这是修订轮：需求、设计或计划需要补充，尚未进入最终交付。";
  if (kind === "recovery") return "这是运行时恢复：外部执行失败，不等于业务验收失败。";
  if (kind === "pass") return "这是成功轮：Codex 接受产出，Orchestrator 可以进入收口或交付。";
  return latest?.displayName || latest?.nodeDefinitionId || decision || "当前轮次暂无明确结论。";
}

function actorFlowContent(actor, nodes) {
  const owned = nodes.filter((node) => actorForNode(node) === actor);
  const latest = owned.at(-1);
  if (!latest) {
    return {
      nodes: [],
      summary: actor === "claude"
        ? "本轮没有 Claude Code 动作。"
        : actor === "codex"
          ? "本轮没有 Codex 审查动作。"
          : actor === "human"
            ? "本轮没有人工关卡。"
            : "等待平台装配证据包和路由结果。",
      label: "无动作",
      kind: "muted",
    };
  }
  const result = latest.result || {};
  const decision = result.decision || result.stage_outcome || result.category || "";
  const summary = result.summary || result.reasoning || result.suggested_action || latest.displayName || latest.nodeDefinitionId || "-";
  return {
    nodes: owned,
    summary,
    label: decision || statusLabel(latest.status) || latest.kind || "artifact",
    kind: decision === "REJECT" ? "bad" : ["ACCEPT", "ACCEPT_WITH_NOTES", "PASS"].includes(decision) ? "ok" : actor,
  };
}

function flowDetail(round, actor, stageNodes, stageLabel, stageSub) {
  if (actor && actor !== "stage") {
    const actorKey = actor === "router" ? "orchestrator" : actor;
    const content = round[actorKey] || { nodes: [], summary: "暂无数据", label: "无动作" };
    const nodes = content.nodes || [];
    return {
      title: `${flowActorTitle(actor)} · ${round.title}`,
      detail: nodes.length
        ? `本轮共有 ${nodes.length} 个相关节点。点击其他流程节点可切换证据视角。`
        : "本轮没有这个角色的动作；这通常说明该阶段只包含部分协作环节。",
      facts: nodes.length ? nodes.slice(-3).map((node) => [node.displayName || node.nodeDefinitionId || "node", flowNodeFact(node)]) : [[flowActorTitle(actor), content]],
    };
  }
  const nodes = stageNodes || [];
  return {
    title: `${stageLabel}阶段 · 流水线概览`,
    detail: `${stageSub}。这不是线性进度，而是该阶段在多个 iteration 中留下的执行、审查、人审和路由证据。`,
    facts: [
      ["阶段节点", { summary: `${nodes.length} 个 timeline 节点`, label: roleCounts(nodes), kind: "muted" }],
      ["当前轮次", { summary: round.detail, label: round.decision || round.pill, kind: round.kind }],
      ["最新节点", { summary: latestIterationNode(nodes) || "暂无节点", label: "latest", kind: "muted" }],
    ],
  };
}

function selectedStageNodes() {
  const stageId = state.selectedStage || firstNonEmptyStage(nodesByStage(timelineNodes())) || "intake";
  return timelineNodes().filter((node) => stageOfNode(node) === stageId);
}

function selectedCollaborationContext() {
  if (!state.selectedId || !state.selectedStage) return null;
  const stageNodes = selectedStageNodes();
  const rounds = collaborationRounds(stageNodes);
  if (!rounds.length) return null;
  const round = rounds.find((item) => item.id === state.selectedRound) || rounds.at(-1);
  const [stageId, stageLabel, stageSub] = STAGES.find(([id]) => id === state.selectedStage) || ["", "阶段", ""];
  const actor = state.selectedFlowActor || "stage";
  return {
    stageId,
    stageLabel,
    stageSub,
    stageNodes,
    rounds,
    round,
    actor,
    detail: flowDetail(round, actor, stageNodes, stageLabel, stageSub),
  };
}

function selectedContextEvidenceCard(context) {
  const actorText = context.actor === "stage" ? "阶段整体" : flowActorTitle(context.actor);
  const latestNode = latestIterationNode(context.stageNodes) || "-";
  const selectedFacts = context.detail.facts.slice(0, 4);
  const rows = [
    ["阶段", `${context.stageLabel}阶段`],
    ["查看对象", actorText],
    ["当前轮次", context.round.shortLabel],
    ["阶段结论", context.round.decision || context.round.pill || "-"],
    ["证据分布", roleCounts(context.stageNodes)],
    ["最新节点", latestNode],
  ];
  const bullets = [
    context.actor === "stage"
      ? `本阶段共 ${context.rounds.length} 次协作；绿色只表示已有完成证据，不代表流程只走过一次。`
      : context.detail.detail,
    ...selectedFacts.map(([label, content]) => `${label}：${content.summary}`),
  ];
  return evidenceCard(`${context.stageLabel}阶段证据`, "回答：这个阶段为什么算推进过，以及当前选中的轮次/节点有什么证据。", rows, bullets);
}

function flowActorTitle(actor) {
  return {
    claude: "Claude Code 产出",
    codex: "Codex 审查",
    router: "Orchestrator 证据包 / 规则路由",
    orchestrator: "Orchestrator 证据包 / 规则路由",
    human: "Human Gate 人工拍板",
  }[actor] || actor;
}

function flowNodeFact(node) {
  const result = node.result || {};
  const decision = result.decision || result.stage_outcome || result.category || "";
  const summary = result.summary || result.reasoning || result.suggested_action || result.reason || node.status || "-";
  return {
    summary,
    label: `${statusLabel(node.status)}${decision ? ` · ${decision}` : ""}`,
    kind: decision === "REJECT" ? "bad" : ["ACCEPT", "ACCEPT_WITH_NOTES", "PASS"].includes(decision) ? "ok" : actorForNode(node),
  };
}

function renderRca(status) {
  const assessment = state.overview?.recoveryAssessment || state.recovery?.assessment || {};
  const command = commandForStatus(status, assessment);
  const symptom = rcaSymptom(status, assessment);
  const impact = rcaImpact(status, assessment);
  const hypothesis = rcaHypothesis(status, assessment);
  els.rcaSymptom.textContent = symptom;
  els.rcaImpact.textContent = impact;
  els.rcaHypothesis.textContent = hypothesis;
  els.diagnosticCommand.textContent = command;
  renderRcaTimeline();
  renderList(els.rcaEvidence, rcaEvidence(status, assessment));
  renderList(els.rcaVerify, rcaVerification(status, assessment));
  renderTokens();
  els.rawFacts.textContent = JSON.stringify({
    workstreamId: state.selectedId,
    status,
    workflow: state.overview?.workflow || {},
    recoveryAssessment: assessment,
    acceptance: state.overview?.acceptance || {},
    goalSatisfaction: state.overview?.goalSatisfaction || {},
    deliveryMerge: state.overview?.deliveryMerge || {},
    deliveryView: state.overview?.deliveryView || {},
  }, null, 2);
}

function renderRcaTimeline() {
  const nodes = timelineNodes().slice(-8);
  if (!nodes.length) {
    els.rcaTimeline.innerHTML = `<p class="empty">暂无 timeline projection。</p>`;
    return;
  }
  els.rcaTimeline.innerHTML = nodes.map((node) => loopItem({
    round: timeLabel(node.startedAt || node.finishedAt || node.updatedAt || node.createdAt) || "-",
    title: node.displayName || node.nodeDefinitionId || node.nodeInstanceId || "node",
    detail: `${node.kind || "-"} · ${node.status || "-"}`,
    pill: stageIdFromNode(node.nodeDefinitionId || node.displayName || "") || "node",
    kind: node.status === "FAILED" ? "bad" : node.status === "COMPLETED" ? "ok" : "info",
  })).join("");
}

function renderTokens() {
  const tokens = tokenSummary();
  els.tokenGrid.innerHTML = [
    tokenMetric("输入", tokens.prompt, "prompt"),
    tokenMetric("输出", tokens.completion, "completion"),
    tokenMetric("预算", tokens.budgetLabel, "context budget"),
    tokenMetric("成本", tokens.cost, "estimate"),
  ].join("");
  if (els.tokenRunBreakdown) {
    els.tokenRunBreakdown.innerHTML = tokenRunBreakdown(tokens);
  }
  els.tokenBudgetBar.style.width = tokens.budgetPercent + "%";
}

function tokenSummary() {
  const metrics = state.overview?.metrics || {};
  const tokenMetrics = metrics.tokenMetrics || {};
  const total = tokenMetrics.total || {};
  const runs = state.overview?.latestAgentRuns || [];
  const prompt = numberValue(total.inputTokens)
    || firstNumber(metrics, ["promptTokens", "inputTokens", "totalPromptTokens", "agentPromptTokens"])
    || sumByKeys(runs, ["promptTokens", "inputTokens", "prompt_tokens", "input_tokens"]);
  const completion = numberValue(total.outputTokens)
    || firstNumber(metrics, ["completionTokens", "outputTokens", "totalCompletionTokens", "agentCompletionTokens"])
    || sumByKeys(runs, ["completionTokens", "outputTokens", "completion_tokens", "output_tokens"]);
  const budget = firstNumber(metrics, ["tokenBudget", "contextTokenBudget"]) || 0;
  const used = prompt + completion;
  const budgetPercent = budget > 0 ? Math.min(100, Math.round((used / budget) * 100)) : 0;
  const costByCurrency = total.costByCurrency || {};
  const actualCost = Object.entries(costByCurrency).map(([currency, amount]) => `${amount} ${currency}`).join(", ");
  const cost = actualCost || firstNumber(metrics, ["estimatedCost", "cost", "totalCost"]) || 0;
  const suggestedBudget = firstNumber(metrics, ["tokenBudget", "contextTokenBudget", "suggestedTokenBudget"]) || 250000;
  const effectiveBudget = budget || suggestedBudget;
  const effectiveBudgetPercent = effectiveBudget > 0 ? Math.min(100, Math.round((used / effectiveBudget) * 100)) : 0;
  return {
    prompt: prompt ? formatNumber(prompt) : "-",
    completion: completion ? formatNumber(completion) : "-",
    totalTokens: used,
    totalLabel: used ? formatNumber(used) : "-",
    budgetLabel: `${effectiveBudgetPercent}%`,
    budgetPercent: effectiveBudgetPercent,
    budgetTokens: effectiveBudget,
    budgetSource: budget ? "配置预算" : "建议预算",
    budgetStatus: budgetAssessment(used, effectiveBudget),
    cost: cost ? String(cost) : "未采集价格",
    costStatus: actualCost ? "实际成本" : "仅 token 估算",
    measuredRunCount: numberValue(tokenMetrics.measuredRunCount),
    runCount: numberValue(tokenMetrics.runCount),
    estimatedRunCount: numberValue(tokenMetrics.estimatedRunCount),
    sessionSnapshotRunCount: numberValue(tokenMetrics.sessionSnapshotRunCount),
    runs: Array.isArray(tokenMetrics.runs) ? tokenMetrics.runs : runs,
    topContextSections: Array.isArray(tokenMetrics.topContextSections) ? tokenMetrics.topContextSections : [],
    contextPackages: Array.isArray(tokenMetrics.contextPackages) ? tokenMetrics.contextPackages : [],
    toolMetrics: metrics.toolMetrics || {},
  };
}

function evidenceCard(title, cap, rows, bullets) {
  return `
    <div class="card">
      <h3>${escapeHtml(title)}</h3>
      <div class="cap">${escapeHtml(cap)}</div>
      ${rows.map(([key, value]) => `
        <div class="kv"><span class="k">${escapeHtml(key)}</span><span class="v">${escapeHtml(value || "-")}</span></div>
      `).join("")}
      ${bullets.length ? `<ul class="clean">${bullets.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>` : ""}
    </div>
  `;
}

function tokenCard(tokens) {
  return `
    <div class="card">
      <h3>Token / 预算评估</h3>
      <div class="cap">只把 token 当作运行风险信号；没有真实价格模型时，不展示确定成本。</div>
      <div class="token-grid">
        ${tokenMetric("总量", tokens.totalLabel, tokens.budgetStatus)}
        ${tokenMetric("预算", tokens.budgetLabel, `${tokens.budgetSource} ${formatNumber(tokens.budgetTokens)}`)}
        ${tokenMetric("实测步骤", String(tokens.measuredRunCount || 0), "provider run usage")}
        ${tokenMetric("估算步骤", String(tokens.estimatedRunCount || 0), "artifact estimate")}
      </div>
      ${tokens.sessionSnapshotRunCount ? `<div class="cap">另有 ${tokens.sessionSnapshotRunCount} 个步骤只拿到 session/context snapshot，不计入总 token。</div>` : ""}
      <div class="budget"><i style="width:${tokens.budgetPercent}%"></i></div>
      ${tokenHotspots(tokens)}
      ${toolGovernanceHotspots(tokens)}
    </div>
  `;
}

function tokenHotspots(tokens) {
  const sections = (tokens.topContextSections || []).slice(0, 4);
  if (!sections.length) {
    return `<div class="cap">暂无 context section 级别的 token breakdown；新任务完成 context package 后会自动出现。</div>`;
  }
  return `
    <div class="token-runs">
      <div class="cap">Context 最大贡献项：用于判断 token 是否被历史输出、输入材料、Git 证据或契约撑大。</div>
      ${sections.map((section) => `
        <div class="token-row">
          <span>${escapeHtml(section.section || "-")}</span>
          <span>${escapeHtml(section.targetNode || "-")} · ${escapeHtml(section.profile || "-")}</span>
          <strong>${formatNumber(numberValue(section.renderedTokens))}</strong>
        </div>
      `).join("")}
    </div>
  `;
}

function toolGovernanceHotspots(tokens) {
  const toolMetrics = tokens.toolMetrics || {};
  const repeated = Array.isArray(toolMetrics.repeatedReadFiles)
    ? toolMetrics.repeatedReadFiles.slice(0, 4)
    : [];
  const toolCalls = numberValue(toolMetrics.toolCallCount);
  const reads = numberValue(toolMetrics.readCount);
  const bash = numberValue(toolMetrics.bashCount);
  if (!toolCalls && !repeated.length) {
    return `<div class="cap">暂无工具调用治理数据；新 Agent run 完成后会自动投影 repeated read 和 evidence 命中。</div>`;
  }
  return `
    <div class="token-runs">
      <div class="cap">工具调用治理：tool calls ${formatNumber(toolCalls)} · reads ${formatNumber(reads)} · bash ${formatNumber(bash)}。重复读取用于判断 Agent 是否在反复探索同一批文件。</div>
      ${repeated.length ? repeated.map((item) => `
        <div class="token-row">
          <span>${escapeHtml(item.path || "-")}</span>
          <span>${escapeHtml((item.evidenceKnown ? "已有证据" : "未命中证据") + " · " + arrayValue(item.nodes).slice(0, 2).join(", "))}</span>
          <strong>x${formatNumber(numberValue(item.count))}</strong>
        </div>
      `).join("") : `<div class="empty">暂无重复读取热点。</div>`}
    </div>
  `;
}

function arrayValue(value) {
  return Array.isArray(value) ? value : [];
}

function tokenRunBreakdown(tokens) {
  const rows = (tokens.runs || []).slice(-8).map((run) => {
    const usage = run.usage || {};
    const breakdown = run.breakdown || {};
    const total = numberValue(usage.totalTokens);
    const contextUsed = numberValue(usage.contextUsedTokens);
    const cachedRead = numberValue(usage.cachedReadTokens);
    const source = usage.source || (run.measured ? "acpx_stream" : run.estimated ? "artifact_estimate" : "unknown");
    const measurementType = usage.measurementType || source;
    const attribution = usage.attribution || (run.estimated ? "ARTIFACT_ESTIMATE" : "");
    const contextTokens = numberValue(breakdown.contextTokens);
    const transcriptTokens = numberValue(breakdown.transcriptTokens);
    const overheadTokens = numberValue(breakdown.promptOverheadTokens) + numberValue(breakdown.adapterOverheadTokens);
    const detailParts = [
      escapeHtml(run.agent || "-"),
      run.sessionScope ? `scope=${escapeHtml(run.sessionScope)}` : "",
      run.stage ? `stage=${escapeHtml(run.stage)}` : "",
      run.semanticIteration ? `iter=${escapeHtml(run.semanticIteration)}` : "",
      escapeHtml(measurementType),
      attribution ? `attr=${escapeHtml(attribution)}` : "",
      `cachedRead ${cachedRead ? formatNumber(cachedRead) : "-"}`,
      `context ${contextTokens ? formatNumber(contextTokens) : "-"}`,
      `transcript ${transcriptTokens ? formatNumber(transcriptTokens) : "-"}`,
      `overhead ${overheadTokens ? formatNumber(overheadTokens) : "-"}`,
    ].filter(Boolean);
    const value = total || contextUsed;
    const suffix = total ? "" : contextUsed ? " snapshot" : "";
    return `
      <div class="token-row">
        <span>${escapeHtml(run.nodeId || run.nodeDefinitionId || "-")}</span>
        <span>${detailParts.join(" · ")}</span>
        <strong>${value ? formatNumber(value) + suffix : "-"}</strong>
      </div>
    `;
  }).join("");
  return `
    <div class="token-runs">
      <div class="cap">按步骤统计：${tokens.measuredRunCount || 0} 个 provider run usage，${tokens.sessionSnapshotRunCount || 0} 个 session snapshot（不计入总量），${tokens.estimatedRunCount || 0} 个估算（不计入总量），共 ${tokens.runCount || 0} 个 Agent run。cachedRead 随 run 增长 = session 在滚雪球。展示最近 8 个。</div>
      ${rows || `<div class="empty">暂无逐步 token usage。新 run 完成后会写入 run.json 并投影到 metrics。</div>`}
    </div>
  `;
}

function tokenMetric(label, value, sub) {
  return `
    <div class="token">
      <div class="k">${escapeHtml(label)}</div>
      <div class="v">${escapeHtml(value)}</div>
      <div class="s">${escapeHtml(sub)}</div>
    </div>
  `;
}

function loopItem(item) {
  return `
    <div class="loop-item">
      <strong>${escapeHtml(item.round)}</strong>
      <div>
        <strong>${escapeHtml(item.title)}</strong>
        <span>${escapeHtml(item.detail)}</span>
      </div>
      <span class="small-pill ${item.kind || "info"}">${escapeHtml(item.pill)}</span>
    </div>
  `;
}

function allowedActions(status) {
  if (!state.selectedId) {
    return [];
  }
  if (status === "WAITING_FOR_HUMAN") {
    return [
      { label: "批准并继续", kind: "primary", onClick: approveAndRun },
      { label: "请求修改", kind: "ghost", onClick: reviseAndRun },
    ];
  }
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    return [
      { label: "重试当前节点", kind: "primary", onClick: retryCurrent },
      { label: "查看管理诊断", kind: "ghost", onClick: () => switchView("management") },
    ];
  }
  if (status === "RUNNING") {
    return [
      { label: "刷新状态", kind: "ghost", onClick: loadSelected },
      { label: "查看管理诊断", kind: "subtle", onClick: () => switchView("management") },
    ];
  }
  if (status === "COMPLETED") {
    const delivery = deliveryView();
    const actions = [];
    if (delivery.canMerge) {
      actions.push({ label: "合回原始仓库", kind: "primary", onClick: mergeDelivery });
    } else if (delivery.status === "APPLIED") {
      actions.push({ label: "查看已暂存差异和命令", kind: "primary", onClick: () => switchView("management") });
    } else {
      actions.push({ label: "查看交付差异", kind: "primary", onClick: () => switchView("management") });
    }
    actions.push({ label: "打开旧管理面板", kind: "ghost", onClick: openOldPanel });
    return actions;
  }
  return [{ label: "刷新状态", kind: "ghost", onClick: loadSelected }];
}

async function postAction(path, body) {
  try {
    await request(path, {
      method: "POST",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    await refreshList();
  } catch (error) {
    renderFailure("操作失败", error.message || JSON.stringify(error));
  }
}

function approveAndRun() {
  const note = (els.revisionFeedback?.value || "").trim();
  if (state.review?.packet?.sourceOutcome === "BLOCKED" && !note) {
    renderFailure("缺少人工决定", "当前评审要求人工决定。请填写决定说明后再批准。");
    els.revisionFeedback?.focus();
    return;
  }
  postAction(
    `/api/workstreams/${encodeURIComponent(state.selectedId)}/review/approve?workdir=${selectedWorkdirParam()}&run=true&maxSteps=80`,
    { note }
  );
}

function reviseAndRun() {
  const feedback = (els.revisionFeedback?.value || "").trim();
  if (!feedback) {
    renderFailure("缺少打回说明", "请求修改前，请先在“打回说明”里写清楚需要重新处理的问题。");
    els.revisionFeedback?.focus();
    return;
  }
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/revise?workdir=${selectedWorkdirParam()}&run=true&maxSteps=80`, { feedback });
}

function retryCurrent() {
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/retry?workdir=${selectedWorkdirParam()}`, { note: "retry from operator console" });
}

function mergeDelivery() {
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/delivery/merge?workdir=${selectedWorkdirParam()}`);
}

function commandForStatus(status, assessment) {
  const task = selectedTask();
  const effectiveWorkdir = task?.effectiveWorkdir || state.workdir || ".";
  const base = `--workdir "${effectiveWorkdir}" ${state.selectedId || "<workstream_id>"}`;
  const jar = "java -jar dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar";
  if (!state.selectedId) return "请选择任务。";
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    const probe = assessment.classification ? "claude -p \"hello\"" : "# 如涉及 Agent 故障，先运行短探针";
    return `${probe}\n${jar} retry ${base}\n${jar} run ${base} --max-steps 40`;
  }
  if (status === "WAITING_FOR_HUMAN") {
    return `${jar} review approve ${base} --run --max-steps 80\n# 或：${jar} review revise ${base} --run --max-steps 80`;
  }
  if (status === "RUNNING") {
    return `${jar} trace ${base}`;
  }
  if (status === "COMPLETED") {
    return `${jar} summary ${base}\n${jar} delivery merge --workdir "${effectiveWorkdir}" --target "<original_repo>" ${state.selectedId}`;
  }
  return `${jar} status ${base}`;
}

function renderEmpty() {
  els.taskTitle.textContent = "请选择任务";
  els.taskSub.textContent = state.workdir
    ? "当前没有选中的任务。"
    : "先打开一个本地 Git 仓库，任务列表会显示该仓库的 workstream。";
  els.taskPill.className = "pill muted";
  els.taskPill.textContent = "未选择";
  els.primaryTitle.textContent = "选择任务后查看当前状态";
  els.primaryWhy.textContent = "任务视图只展示完成判断所需的信息。";
  els.primaryNext.textContent = state.workdir ? "从左侧选择任务，或新建任务。" : "输入本地 Git 仓库根目录并点击“打开任务池”。";
  renderRevisionBox(null);
  els.primaryActions.innerHTML = "";
  if (els.collaborationFlow) {
    els.collaborationFlow.innerHTML = "";
  }
  if (els.stageDetail) {
    els.stageDetail.innerHTML = "";
  }
  if (els.tokenSummary) {
    els.tokenSummary.innerHTML = "";
  }
  els.evidence.innerHTML = "";
  els.loopHistory.innerHTML = "";
  els.rcaTimeline.innerHTML = "";
  els.rawFacts.textContent = "{}";
}

function renderFailure(title, message) {
  els.primaryCard.className = "primary-card recovery";
  els.primaryEyebrow.textContent = "加载失败";
  els.primaryTitle.textContent = title;
  els.primaryWhy.textContent = message;
  els.primaryNext.textContent = "检查 server 是否运行，或打开旧管理面板排查。";
}

function switchView(view) {
  state.view = view;
  localStorage.setItem("dual.console.view", view);
  els.operatorTab.classList.toggle("active", view === "operator");
  els.managementTab.classList.toggle("active", view === "management");
  els.operatorView.classList.toggle("hidden", view !== "operator");
  els.managementView.classList.toggle("active", view === "management");
}

function openOldPanel() {
  window.location.href = "/mgt/index.html";
}

function renderWorkspace() {
  renderProjectSelect();
  if (els.workspacePath && els.workspacePath.value !== state.workdir) {
    els.workspacePath.value = state.workdir;
  }
  const hasWorkdir = Boolean(state.workdir);
  els.workspaceState.textContent = hasWorkdir ? "已连接" : "未设置";
  els.workspaceState.className = `small-pill ${hasWorkdir ? "ok" : "muted"}`;
  const project = selectedProject();
  els.workspaceHint.innerHTML = hasWorkdir
    ? `当前读取：<code>${escapeHtml(project?.name || state.workdir)}</code>`
    : `输入本地 repo 根目录后，会自动发现旁边的 <code>&lt;repo-name&gt;-agent-runs</code>。`;
}

function renderProjectSelect() {
  if (!els.projectSelect) {
    return;
  }
  if (!state.projects.length) {
    els.projectSelect.innerHTML = `<option value="">暂无已注册项目</option>`;
    return;
  }
  els.projectSelect.innerHTML = state.projects.map((project) => {
    const suffix = project.workstreamCount === undefined ? "" : ` · ${project.workstreamCount} 个任务`;
    return `<option value="${escapeHtml(project.projectId)}">${escapeHtml(project.name || project.repoRoot)}${escapeHtml(suffix)}</option>`;
  }).join("");
  els.projectSelect.value = state.selectedProjectId || state.projects[0]?.projectId || "";
}

function selectedProject() {
  return state.projects.find((project) => project.projectId === state.selectedProjectId) || null;
}

function selectProject(project, persist = true) {
  state.selectedProjectId = project?.projectId || "";
  state.workdir = project?.repoRoot || "";
  if (persist) {
    localStorage.setItem("dual.console.selectedProjectId", state.selectedProjectId);
    localStorage.setItem("dual.console.workdir", state.workdir);
  }
}

async function applyWorkspace() {
  try {
    const nextWorkdir = (els.workspacePath?.value || "").trim();
    if (!nextWorkdir) {
      state.workdir = "";
      state.selectedProjectId = "";
      localStorage.removeItem("dual.console.selectedProjectId");
      localStorage.removeItem("dual.console.workdir");
      await refreshList();
      return;
    }
    disconnectProgressStream();
    const data = await request("/api/workspaces/register", {
      method: "POST",
      body: JSON.stringify({ repoRoot: nextWorkdir }),
    });
    state.projects = data.projects || [];
    const project = state.projects.find((item) => item.repoRoot === nextWorkdir)
      || state.projects.find((item) => item.projectId === data.lastProjectId)
      || state.projects[0]
      || { projectId: "", repoRoot: nextWorkdir };
    selectProject(project);
    state.selectedId = "";
    localStorage.removeItem("dual.console.selectedId");
    await refreshList();
  } catch (error) {
    renderFailure("本地仓库注册失败", error.message || JSON.stringify(error));
  }
}

function openModal() {
  els.newTaskRepo.value = state.workdir || els.newTaskRepo.value;
  updateNewTaskCommand();
  els.newTaskModal.classList.add("active");
  els.newTaskModal.setAttribute("aria-hidden", "false");
}

function closeModal() {
  els.newTaskModal.classList.remove("active");
  els.newTaskModal.setAttribute("aria-hidden", "true");
}

function updateNewTaskCommand() {
  const repo = els.newTaskRepo.value.trim() || "<local_git_repo_path>";
  const language = els.newTaskLanguage.value;
  const lifecycle = els.newTaskLifecycle.value;
  const maxSteps = Number.parseInt(els.newTaskMaxSteps.value, 10) || 80;
  const requirement = els.newTaskDescription.value.trim() || els.newTaskName.value.trim() || "任务描述";
  const variant = lifecycle === "design-only"
    ? "--workflow-variant contract"
    : lifecycle === "terminal-delivery"
      ? "--workflow-variant terminal-delivery"
      : "";
  const mode = els.newTaskMode.value === "in-place" ? "# 原地运行需要 CLI 支持显式 in-place 策略；默认建议 worktree。" : "";
  els.newTaskCommand.textContent = `${mode}
java -jar dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar start \\
  --workdir "${repo}" \\
  --prompt-language ${language} \\
  ${variant} \\
  --run \\
  --max-steps ${maxSteps} \\
  "${escapeShell(requirement)}"`.trim();
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    setConnection("已复制", "ok");
  } catch {
    setConnection("复制失败", "bad");
  }
}

function selectedTask() {
  return state.workstreams.find((item) => item.workstreamId === state.selectedId) || null;
}

function timelineNodes() {
  return [...(state.timeline?.nodes || state.overview?.recentTimeline || [])].sort(compareNodes);
}

function nodesByStage(nodes) {
  const groups = new Map(STAGES.map(([id]) => [id, []]));
  for (const node of nodes) {
    const stage = stageOfNode(node);
    if (groups.has(stage)) {
      groups.get(stage).push(node);
    }
  }
  return groups;
}

function firstNonEmptyStage(groups) {
  for (const [id] of STAGES) {
    if ((groups.get(id) || []).length) return id;
  }
  return "";
}

function stageOfNode(node) {
  const fromDefinition = stageIdFromNode(node?.nodeDefinitionId || node?.displayName || "");
  if (fromDefinition) return fromDefinition;
  const fromField = String(node?.stageId || "").replace(/^stage-/, "");
  if (STAGES.some(([id]) => id === fromField)) return fromField;
  return "";
}

function stageStateFor(stageId, nodes, currentStage) {
  if (stageId === currentStage) return "current";
  if (!nodes.length) return "";
  if (nodes.some((node) => ["FAILED", "BLOCKED"].includes(node.status))) return "recovery";
  if (nodes.every((node) => ["COMPLETED", "SKIPPED"].includes(node.status))) return "done";
  if (nodes.some((node) => ["RUNNING", "WAITING"].includes(node.status))) return "current";
  return "";
}

function roleCounts(nodes) {
  const counts = nodes.reduce((acc, node) => {
    const role = actorForNode(node);
    acc[role] = (acc[role] || 0) + 1;
    return acc;
  }, {});
  const parts = [];
  if (counts.claude) parts.push(`Claude ${counts.claude}`);
  if (counts.codex) parts.push(`Codex ${counts.codex}`);
  if (counts.orchestrator) parts.push(`总控 ${counts.orchestrator}`);
  if (counts.human) parts.push(`人审 ${counts.human}`);
  return parts.join(" · ") || "待执行";
}

function actorForNode(node) {
  const handler = String(node?.handlerType || "").toLowerCase();
  const value = `${node?.nodeDefinitionId || ""} ${node?.result?.agent || ""}`.toLowerCase();
  if (node?.kind === "GATE" || handler.includes("human")) return "human";
  if (handler === "acpx.agent_run") {
    if (value.includes("codex")) return "codex";
    if (value.includes("claude")) return "claude";
  }
  return "orchestrator";
}

function actorLabel(actor) {
  return {
    claude: "Claude 工作",
    codex: "Codex 审核",
    orchestrator: "证据包 / 规则路由",
    router: "证据包 / 规则路由",
    human: "Human Gate",
  }[actor] || actor;
}

function defaultActorForStage(stageId) {
  if (stageId === "acceptance") return "codex";
  if (stageId === "contract" || stageId === "delivery") return "router";
  if (stageId === "implement" || stageId === "self_test") return "claude";
  if (stageId === "intake" || stageId === "design" || stageId === "plan") return "human";
  return "stage";
}

function renderStageDetail(nodes, currentStage) {
  if (!els.stageDetail) return;
  const [stageId, label, sub] = STAGES.find(([id]) => id === state.selectedStage) || ["", "阶段", ""];
  const status = stageStateFor(stageId, nodes, currentStage);
  const headline = nodes.length
    ? `${nodes.length} 个节点 · ${roleCounts(nodes)}`
    : "还没有执行到这个阶段";
  const selectedActor = state.selectedFlowActor || "stage";
  const actorText = selectedActor === "stage" ? "阶段整体" : actorLabel(selectedActor);
  els.stageDetail.innerHTML = `
    <div class="stage-detail-head">
      <div>
        <h3>${escapeHtml(label)}阶段</h3>
        <div class="cap">${escapeHtml(sub)}。${escapeHtml(headline)}</div>
      </div>
      <span class="small-pill ${statusKind(status === "done" ? "COMPLETED" : status === "current" ? "RUNNING" : "")}">${escapeHtml(stageStateLabel(status))}</span>
    </div>
    <div class="stage-detail-summary">
      <div><b>当前查看对象</b><span>${escapeHtml(actorText)}</span></div>
      <div><b>阶段证据</b><span>${escapeHtml(stageEvidenceText(nodes))}</span></div>
      <div><b>轮次提示</b><span>${escapeHtml(stageIterationText(nodes))}</span></div>
    </div>
  `;
}

function stageEvidenceText(nodes) {
  if (!nodes.length) return "还没有 timeline 证据。";
  return roleCounts(nodes);
}

function stageIterationText(nodes) {
  const ids = [...new Set(nodes.map((node) => node.iterationId).filter(Boolean))];
  if (!ids.length) return "暂无轮次信息。";
  if (ids.length === 1) return "本阶段出现 1 次协作。";
  return `本阶段出现 ${ids.length} 次协作：${ids.slice(0, 4).map((_, index) => `第 ${index + 1} 次`).join("、")}${ids.length > 4 ? "…" : ""}`;
}

function stageStateLabel(status) {
  if (status === "done") return "已完成";
  if (status === "current") return "当前阶段";
  if (status === "recovery") return "需要恢复";
  return "未开始";
}

function iterationRounds(nodes) {
  const byIteration = new Map();
  for (const node of nodes) {
    const id = node.iterationId || "iter-unknown";
    if (!byIteration.has(id)) byIteration.set(id, []);
    byIteration.get(id).push(node);
  }
  const iterations = [...byIteration.entries()].sort(([a], [b]) => a.localeCompare(b));
  return iterations.map(([iterationId, items], index) => {
    const decisions = items
      .map((node) => node.result?.decision || node.result?.stage_outcome || node.result?.category || "")
      .filter(Boolean);
    const rejected = decisions.some((value) => ["REJECT", "NEEDS_REPAIR", "REJECT_CONTRACT_GAP"].includes(value));
    const revision = decisions.some((value) => ["NEEDS_REVISION", "BLOCKED"].includes(value));
    const accepted = decisions.some((value) => ["ACCEPT", "ACCEPT_WITH_NOTES", "PASS", "APPROVE"].includes(value));
    const roles = roleCounts(items);
    const keyDecision = decisions.find((value) => ["REJECT", "NEEDS_REVISION", "ACCEPT_WITH_NOTES", "ACCEPT", "PASS"].includes(value)) || decisions[0] || "";
    const title = rejected
      ? "Codex 拒收，进入修复轮"
      : revision
        ? "Codex 要求修订"
        : accepted
          ? "审查通过"
          : index === 0 ? "基础执行轮" : "继续收敛";
    const kind = rejected ? "bad" : revision ? "warn" : accepted ? "ok" : "info";
    const pill = rejected ? "REJECT" : revision ? "REVISE" : accepted ? "PASS" : index === 0 ? "BASE" : "LOOP";
    const readableIteration = iterationId.replace("iter-", "第 ") + " 轮";
    const detail = [
      roles,
      keyDecision ? `关键结论：${keyDecision}` : "",
      latestIterationNode(items) ? `停靠/完成：${latestIterationNode(items)}` : "",
    ].filter(Boolean).join("；");
    return { round: readableIteration, title, detail, pill, kind };
  });
}

function latestIterationNode(nodes) {
  const node = [...nodes].sort(compareNodes).at(-1);
  return node?.displayName || node?.nodeDefinitionId || "";
}

function stageIdFromNode(nodeId) {
  const value = String(nodeId || "");
  if (value.includes("requirement") || value.includes("intake")) return "intake";
  if (value.includes("contract")) return "contract";
  if (value.includes("design") || value.includes("arch")) return "design";
  if (value.includes("plan")) return "plan";
  if (value.includes("self_test") || value.includes("self-test")) return "self_test";
  if (value.includes("acceptance")) return "acceptance";
  if (value.includes("implement")) return "implement";
  if (value.includes("delivery") || value.includes("report") || value.includes("next_stage")) return "delivery";
  return "";
}

function stageSubtitle(stageId, stateName) {
  if (stateName === "done") return "已完成";
  if (stateName === "current") {
    const status = state.overview?.workflow?.status || "";
    if (status === "WAITING_FOR_HUMAN") return "等待人审";
    if (status === "WAITING_FOR_RECOVERY") return "需要恢复";
    if (status === "RUNNING") return "运行中";
    return "当前";
  }
  return STAGES.find(([id]) => id === stageId)?.[2] || "待执行";
}

function loopLabel(overview) {
  const count = overview.acceptance?.repairLoopCount || 0;
  const status = overview.workflow?.status || "";
  if (count > 0) return `修复轮 ${count}/3`;
  if (status === "WAITING_FOR_RECOVERY") return "运行时恢复";
  if (status === "WAITING_FOR_HUMAN") return "等待人审";
  return "基础轮";
}

function primaryKind(status) {
  if (status === "WAITING_FOR_HUMAN") return "waiting";
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) return "recovery";
  if (status === "RUNNING") return "running";
  if (status === "COMPLETED") return "completed";
  return "";
}

function primaryEyebrow(status) {
  if (status === "WAITING_FOR_HUMAN") return "需要你处理";
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) return "需要恢复";
  if (status === "RUNNING") return "系统执行中";
  if (status === "COMPLETED") return "已完成";
  return "当前状态";
}

function primaryTitle(status) {
  if (status === "WAITING_FOR_HUMAN") return "等待人工审查";
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) return "当前节点需要恢复";
  if (status === "RUNNING") return "系统正在执行任务";
  if (status === "COMPLETED") return "任务已完成";
  return "任务状态";
}

function rcaSymptom(status, assessment) {
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    return assessment.classification || assessment.reason || "工作流需要恢复。";
  }
  if (status === "WAITING_FOR_HUMAN") return "流程停在人闸，等待人工决定。";
  if (status === "RUNNING") return "任务运行中，没有故障。";
  if (status === "COMPLETED") return "任务完成，没有故障。";
  return "暂无症状。";
}

function rcaImpact(status) {
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    return "影响当前 workstream；不应新开任务或直接修改 original repo。";
  }
  if (status === "WAITING_FOR_HUMAN") return "只影响当前任务推进，等待人类判断。";
  if (status === "RUNNING") return "占用一个 agent execution。";
  if (status === "COMPLETED") return "等待交付动作，不影响执行链路。";
  return "-";
}

function rcaHypothesis(status, assessment) {
  if (assessment.readiness || assessment.nextAction) {
    return `${assessment.readiness || ""} ${assessment.nextAction || ""}`.trim();
  }
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) return "根据 recovery assessment 处理当前节点。";
  if (status === "WAITING_FOR_HUMAN") return "这是正常的人审 gate。";
  if (status === "RUNNING") return "当前有进展时不要重复运行。";
  if (status === "COMPLETED") return "终态保护应阻止继续执行。";
  return "-";
}

function rcaEvidence(status, assessment) {
  const evidence = [];
  if (assessment.classification) evidence.push(`Recovery classification: ${assessment.classification}`);
  if (assessment.reason) evidence.push(`Reason: ${assessment.reason}`);
  if (state.recovery?.recoveryPath) evidence.push(`Failure summary: ${state.recovery.recoveryPath}`);
  if (state.review?.packetPath) evidence.push(`Review packet: ${state.review.packetPath}`);
  if (state.overview?.whyStopped) evidence.push(`Attention detail: ${state.overview.whyStopped}`);
  if (!evidence.length) evidence.push("当前状态没有额外故障证据。");
  return evidence;
}

function rcaVerification(status) {
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) {
    return [
      "先确认外部工具可用，再执行 retry。",
      "retry 后检查 active node 是否继续推进，而不是新建 workstream。",
      "恢复后检查是否进入下一个 gate 或完成状态。",
    ];
  }
  if (status === "WAITING_FOR_HUMAN") {
    return ["批准前阅读 Gate Evidence。", "打回时必须填写可执行反馈。", "批准后观察是否进入下一阶段。"];
  }
  if (status === "COMPLETED") {
    return ["合并前确认 target repo 干净。", "检查 merge patch 和 excluded files。", "delivery merge 不自动 commit。"];
  }
  return ["观察下一次 overview 更新。"];
}

function recoveryEvidence() {
  const evidence = rcaEvidence(state.overview?.workflow?.status || "", state.overview?.recoveryAssessment || {});
  return evidence;
}

function deliveryEvidenceCard() {
  const delivery = deliveryView();
  const changedFiles = asList(delivery.changedFiles);
  const excludedFiles = asList(delivery.excludedFiles);
  const blockedReasons = asList(delivery.blockedReasons);
  const bullets = [
    ...blockedReasons.map((reason) => `阻塞原因：${reason}`),
    ...changedFiles.slice(0, 8).map((file) => `业务改动：${file}`),
    ...excludedFiles.slice(0, 5).map((file) => `已排除：${file}`),
  ];
  if (delivery.failureReason) {
    bullets.unshift(`合并失败：${delivery.failureReason}`);
  }
  if (delivery.operatorNextAction || delivery.nextAction) {
    bullets.push(`下一步：${delivery.operatorNextAction || delivery.nextAction}`);
  }
  asList(delivery.nextCommands).forEach((command) => {
    bullets.push(`命令：${command}`);
  });
  if (delivery.status === "APPLIED") {
    bullets.push("说明：当前只完成暂存，没有自动提交、推送或创建 PR。");
  }
  if (!bullets.length) {
    bullets.push("暂无可展示的交付证据。");
  }
  return evidenceCard("交付就绪检查", "这不是新的流程状态，而是终态后的交付前核对清单。", [
    ["交付状态", deliveryStatus()],
    ["最终验收", delivery.acceptanceDecision || state.overview?.acceptance?.finalDecision || "-"],
    ["目标结果", delivery.goalOutcome || state.overview?.goalSatisfaction?.domainOutcome || "-"],
    ["目标仓库", delivery.targetRepo || "-"],
    ["业务改动", changedFiles.length ? `${changedFiles.length} 个文件` : "-"],
    ["终态保护", "已完成任务只能查看、导出或显式合并"],
  ], bullets);
}

function completionEvidence() {
  const delivery = deliveryView();
  const gaps = state.overview?.completionAudit?.gaps || [];
  return [
    delivery.status ? `Delivery merge: ${delivery.status}` : "Delivery merge 尚未执行或未记录。",
    ...(delivery.operatorNextAction || delivery.nextAction ? [`下一步：${delivery.operatorNextAction || delivery.nextAction}`] : []),
    ...asList(delivery.nextCommands).map((command) => `命令：${command}`),
    ...asList(gaps).map((gap) => `Completion gap: ${gap}`),
  ];
}

function deliveryView() {
  return state.overview?.deliveryView || state.status?.deliveryView || state.overview?.deliveryMerge || state.status?.deliveryMerge || {};
}

function deliveryStatus() {
  return deliveryView().status || "未执行";
}

function latestTimelineText() {
  const nodes = timelineNodes();
  const latest = nodes[nodes.length - 1] || {};
  return latest.displayName || latest.nodeDefinitionId || latest.status || "-";
}

function latestAgentTaskText() {
  const task = (state.overview?.latestAgentTasks || [])[0] || {};
  return task.status ? `${task.status} · ${task.agent || task.handlerType || ""}` : "-";
}

function stripCommand(text) {
  return String(text || "").replace(/\s*Command:\s*.*/s, "").trim() || "-";
}

function statusKind(status) {
  if (status === "COMPLETED") return "ok";
  if (status === "RUNNING") return "info";
  if (status === "WAITING_FOR_HUMAN") return "warn";
  if (["WAITING_FOR_RECOVERY", "FAILED", "BLOCKED"].includes(status)) return "bad";
  return "warn";
}

function statusLabel(status) {
  return {
    COMPLETED: "已完成",
    RUNNING: "运行中",
    WAITING_FOR_HUMAN: "等待审查",
    WAITING_FOR_RECOVERY: "需要恢复",
    FAILED: "已失败",
    BLOCKED: "已阻塞",
  }[status] || status || "-";
}

function selectedBase() {
  return `/api/workstreams/${encodeURIComponent(state.selectedId)}`;
}

function renderList(element, values) {
  element.innerHTML = asList(values).map((value) => `<li>${escapeHtml(value)}</li>`).join("");
}

function asList(value) {
  if (Array.isArray(value)) return value.filter(Boolean);
  return value ? [value] : [];
}

function firstNumber(object, keys) {
  for (const key of keys) {
    const value = numberValue(object?.[key]);
    if (value > 0) return value;
  }
  return 0;
}

function sumByKeys(items, keys) {
  return (items || []).reduce((sum, item) => {
    for (const key of keys) {
      const value = numberValue(item?.[key]);
      if (value > 0) return sum + value;
    }
    return sum;
  }, 0);
}

function numberValue(value) {
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : 0;
}

function formatNumber(value) {
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`;
  return String(value);
}

function budgetAssessment(used, budget) {
  if (!budget || !used) return "暂无预算评估";
  const ratio = used / budget;
  if (ratio >= 1) return "已超预算";
  if (ratio >= 0.85) return "接近预算";
  if (ratio >= 0.65) return "消耗偏高";
  return "预算健康";
}

function compareNodes(left, right) {
  return timestamp(left) - timestamp(right);
}

function timestamp(node) {
  for (const value of [node.startedAt, node.finishedAt, node.updatedAt, node.createdAt]) {
    const time = Date.parse(value);
    if (Number.isFinite(time)) return time;
  }
  return Number.MAX_SAFE_INTEGER;
}

function timeLabel(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString([], { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function shortId(value) {
  const text = String(value || "");
  return text.length > 8 ? text.slice(-8) : text;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function escapeShell(value) {
  return String(value || "").replaceAll("\\", "\\\\").replaceAll('"', '\\"').replaceAll("\n", "\\n");
}

els.operatorTab.addEventListener("click", () => switchView("operator"));
els.managementTab.addEventListener("click", () => switchView("management"));
els.openWorkspaceBtn.addEventListener("click", applyWorkspace);
els.refreshWorkspaceBtn.addEventListener("click", () => loadWorkspaceRegistry().then(refreshList));
els.projectSelect.addEventListener("change", async () => {
  const project = state.projects.find((item) => item.projectId === els.projectSelect.value);
  selectProject(project);
  state.selectedId = "";
  localStorage.removeItem("dual.console.selectedId");
  await refreshList();
});
els.workspacePath.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    applyWorkspace();
  }
});
els.prevTaskPage.addEventListener("click", () => {
  state.taskPage = Math.max(1, state.taskPage - 1);
  renderTaskList();
});
els.nextTaskPage.addEventListener("click", () => {
  state.taskPage += 1;
  renderTaskList();
});
els.openOldPanel.addEventListener("click", openOldPanel);
els.copyCommand.addEventListener("click", () => copyText(els.diagnosticCommand.textContent));
els.newTaskBtn.addEventListener("click", openModal);
els.closeModal.addEventListener("click", closeModal);
els.closeModalBottom.addEventListener("click", closeModal);
els.newTaskModal.addEventListener("click", (event) => {
  if (event.target === els.newTaskModal) closeModal();
});
for (const element of [els.newTaskName, els.newTaskRepo, els.newTaskDescription, els.newTaskLanguage, els.newTaskMode, els.newTaskLifecycle, els.newTaskMaxSteps]) {
  element.addEventListener("input", updateNewTaskCommand);
  element.addEventListener("change", updateNewTaskCommand);
}
els.copyNewTaskCommand.addEventListener("click", () => copyText(els.newTaskCommand.textContent));

switchView(state.view);
renderWorkspace();
renderLifecycle();
loadWorkspaceRegistry().then(refreshList);
