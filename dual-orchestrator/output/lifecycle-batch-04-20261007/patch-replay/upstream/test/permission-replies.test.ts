import assert from "node:assert/strict";
import test from "node:test";
import type { RequestPermissionRequest } from "@agentclientprotocol/sdk";
import {
  SessionQueueOwner,
  tryAcquireQueueOwnerLease,
  releaseQueueOwnerLease,
  sendNativePermissionReply,
} from "../src/cli/queue/ipc.js";
import { parseQueueRequest } from "../src/cli/queue/messages.js";
import {
  QueuePermissionReplies,
  type NativePermissionRequest,
  type NativePermissionReply,
} from "../src/cli/queue/permission-replies.js";
import {
  withTempHome,
  queuePaths,
  writeQueueOwnerLock,
  createSingleRequestServer,
  listenServer,
  closeServer,
} from "./queue-test-helpers.js";

function raw(sessionId = "session"): RequestPermissionRequest {
  return {
    sessionId,
    toolCall: { toolCallId: "tool", title: "edit", kind: "edit" },
    options: [
      { optionId: "allow-exact", kind: "allow_once", name: "Allow once" },
      { optionId: "deny-exact", kind: "reject_once", name: "Deny once" },
    ],
  };
}
function response(
  request: NativePermissionRequest,
  optionId = "allow-exact",
): NativePermissionReply {
  const { schema: _schema, rawJson: _raw, ...identity } = request;
  return { ...identity, optionId, commitRef: "execution.json", commitSha256: "a".repeat(64) };
}
function active(deadlineMs = Date.now() + 10000) {
  const replies = new QueuePermissionReplies();
  const requests: NativePermissionRequest[] = [];
  replies.begin({
    promptId: "prompt",
    ownerGeneration: 7,
    deadlineMs,
    publish: (request) => requests.push(request),
  });
  const ask = (rpcId: string | number, signal = new AbortController().signal) =>
    replies.ask(
      { raw: raw(), sessionId: "session", inferredKind: "edit" },
      { requestId: rpcId, signal },
    );
  return { replies, requests, ask };
}

test("concurrent asks retain exact per-request allow/deny decisions; repeats never send twice", async () => {
  const { replies, requests, ask } = active();
  const a = ask("A");
  const b = ask("B");
  assert.equal(requests.length, 2);
  assert.equal(replies.reply(response(requests[1], "deny-exact")), true);
  assert.equal(replies.reply(response(requests[0])), true);
  assert.deepEqual(await a, { optionId: "allow-exact" });
  assert.deepEqual(await b, { optionId: "deny-exact" });
  assert.equal(replies.reply(response(requests[0])), false);
  assert.deepEqual(await ask("A"), { cancel: true });
  replies.close();
});

test("wrong owner/prompt/session/RPC/hash/deadline/choice does not poison a legal ask", async () => {
  const { replies, requests, ask } = active();
  const pending = ask(12);
  const valid = response(requests[0]);
  const invalid = [
    { ownerGeneration: 8 },
    { promptId: "another" },
    { sessionId: "other" },
    { rpcId: "12" },
    { rawSha256: "b".repeat(64) },
    { deadlineMs: valid.deadlineMs + 1 },
    { optionId: "missing" },
    { commitSha256: "" },
  ];
  for (const change of invalid) {
    assert.equal(replies.reply({ ...valid, ...change }), false);
  }
  assert.equal(replies.reply(valid), true);
  assert.deepEqual(await pending, { optionId: "allow-exact" });
  replies.close();
});

test("cancel/turn replacement/owner restart never restore an old callback from a commit", async () => {
  const { replies, requests, ask } = active();
  const controller = new AbortController();
  const pending = ask(1, controller.signal);
  const old = response(requests[0]);
  controller.abort();
  assert.deepEqual(await pending, { cancel: true });
  assert.equal(replies.reply(old), false);
  replies.close();
  replies.begin({
    promptId: "next",
    ownerGeneration: 8,
    deadlineMs: Date.now() + 10000,
    publish: () => {},
  });
  assert.equal(replies.reply(old), false);
  assert.equal(new QueuePermissionReplies().reply(old), false);
  replies.close();
});

