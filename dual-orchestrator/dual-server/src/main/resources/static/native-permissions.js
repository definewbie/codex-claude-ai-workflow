/* Both consoles consume the same server projection; no browser-derived permission authority. */
window.DualNativePermissions = {
  render(container, view, submit) {
    if (!container) return;
    container.replaceChildren();
    const rows = view?.requests || [];
    container.hidden = rows.length === 0 && !view?.reason;
    if (container.hidden) return;
    const add = (tag, text, parent = container) => {
      const element = document.createElement(tag);
      element.textContent = text;
      parent.append(element);
      return element;
    };
    add("h3", "原生客户端权限请求");
    add("p", "这里只决定客户端提出的操作。不会扩大业务 scope，也不代替独立复评或交付批准；原生请求可能涉及整个本轮权限，请阅读完整材料。");
    if (view.reason) add("p", `当前不可操作：${view.status} — ${view.reason}`);
    const labels = {
      REQUEST_PENDING: "等待你的选择", DECISION_SAVED: "决定已保存，尚未提交回复",
      COMMITTED_MAY_BE_IN_FLIGHT: "回复已提交，可能在途；不会补发", REPLY_CONSUMED: "客户端已消费选择，操作是否完成另看执行证据",
      REPLY_REJECTED: "客户端未接受回复，不会补发", REPLY_UNKNOWN: "回复送达不明，需核对原执行；不会补发", REQUEST_ENDED: "原请求已结束，不能继续回复",
      STOP_UNCONFIRMED: "新回复已关闭，停止仍未确认", EXPIRED: "请求已过期，不能继续批准", HISTORICAL: "历史请求，只读",
    };
    for (const row of rows) {
      const section = add("section", "");
      add("h4", row.raw_request?.toolCall?.title || "客户端请求", section);
      add("p", `${labels[row.status] || row.status} · ${row.request_key}`, section);
      add("p", `请求期限：${new Date(row.request.deadlineMs).toLocaleString()}`, section);
      const details = add("details", "", section);
      add("summary", "完整原生请求、身份和回复观察", details);
      add("pre", JSON.stringify({ request: row.raw_request, identity: row.request, decision: row.decision, commit: row.commit, observation: row.observation }, null, 2), details);
      const choose = (button, action, body) => {
        button.addEventListener("click", async () => {
          button.disabled = true;
          try { await submit(action, body); } catch (error) { add("p", `操作未确认：${error.message}`, section); }
        });
      };
      if (row.can_decide) for (const option of row.options || []) {
        const button = add("button", `${option.name} (${option.optionId})`, section);
        button.type = "button";
        choose(button, "decide", { execution: row.execution, requestKey: row.request_key, optionId: option.optionId, note: "operator selected the offered native option" });
      }
      if (row.can_cancel) {
        const button = add("button", "关闭本次执行的新许可并请求停止", section);
        button.type = "button";
        choose(button, "cancel", { execution: row.execution, note: "operator cancelled native permission wait; an already committed reply may be in flight" });
      }
    }
  },
};
