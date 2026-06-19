const state = {
  workdir: localStorage.getItem("dual.console.workdir") || "",
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
};

const $ = (id) => document.getElementById(id);

const els = {
  connection: $("connection"),
  operatorTab: $("operatorTab"),
  managementTab: $("managementTab"),
  operatorView: $("operatorView"),
  managementView: $("managementView"),
  taskCount: $("taskCount"),
  taskList: $("taskList"),
  taskTitle: $("taskTitle"),
  taskSub: $("taskSub"),
  taskPill: $("taskPill"),
  stages: $("stages"),
  loopBadge: $("loopBadge"),
  primaryCard: $("primaryCard"),
  primaryEyebrow: $("primaryEyebrow"),
  primaryTitle: $("primaryTitle"),
  primaryWhy: $("primaryWhy"),
  primaryNext: $("primaryNext"),
  revisionBox: $("revisionBox"),
  revisionFeedback: $("revisionFeedback"),
  primaryActions: $("primaryActions"),
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

function renderTaskList() {
  els.taskList.innerHTML = "";
  if (!state.workstreams.length) {
    els.taskList.innerHTML = `<p class="empty">当前目录没有任务。可以新建任务，或在管理诊断中打开旧面板选择 workspace。</p>`;
    return;
  }
  for (const item of state.workstreams) {
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
      request(`${base}/status?workdir=${workdirParam()}`),
      request(`${base}/overview?workdir=${workdirParam()}`),
      request(`${base}/timeline?workdir=${workdirParam()}`),
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
    state.review = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/current?workdir=${workdirParam()}`);
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
    const recovery = await request(`/api/workstreams/${encodeURIComponent(state.selectedId)}/recovery/current?workdir=${workdirParam()}`);
    state.recovery = recovery.available ? recovery : null;
  } catch {
    state.recovery = null;
  }
}

function connectProgressStream() {
  if (!state.selectedId || !window.EventSource) {
    return;
  }
  const url = `/api/workstreams/${encodeURIComponent(state.selectedId)}/stream?workdir=${workdirParam()}&intervalMs=2500`;
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
  const done = new Set();
  for (const node of nodes) {
    if (node.status === "COMPLETED") {
      done.add(stageIdFromNode(node.nodeDefinitionId || node.displayName || ""));
    }
  }
  els.stages.innerHTML = "";
  for (const [id, label, sub] of STAGES) {
    const stageState = id === currentStage ? "current" : done.has(id) ? "done" : "";
    const item = document.createElement("div");
    item.className = `stage ${stageState}`;
    const number = STAGES.findIndex(([stageId]) => stageId === id) + 1;
    item.innerHTML = `
      <div class="node">${stageState === "done" ? "✓" : number}</div>
      <div class="lab">${escapeHtml(label)}</div>
      <div class="sub">${escapeHtml(stageSubtitle(id, stageState))}</div>
    `;
    els.stages.appendChild(item);
  }
  els.loopBadge.className = "small-pill warn";
  els.loopBadge.textContent = loopLabel(overview);
}

function renderPrimary(status) {
  const overview = state.overview || {};
  const why = overview.whyStopped || friendlyWhy(status);
  const next = overview.nextAction || friendlyNext(status);
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
    left = evidenceCard("完成审计", "终态任务只能观测、导出和显式交付。", [
      ["最终验收", state.overview?.acceptance?.finalDecision || "-"],
      ["目标结果", state.overview?.goalSatisfaction?.domainOutcome || "-"],
      ["交付状态", deliveryStatus()],
      ["终态保护", "禁止 resume / retry / approve-run"],
    ], completionEvidence());
  } else {
    left = evidenceCard("当前状态", "尚无可操作状态。", [["状态", status]], []);
  }

  els.evidence.innerHTML = left + tokenCard(tokens);
}

function renderLoopHistory() {
  const overview = state.overview || {};
  const repairActions = overview.acceptance?.repairActions || [];
  const nodes = timelineNodes();
  const items = [];
  if (repairActions.length) {
    repairActions.forEach((action, index) => {
      items.push({
        round: `第 ${index + 1} 轮`,
        title: action.action || "semantic repair",
        detail: "外部验收或目标满足度触发语义修复。",
        pill: "REPAIR",
        kind: "warn",
      });
    });
  }
  const recovery = overview.recoveryAssessment || {};
  if (recovery.classification || recovery.readiness) {
    items.push({
      round: "当前",
      title: recovery.classification || "runtime recovery",
      detail: "运行时恢复，不计入语义修复轮次。",
      pill: "RECOVERY",
      kind: "bad",
    });
  }
  const recentGate = nodes.find((node) => node.kind === "GATE" && node.status === "WAITING");
  if (recentGate) {
    items.push({
      round: "当前",
      title: recentGate.displayName || recentGate.nodeDefinitionId || "human gate",
      detail: "人工判断点，不是自动 retry。",
      pill: "HUMAN",
      kind: "info",
    });
  }
  if (!items.length) {
    items.push({
      round: "第 1 轮",
      title: "暂无修复循环",
      detail: "当前任务还没有语义修复或运行时恢复记录。",
      pill: "BASE",
      kind: "ok",
    });
  }
  els.loopHistory.innerHTML = items.map(loopItem).join("");
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
  els.tokenBudgetBar.style.width = tokens.budgetPercent + "%";
}

function tokenSummary() {
  const metrics = state.overview?.metrics || {};
  const runs = state.overview?.latestAgentRuns || [];
  const prompt = firstNumber(metrics, ["promptTokens", "inputTokens", "totalPromptTokens", "agentPromptTokens"])
    || sumByKeys(runs, ["promptTokens", "inputTokens", "prompt_tokens", "input_tokens"]);
  const completion = firstNumber(metrics, ["completionTokens", "outputTokens", "totalCompletionTokens", "agentCompletionTokens"])
    || sumByKeys(runs, ["completionTokens", "outputTokens", "completion_tokens", "output_tokens"]);
  const budget = firstNumber(metrics, ["tokenBudget", "contextTokenBudget"]) || 0;
  const used = prompt + completion;
  const budgetPercent = budget > 0 ? Math.min(100, Math.round((used / budget) * 100)) : 0;
  const cost = firstNumber(metrics, ["estimatedCost", "cost", "totalCost"]) || 0;
  return {
    prompt: prompt ? formatNumber(prompt) : "-",
    completion: completion ? formatNumber(completion) : "-",
    budgetLabel: budget ? `${budgetPercent}%` : "-",
    budgetPercent,
    cost: cost ? String(cost) : "-",
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
      <h3>Token 与成本</h3>
      <div class="cap">最佳努力观测数据；用于发现 context 膨胀，不参与路由。</div>
      <div class="token-grid">
        ${tokenMetric("输入", tokens.prompt, "prompt")}
        ${tokenMetric("输出", tokens.completion, "completion")}
        ${tokenMetric("预算", tokens.budgetLabel, "budget")}
        ${tokenMetric("成本", tokens.cost, "estimate")}
      </div>
      <div class="budget"><i style="width:${tokens.budgetPercent}%"></i></div>
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
    return [
      { label: "查看交付差异", kind: "primary", onClick: () => switchView("management") },
      { label: "打开旧管理面板", kind: "ghost", onClick: openOldPanel },
    ];
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
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/approve?workdir=${workdirParam()}&run=true&maxSteps=80`);
}

function reviseAndRun() {
  const feedback = (els.revisionFeedback?.value || "").trim();
  if (!feedback) {
    renderFailure("缺少打回说明", "请求修改前，请先在“打回说明”里写清楚需要重新处理的问题。");
    els.revisionFeedback?.focus();
    return;
  }
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/review/revise?workdir=${workdirParam()}&run=true&maxSteps=80`, { feedback });
}

function retryCurrent() {
  postAction(`/api/workstreams/${encodeURIComponent(state.selectedId)}/retry?workdir=${workdirParam()}`, { note: "retry from operator console" });
}

function commandForStatus(status, assessment) {
  const base = `--workdir "${state.workdir || "."}" ${state.selectedId || "<workstream_id>"}`;
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
    return `${jar} summary ${base}\n${jar} delivery merge --workdir "${state.workdir || "."}" --target "<original_repo>" ${state.selectedId}`;
  }
  return `${jar} status ${base}`;
}

function renderEmpty() {
  els.taskTitle.textContent = "请选择任务";
  els.taskSub.textContent = "当前没有选中的任务。";
  els.taskPill.className = "pill muted";
  els.taskPill.textContent = "未选择";
  els.primaryTitle.textContent = "选择任务后查看当前状态";
  els.primaryWhy.textContent = "任务视图只展示完成判断所需的信息。";
  els.primaryNext.textContent = "从左侧选择任务，或新建任务。";
  renderRevisionBox(null);
  els.primaryActions.innerHTML = "";
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
  window.location.href = "/index.html";
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
  const repo = els.newTaskRepo.value.trim() || "<repo>";
  const language = els.newTaskLanguage.value;
  const lifecycle = els.newTaskLifecycle.value;
  const maxSteps = Number.parseInt(els.newTaskMaxSteps.value, 10) || 80;
  const requirement = els.newTaskDescription.value.trim() || els.newTaskName.value.trim() || "任务描述";
  const variant = lifecycle === "design-only" ? "--workflow-variant contract" : "";
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

function friendlyWhy(status) {
  if (status === "WAITING_FOR_HUMAN") return "当前停在人闸，需要阅读证据后批准或请求修改。";
  if (status === "WAITING_FOR_RECOVERY") return "当前有运行时故障，需要恢复后继续。";
  if (status === "RUNNING") return "当前任务正在执行。";
  if (status === "COMPLETED") return "任务已进入终态。";
  return "状态信息不足。";
}

function friendlyNext(status) {
  if (status === "WAITING_FOR_HUMAN") return "阅读 Gate Evidence 后批准继续，或请求修改。";
  if (status === "WAITING_FOR_RECOVERY") return "查看 RCA，确认外部条件后重试当前节点。";
  if (status === "RUNNING") return "等待系统到达下一个人闸。";
  if (status === "COMPLETED") return "查看交付差异并显式合并。";
  return "刷新状态。";
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

function completionEvidence() {
  const delivery = state.overview?.deliveryMerge || {};
  const gaps = state.overview?.completionAudit?.gaps || [];
  return [
    delivery.status ? `Delivery merge: ${delivery.status}` : "Delivery merge 尚未执行或未记录。",
    ...asList(gaps).map((gap) => `Completion gap: ${gap}`),
  ];
}

function deliveryStatus() {
  return state.overview?.deliveryMerge?.status || state.status?.deliveryMerge?.status || "未执行";
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
    const value = Number(object?.[key]);
    if (Number.isFinite(value) && value > 0) return value;
  }
  return 0;
}

function sumByKeys(items, keys) {
  return (items || []).reduce((sum, item) => {
    for (const key of keys) {
      const value = Number(item?.[key]);
      if (Number.isFinite(value) && value > 0) return sum + value;
    }
    return sum;
  }, 0);
}

function formatNumber(value) {
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`;
  return String(value);
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
renderLifecycle();
refreshList();
