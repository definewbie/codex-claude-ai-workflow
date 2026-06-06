import { STATUSES } from "./issueWorkflow.js";

export function summarizeIssues(issues) {
  const counts = Object.fromEntries(Object.values(STATUSES).map((status) => [status, 0]));
  const blocked = [];

  for (const issue of issues) {
    if (!counts.hasOwnProperty(issue.status)) {
      throw new Error(`Unknown issue status: ${issue.status}`);
    }
    counts[issue.status] += 1;
    if (issue.status === STATUSES.BLOCKED) {
      blocked.push({ id: issue.id, title: issue.title });
    }
  }

  return {
    total: issues.length,
    counts,
    blocked
  };
}