test("deadline and request publication failure cancel instead of falling through", async () => {
  const { replies, requests, ask } = active(Date.now() + 40);
  const pending = ask(1);
  assert.deepEqual(await pending, { cancel: true });
  assert.equal(replies.reply(response(requests[0])), false);
  replies.close();
  replies.begin({
    promptId: "next",
    ownerGeneration: 8,
    deadlineMs: Date.now() + 10000,
    publish: () => {
      throw new Error("broken output");
    },
  });
  assert.deepEqual(
    await replies.ask(
      { raw: raw(), sessionId: "session", inferredKind: "edit" },
      { requestId: 1, signal: new AbortController().signal },
    ),
    { cancel: true },
  );
  replies.close();
});

test("wire rejects absent/changed generations, unbounded asks and prompt retry", async () => {
  const { replies, requests, ask } = active();
  const pending = ask(1);
  const reply = response(requests[0]);
  const wire = { type: "permission_reply", requestId: "ipc", ownerGeneration: 7, reply };
  assert.ok(parseQueueRequest(wire));
  assert.equal(parseQueueRequest({ ...wire, ownerGeneration: undefined }), null);
  assert.equal(parseQueueRequest({ ...wire, ownerGeneration: 8 }), null);
  const submit = {
    type: "submit_prompt",
    requestId: "p",
    message: "task",
    permissionMode: "approve-reads",
    permissionReplies: true,
    waitForCompletion: true,
    timeoutMs: 1000,
    promptRetries: 0,
  };
  assert.ok(parseQueueRequest(submit));
  assert.equal(parseQueueRequest({ ...submit, timeoutMs: undefined }), null);
  assert.equal(parseQueueRequest({ ...submit, promptRetries: 1 }), null);
  assert.equal(parseQueueRequest({ ...submit, waitForCompletion: false }), null);
  replies.close();
  await pending;
});

test("live IPC replies bypass queued prompts and never recreate consumed callbacks", async () => {
  await withTempHome(async () => {
    const lease = await tryAcquireQueueOwnerLease("permission-ipc");
    assert.ok(lease);
    const replies = new QueuePermissionReplies();
    const requests: NativePermissionRequest[] = [];
    replies.begin({
      promptId: "live-prompt",
      ownerGeneration: lease.ownerGeneration,
      deadlineMs: Date.now() + 5000,
      publish: (request) => requests.push(request),
    });
    const owner = await SessionQueueOwner.start(lease, {
      permissionReply: (reply) => replies.reply(reply),
      cancelPrompt: async () => {
        replies.close();
        return true;
      },
      closeSession: async () => true,
      setSessionMode: async () => {},
      setSessionModel: async () => {},
      setSessionConfigOption: async () => ({ configOptions: [] }),
    });
    try {
      const a = replies.ask(
        { raw: raw(), sessionId: "session", inferredKind: "edit" },
        { requestId: "A", signal: new AbortController().signal },
      );
      const b = replies.ask(
        { raw: raw(), sessionId: "session", inferredKind: "edit" },
        { requestId: "B", signal: new AbortController().signal },
      );
      assert.equal(
        await sendNativePermissionReply(lease.sessionId, response(requests[1], "deny-exact")),
        true,
      );
      assert.deepEqual(await b, { optionId: "deny-exact" });
      assert.equal(await sendNativePermissionReply(lease.sessionId, response(requests[0])), true);
      assert.deepEqual(await a, { optionId: "allow-exact" });
      assert.equal(await sendNativePermissionReply(lease.sessionId, response(requests[0])), false);
      await assert.rejects(
        sendNativePermissionReply(lease.sessionId, {
          ...response(requests[0]),
          ownerGeneration: lease.ownerGeneration + 1,
        }),
      );
      assert.equal(
        await owner.nextTask(10),
        undefined,
        "reply is a control, never a new queued prompt",
      );
    } finally {
      replies.close();
      await owner.close();
      await releaseQueueOwnerLease(lease);
    }
  });
});

test("lost or silent IPC receipt makes one attempt and no owner recovery or resend", async () => {
  for (const silent of [false, true]) {
    await withTempHome(async (dir) => {
      const sessionId = "lost-permission";
      const paths = queuePaths(dir, sessionId);
      await writeQueueOwnerLock({ ...paths, pid: process.pid, sessionId, ownerGeneration: 7 });
      let attempts = 0;
      const server = createSingleRequestServer((socket) => {
        attempts++;
        if (!silent) {
          socket.destroy();
        }
      });
      await listenServer(server, paths.socketPath);
      const { replies, requests, ask } = active(Date.now() + 200);
      const pending = ask(1);
      try {
        await assert.rejects(sendNativePermissionReply(sessionId, response(requests[0])));
        assert.equal(attempts, 1);
      } finally {
        replies.close();
        await pending;
        await closeServer(server);
      }
    });
  }
});
