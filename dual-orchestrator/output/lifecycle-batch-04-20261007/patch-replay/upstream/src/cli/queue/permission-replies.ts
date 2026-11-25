import { createHash, randomUUID } from "node:crypto";
import type { JsonRpcId, RequestPermissionRequest } from "@agentclientprotocol/sdk";
import { isOncePermissionOption } from "../../permissions.js";
import type {
  AcpExactPermissionDecision,
  AcpPermissionRequest,
  AcpPermissionRequestContext,
} from "../../types.js";

export type NativePermissionRequest = {
  schema: "dual.native.permission.request.v1";
  requestKey: string;
  sessionId: string;
  promptId: string;
  ownerGeneration: number;
  rpcId: JsonRpcId;
  rawJson: string;
  rawSha256: string;
  deadlineMs: number;
};
export type NativePermissionReply = Omit<NativePermissionRequest, "schema" | "rawJson"> & {
  optionId: string;
  commitRef: string;
  commitSha256: string;
};
type Pending = {
  request: NativePermissionRequest;
  raw: RequestPermissionRequest;
  consumed: boolean;
  finish: (decision: AcpExactPermissionDecision) => void;
};
type Turn = {
  promptId: string;
  ownerGeneration: number;
  deadlineMs: number;
  publish: (request: NativePermissionRequest) => void;
};

/** Lives only in the existing queue owner. Disk commits never recreate callbacks. */
export class QueuePermissionReplies {
  private turn?: Turn;
  private readonly pending = new Map<string, Pending>();
  private readonly seenRpcIds = new Set<string>();

  begin(turn: Turn): void {
    this.close();
    if (
      !Number.isSafeInteger(turn.ownerGeneration) ||
      turn.ownerGeneration <= 0 ||
      !Number.isFinite(turn.deadlineMs) ||
      turn.deadlineMs <= Date.now()
    ) {
      throw new Error("Online permission asks require a current owner and finite turn deadline");
    }
    this.turn = turn;
  }

  close(): void {
    this.turn = undefined;
    for (const pending of this.pending.values()) {
      pending.finish({ cancel: true });
    }
    this.pending.clear();
    this.seenRpcIds.clear();
  }

  async ask(
    request: AcpPermissionRequest,
    context: AcpPermissionRequestContext,
  ): Promise<AcpExactPermissionDecision> {
    const turn = this.turn;
    if (
      !turn ||
      context.signal.aborted ||
      turn.deadlineMs <= Date.now() ||
      context.requestId == null
    ) {
      return { cancel: true };
    }
    const rpcKey = JSON.stringify([request.sessionId, typeof context.requestId, context.requestId]);
    if (this.seenRpcIds.has(rpcKey)) {
      return { cancel: true };
    }
    this.seenRpcIds.add(rpcKey);
    const rawJson = JSON.stringify(request.raw);
    const envelope: NativePermissionRequest = {
      schema: "dual.native.permission.request.v1",
      requestKey: randomUUID(),
      sessionId: request.sessionId,
      promptId: turn.promptId,
      ownerGeneration: turn.ownerGeneration,
      rpcId: context.requestId,
      rawJson,
      rawSha256: createHash("sha256").update(rawJson).digest("hex"),
      deadlineMs: turn.deadlineMs,
    };
    return await this.waitForReply(turn, envelope, context.signal);
  }

  private async waitForReply(
    turn: Turn,
    request: NativePermissionRequest,
    signal: AbortSignal,
  ): Promise<AcpExactPermissionDecision> {
    return await new Promise((resolve) => {
      let timer: NodeJS.Timeout | undefined;
      const finish = (decision: AcpExactPermissionDecision) => {
        const item = this.pending.get(request.requestKey);
        if (!item || item.consumed) {
          return;
        }
        // Consumption is synchronous with the guard; no await and no reset on send failure.
        item.consumed = true;
        if (timer) {
          clearTimeout(timer);
        }
        signal.removeEventListener("abort", abort);
        resolve(decision);
      };
      const abort = () => finish({ cancel: true });
      const copy: RequestPermissionRequest = JSON.parse(
        request.rawJson,
      ) as RequestPermissionRequest;
      this.pending.set(request.requestKey, { request, raw: copy, consumed: false, finish });
      signal.addEventListener("abort", abort, { once: true });
      timer = setTimeout(abort, Math.max(0, request.deadlineMs - Date.now()));
      if (signal.aborted || this.turn !== turn) {
        abort();
        return;
      }
      try {
        turn.publish(request);
      } catch {
        abort();
      }
    });
  }

  reply(reply: NativePermissionReply): boolean {
    const item = this.pending.get(reply.requestKey);
    const turn = this.turn;
    if (!item || !turn) {
      return false;
    }
    if (!matchesCurrentTurn(item.request, reply, turn)) {
      return false;
    }
    if (!validUnconsumedCommit(item, reply)) {
      return false;
    }
    const option = item.raw.options.find((candidate) => candidate.optionId === reply.optionId);
    if (!option || !isOncePermissionOption(option)) {
      return false;
    }
    item.finish({ optionId: option.optionId });
    return true;
  }
}

function validUnconsumedCommit(item: Pending, reply: NativePermissionReply): boolean {
  return (
    !item.consumed &&
    Date.now() < item.request.deadlineMs &&
    Boolean(reply.commitRef) &&
    /^[a-f0-9]{64}$/.test(reply.commitSha256)
  );
}

function matchesCurrentTurn(
  request: NativePermissionRequest,
  reply: NativePermissionReply,
  turn: Turn,
): boolean {
  return (
    matchesRequest(request, reply) &&
    turn.promptId === reply.promptId &&
    turn.ownerGeneration === reply.ownerGeneration
  );
}

function matchesRequest(request: NativePermissionRequest, reply: NativePermissionReply): boolean {
  const keys = [
    "requestKey",
    "sessionId",
    "promptId",
    "ownerGeneration",
    "rpcId",
    "rawSha256",
    "deadlineMs",
  ] as const;
  return keys.every((key) => request[key] === reply[key]);
}
