export const STATUSES = Object.freeze({
  OPEN: "OPEN",
  IN_PROGRESS: "IN_PROGRESS",
  BLOCKED: "BLOCKED",
  DONE: "DONE"
});

const ALLOWED_TRANSITIONS = Object.freeze({
  [STATUSES.OPEN]: [STATUSES.IN_PROGRESS],
  [STATUSES.IN_PROGRESS]: [STATUSES.BLOCKED, STATUSES.DONE],
  [STATUSES.BLOCKED]: [STATUSES.IN_PROGRESS],
  [STATUSES.DONE]: []
});

export function canTransition(fromStatus, toStatus) {
  assertKnownStatus(fromStatus);
  assertKnownStatus(toStatus);
  return ALLOWED_TRANSITIONS[fromStatus].includes(toStatus);
}

export function transitionIssue(issue, toStatus) {
  if (!issue || typeof issue !== "object") {
    throw new Error("issue is required");
  }
  assertKnownStatus(issue.status);
  assertKnownStatus(toStatus);

  if (!canTransition(issue.status, toStatus)) {
    throw new Error(`Cannot transition issue from ${issue.status} to ${toStatus}`);
  }

  return {
    ...issue,
    status: toStatus,
    updatedAt: new Date().toISOString()
  };
}

export function createIssue(id, title) {
  if (!id || !title) {
    throw new Error("id and title are required");
  }

  return {
    id,
    title,
    status: STATUSES.OPEN,
    createdAt: new Date().toISOString(),
    updatedAt: null
  };
}

function assertKnownStatus(status) {
  if (!Object.values(STATUSES).includes(status)) {
    throw new Error(`Unknown issue status: ${status}`);
  }
}
