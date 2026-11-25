import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import type { NativePermissionRequest } from "../src/cli/queue/permission-replies.js";
const cli = fileURLToPath(new URL("../src/cli.js", import.meta.url));
const peer = fileURLToPath(new URL("./mock-agent.js", import.meta.url));
function launch(home: string, args: string[]) {
  const child = spawn(process.execPath, [cli, ...args], {
    env: { ...process.env, HOME: home },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let output = "";
  let error = "";
  child.stdout.setEncoding("utf8");
  child.stderr.setEncoding("utf8");
  child.stdout.on("data", (chunk: string) => {
    output += chunk;
  });
  child.stderr.on("data", (chunk: string) => {
    error += chunk;
  });
  const done = once(child, "close").then(([code]) => ({ code, output, error }));
  return { child, done };
}
async function command(home: string, args: string[]) {
  return await launch(home, args).done;
}

test("CLI live ask -> exact IPC reply -> original ACP continuation; saved old reply cannot replay", async () => {
  for (const optionId of ["allow", "reject"]) {
    const home = await mkdtemp(path.join(os.tmpdir(), "acpx-permission-cli-"));
    const base = [
      "--agent",
      `${JSON.stringify(process.execPath)} ${JSON.stringify(peer)}`,
      "--cwd",
      home,
      "--format",
      "json",
      "--ttl",
      "0",
    ];
    try {
      const created = await command(home, [...base, "sessions", "new", "--name", "exact"]);
      assert.equal(created.code, 0, created.error);
      const original = launch(home, [
        ...base,
        "--permission-replies",
        "--timeout",
        "10",
        "--prompt-retries",
        "0",
        "--permission-policy",
        JSON.stringify({ escalate: ["edit"] }),
        "prompt",
        "-s",
        "exact",
        "permission edit approved-product",
      ]);
      let buffer = "";
      const request = await new Promise<NativePermissionRequest>((resolve, reject) => {
        const timer = setTimeout(() => {
          original.child.kill();
          reject(new Error("legal ask never reached stdout"));
        }, 8000);
        original.child.stdout.on("data", (chunk: string) => {
          buffer += chunk;
          let lineEnd = buffer.indexOf("\n");
          while (lineEnd >= 0) {
            const line = buffer.slice(0, lineEnd);
            buffer = buffer.slice(lineEnd + 1);
            const event = JSON.parse(line) as { method?: string; params?: NativePermissionRequest };
            if (event.method === "dual/permission/request" && event.params) {
              clearTimeout(timer);
              resolve(event.params);
            }
            lineEnd = buffer.indexOf("\n");
          }
        });
      });
      const { schema: _schema, rawJson: _raw, ...identity } = request;
      const reply = {
        ...identity,
        optionId,
        commitRef: "offline-fixture-C",
        commitSha256: "a".repeat(64),
      };
      const result = await command(home, [
        ...base,
        "permission-reply",
        "-s",
        "exact",
        "--reply",
        JSON.stringify(reply),
      ]);
      assert.equal(result.code, 0, result.error);
      assert.equal((JSON.parse(result.output) as { consumed: boolean }).consumed, true);
      const completed = await original.done;
      assert.equal(
        completed.code,
        optionId === "reject" ? 5 : 0,
        completed.error + completed.output,
      );
      assert.match(completed.output, new RegExp(`permission selected:${optionId}`));
      const repeated = await command(home, [
        ...base,
        "permission-reply",
        "-s",
        "exact",
        "--reply",
        JSON.stringify(reply),
      ]);
      assert.notEqual(repeated.code, 0, "ended request cannot regain IPC authority");
    } finally {
      await command(home, [...base, "sessions", "close", "exact"]);
      await rm(home, { recursive: true, force: true });
    }
  }
});
