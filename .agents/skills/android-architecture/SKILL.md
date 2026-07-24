---
name: android-architecture
description: Enforces LogFox Android architecture — modified TEA (Reducer/EffectHandler/Store), api/impl/presentation module boundaries, ViewState mapping, passive UI containers, Hilt `@Binds` conventions. Use when creating or modifying feature modules, adding reducers/effect handlers/view state mappers, setting up navigation via side effects, or reviewing multi-file architectural changes in this repo.
---

# Android Architecture

Architecture rules and patterns for LogFox feature code. Pairs with `CLAUDE.md` (which defines module layout, dependency direction, and naming) — this skill adds the TEA pattern, ViewState/UI split, and concrete examples.

## When to use

- Creating a new `feature/<name>/{api,impl,presentation}` module
- Adding or modifying a `Reducer`, `EffectHandler`, `ViewStateMapper`, or `BaseStoreViewModel` subclass
- Wiring navigation as a `SideEffect`
- Setting up Hilt `@Binds` for a new repository or use case
- Reviewing PRs that touch module boundaries, DI, or state management

## When NOT to use

- Single-file bug fixes that don't touch state, DI, or module boundaries
- String, drawable, translation, or other resource-only changes
- Gradle/dependency version bumps
- Build-logic / convention-plugin edits (no feature logic involved)

## Workflow

1. **Read `references/reference-index.md`** (10 lines) to pick the relevant references for the task.
2. Load one or two focused references; pull in more only if scope expands.
3. For new features, read `references/example-feature.md` first — it's a full end-to-end walkthrough.
4. Implement the change with the smallest possible surface area. Follow the non-negotiable rules below.
5. **Run `references/checklist.md`** — each item is grep-able. Fix failures before reporting done.
6. **Validate the build**: `./gradlew :feature:<name>:presentation:compileDebugKotlin --quiet` (or the module you touched). Compilation is the fastest boundary check.

## Non-negotiable rules

These rules extend `CLAUDE.md`. If a rule here conflicts with CLAUDE.md, CLAUDE.md wins.

1. **Modified TEA**: immutable `State`, `Command`, `SideEffect`; pure `Reducer`; async `EffectHandler`. Use `withSideEffects(...)` / `noSideEffects()` helpers from `core.tea` — don't build `ReduceResult` by hand.
2. **`Store.send()` is Main-thread only.** `EffectHandler` may do background work but must re-enter commands on Main (`BaseStoreViewModel` already handles this).
3. **Both `State` and `ViewState` exist.** Mapping happens in `BaseStoreViewModel` via a `ViewStateMapper`. UI never sees `State`.
4. **UI is passive.** Fragments/Composables call `render(ViewState)` and `handleSideEffect(SideEffect)` only. No business logic, no direct repository/use case calls.
5. **Navigation and one-off UI actions are `SideEffect`s** emitted from the reducer and consumed in the container fragment.
6. **UI side effects get `Unit` branches in `EffectHandler`; business side effects get `else -> Unit` in `handleSideEffect`.** Every side effect is seen by both sides — each ignores what isn't theirs. See `references/viewstate-and-ui.md` (via `ui-container-pattern.md`).
7. **`impl` classes are `internal`.** Only `api` types are public. Presentation never imports `impl`.
8. **Hilt `@Binds` returns the interface**, never `*Impl`. See `references/di-and-reactive-flow.md`.
9. **Feature-prefixed names**: `FooViewModel`, `FooReducer`, `FooEffectHandler`, `FooViewStateMapper`, `FooState`, `FooViewState`, `FooCommand`, `FooSideEffect`.

## Reference map

- **Quick self-check before finalizing**: `references/checklist.md`
- **Index of all reference docs**: `references/reference-index.md`
- **Full worked example**: `references/example-feature.md`

## Edge cases

- **Legacy code violates the rules.** Make the smallest safe change. Document the gap in the PR description. Don't refactor out of scope.
- **Two rules conflict.** Prefer dependency boundaries and thread-safety first, naming/style last.
- **Hybrid Fragment + Compose feature.** One source of truth in the ViewModel. Both UI layers stay passive.
- **Expensive `ViewStateMapper`.** Pass `Dispatchers.Default` as `viewStateMappingDispatcher` to `BaseStoreViewModel`.
