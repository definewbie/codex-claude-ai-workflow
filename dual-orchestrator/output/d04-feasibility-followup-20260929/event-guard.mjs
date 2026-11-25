// Diagnostic only. Consume typed SDK events; never infer permission from prose.
export class EventGuard {
  constructor(model) { this.model = model; this.stop = null; this.tools = new Set(); this.init = null; }
  observe(e) {
    if (this.stop) return this.stop;
    const stop = reason => this.stop = reason;
    if (!e || typeof e.type !== 'string') return stop('INVALID_EVENT');
    if (e.type === 'system' && e.subtype === 'api_retry')
      return stop(e.error_status === 401 || e.error === 'authentication_failed' ? 'AUTHENTICATION_ERROR' : 'API_RETRY_ERROR');
    if (e.api_error_status === 401 || e.error === 'authentication_failed') return stop('AUTHENTICATION_ERROR');
    if (e.type === 'probe_error') return stop('SDK_ERROR');
    if (e.type === 'result' && (e.is_error || e.subtype !== 'success')) return stop('RESULT_ERROR');
    if (e.type === 'system' && e.subtype === 'init') {
      if (this.init) return stop('REPEATED_INIT');
      this.init = e;
      if (!Array.isArray(e.tools) || e.tools.length !== 3 || new Set(e.tools).size !== 3 ||
          e.tools.some(t => !['Read', 'Grep', 'Glob'].includes(t))) return stop('UNEXPECTED_EFFECTIVE_TOOLS');
      if (e.model !== this.model) return stop('MODEL_MISMATCH');
      if (e.permissionMode !== 'dontAsk') return stop('PERMISSION_MODE_MISMATCH');
      for (const field of ['mcp_servers', 'plugins', 'skills', 'slash_commands'])
        if (!Array.isArray(e[field]) || e[field].length) return stop('UNEXPECTED_OR_UNKNOWN_EXTENSION_SURFACE');
    }
    if (e.type === 'assistant') {
      if (e.error) return stop('MODEL_ERROR');
      for (const b of e.message?.content ?? []) if (b.type === 'tool_use') {
        if (!this.init) return stop('TOOL_BEFORE_INIT');
        if (!b.id || !['Read', 'Grep', 'Glob'].includes(b.name)) return stop('UNEXPECTED_TOOL_ATTEMPT');
        this.tools.add(b.id);
        if (this.tools.size >= 6) return stop('TOOL_THRESHOLD');
      }
    }
    if (e.type === 'user') for (const b of e.message?.content ?? [])
      if (b.type === 'tool_result' && b.is_error) return stop('READ_TOOL_ERROR');
    return null;
  }
}

// Shared by the real SDK stream and the offline sequence checks.
export async function consumeGuarded(stream, guard, emit, close) {
  try {
    for await (const event of stream) {
      emit(event);
      const reason = guard.observe(event);
      if (reason) {
        emit({type:'probe_stop', reason, triggerType:event.type, triggerSubtype:event.subtype ?? null});
        return reason;
      }
    }
    return null;
  } finally { close(); }
}
