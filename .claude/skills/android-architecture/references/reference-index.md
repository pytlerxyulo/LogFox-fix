# Architecture Reference Index

Load only the references needed for the current task.

- `example-feature.md` — full end-to-end walkthrough of a minimal LogFox feature (State, Command, SideEffect, Reducer, EffectHandler, ViewStateMapper, ViewModel, Fragment, Hilt module). **Start here for any new feature.**
- `tea-and-viewstate.md` — TEA primitives (`Reducer`, `Store`, `EffectHandler`, `ReduceResult` helpers), `BaseStoreViewModel` contract, `ViewStateMapper` rules, thread-safety.
- `ui-container-pattern.md` — `BaseStoreFragment` usage, `render` / `handleSideEffect` split, anti-patterns with code.
- `navigation.md` — side-effect-driven navigation, consuming nav effects in containers.
- `layers-and-modules.md` — LogFox-specific module deltas on top of `CLAUDE.md` (internal visibility, qualifier injection, package roots). Skip if `CLAUDE.md` already answers the question.
- `di-and-reactive-flow.md` — Hilt `@Binds` module pattern, scoping, `Flow`/`StateFlow` leakage rules.
- `checklist.md` — grep-able compliance checks to run before finalizing.

## Suggested load order

1. For **new features**: `example-feature.md` → `tea-and-viewstate.md` → `checklist.md`.
2. For **bug fixes in state/UI**: `tea-and-viewstate.md` or `ui-container-pattern.md`, whichever matches the symptom.
3. For **DI or module-boundary questions**: `di-and-reactive-flow.md` + `layers-and-modules.md`.
