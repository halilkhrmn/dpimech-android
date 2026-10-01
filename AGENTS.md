# AGENTS.md — DPIMech for Android

Guide for anyone (human or AI agent) working on this repo. **Read this first, then `docs/PLAN.md`
and `docs/PROGRESS.md`.**

Android companion of the desktop app [halilkhrmn/dpimech](https://github.com/halilkhrmn/dpimech):
per-app DPI bypass through `VpnService` + ByeDPI, no root, no remote server.

## Project docs — keep them current

| File | What it holds | When to update |
|---|---|---|
| `AGENTS.md` | Rules, architecture, commands, file map | When structure, commands or conventions change |
| `docs/PLAN.md` | Product plan, feature scope, phases | When scope or phases change |
| `docs/PROGRESS.md` | Phase checklist + dated work log | **At the end of every work session** |
| `docs/DECISIONS.md` | Numbered decisions with reasons | Whenever a non-obvious choice is made |

Work-log entries: newest on top, `### YYYY-MM-DD — short title`, then bullets for *done*, *verified how*, *open/next*.

## Rules

- Scope is fixed in `docs/PLAN.md`: ByeDPI only, no root mode, no remote tunnels, no Android TV.
- Keep F-Droid compatible: no Google Play Services, Firebase, analytics or crash reporters; no
  executable downloads at runtime; native code only from pinned submodules.
- Strategy data comes from the desktop repo's `strategies/default.json`; do not fork the format.
  If Android needs a change, change it there.
- User-visible strings go through Android resources (`values/`, `values-tr/`, `values-ru/`).

## Commands

To be filled in with the Gradle skeleton (Phase 1).
