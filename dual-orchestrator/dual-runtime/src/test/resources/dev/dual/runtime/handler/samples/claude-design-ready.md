# Design Document

## Goal

Add user authentication with JWT tokens.

## Non-Goals

- SSO integration
- Password reset flow

## Proposed Architecture

Add `AuthService` in the core module with JWT generation and validation.

## Test Strategy

Unit tests for token generation, integration test for login flow.

## Risks And Tradeoffs

Token expiry handling adds complexity.

```dual-control
decision: READY_FOR_ARCH_REVIEW
summary: JWT auth design with narrow scope, ready for Codex review
```
