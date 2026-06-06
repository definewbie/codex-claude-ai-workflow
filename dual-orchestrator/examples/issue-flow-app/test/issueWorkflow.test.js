import assert from "node:assert/strict";
import test from "node:test";

import { canTransition, createIssue, STATUSES, transitionIssue } from "../src/issueWorkflow.js";
import { summarizeIssues } from "../src/dashboard.js";

test("new issues start open", () => {
  const issue = createIssue("ISS-1", "Add review state");

  assert.equal(issue.status, STATUSES.OPEN);
  assert.equal(issue.updatedAt, null);
});

test("allowed transitions move an issue forward", () => {
  const issue = createIssue("ISS-2", "Wire dashboard");

  const inProgress = transitionIssue(issue, STATUSES.IN_PROGRESS);
  const blocked = transitionIssue(inProgress, STATUSES.BLOCKED);
  const resumed = transitionIssue(blocked, STATUSES.IN_PROGRESS);
  const done = transitionIssue(resumed, STATUSES.DONE);

  assert.equal(done.status, STATUSES.DONE);
});

test("invalid transitions are rejected", () => {
  const issue = createIssue("ISS-3", "Skip implementation");

  assert.equal(canTransition(issue.status, STATUSES.DONE), false);
  assert.throws(() => transitionIssue(issue, STATUSES.DONE), /Cannot transition/);
});

test("dashboard summarizes issue status and blocked work", () => {
  const first = transitionIssue(createIssue("ISS-4", "Build report"), STATUSES.IN_PROGRESS);
  const second = transitionIssue(
    transitionIssue(createIssue("ISS-5", "Waiting on copy"), STATUSES.IN_PROGRESS),
    STATUSES.BLOCKED
  );

  const summary = summarizeIssues([first, second]);

  assert.equal(summary.total, 2);
  assert.equal(summary.counts.IN_PROGRESS, 1);
  assert.equal(summary.counts.BLOCKED, 1);
  assert.deepEqual(summary.blocked, [{ id: "ISS-5", title: "Waiting on copy" }]);
});
