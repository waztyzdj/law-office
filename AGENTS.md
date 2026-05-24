# Project AI Working Rules

> This file is the root entry for AI coding in this repository. Read it before making any code change.

## Mandatory Reading

- Before changing anything under `frontend/`, read and follow `frontend/AGENTS.md`.
- Before changing anything under `backend/`, read and follow `backend/AGENTS.md`.
- Before changing shared docs or cross-cutting behavior, read `docs/07-quality/coding-standards.md`.
- When a change touches both frontend and backend, follow both frontend and backend rules.
- When a task involves database fields, enum values, status codes, permissions, menus, or field meanings, check `sql/建表脚本.sql` first and keep frontend, backend, SQL, and docs consistent.

## Required Workflow

1. Use `rg` / `rg --files` to find similar existing code before editing.
2. Prefer the smallest change that satisfies the request.
3. Reuse existing project patterns, helpers, base classes, components, API wrappers, and naming conventions.
4. Do not rewrite framework code, global configuration, generated assets, or unrelated modules unless the task explicitly requires it.
5. Preserve user changes in the working tree. Never revert unrelated changes.
6. Keep documentation in sync when changing API contracts, database schema, feature behavior, or deployment configuration.

## Verification

- Frontend TypeScript/Vue changes: run `pnpm.cmd typecheck` in `frontend/`.
- Frontend build, routing, global style, dependency, or registration changes: also run `pnpm.cmd build` in `frontend/`.
- Backend Java changes: run `.\mvnw.cmd -q -DskipTests compile` in `backend/`.
- If a verification command cannot run, explain why and state the remaining risk in the final response.

## Encoding

- Treat project text files as UTF-8.
- If Chinese comments, docs, or SQL text display as garbled output, re-read with explicit UTF-8 before editing.
- Do not rewrite an entire file just to fix terminal display encoding; first confirm the file's actual encoding.

