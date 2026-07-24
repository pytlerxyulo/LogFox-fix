# Architecture Compliance Checklist

Run this pass before finalizing any multi-file change. Each check is phrased so you can verify it mechanically (grep, quick read, or compile). Fix failures before reporting done.

## 1. TEA correctness

- [ ] **Reducer is pure.** Grep the reducer file for `viewModelScope`, `launch`, `delay`, `runBlocking`, `System.currentTimeMillis`, `Random`, `Clock.` — **zero matches**.
- [ ] **Reducer uses the helpers.** Grep for `ReduceResult(` in the reducer file — zero matches. Only `withSideEffects(...)` and `noSideEffects()` should build results.
- [ ] **Reducer `when` is exhaustive over `Command`** (no `else ->` branch).
- [ ] **`Store.send()` is not called from a background dispatcher.** Only `BaseStoreViewModel.send` and the UI call it.
- [ ] **EffectHandler handles every SideEffect variant.** UI-only variants end with `-> Unit`.

## 2. ViewState boundary

- [ ] Feature has **both** `FooState.kt` and `FooViewState.kt`.
- [ ] `FooViewStateMapper.kt` exists, implements `ViewStateMapper<FooState, FooViewState>`, and is injected into the ViewModel (not the Fragment).
- [ ] Grep the `ui/` folder for `viewModel.state.value` or direct `FooState` imports — **zero matches**. The fragment only sees `FooViewState`.
- [ ] Mapper has no `suspend`, no `launch`, no I/O.

## 3. UI container / passive split

- [ ] Fragment extends `BaseStoreFragment<...>` and provides `render(state)` + `handleSideEffect(effect)`.
- [ ] Grep the fragment for `Repository`, `UseCase`, `lifecycleScope.launch` — should only appear for dialog helpers, not for data access.
- [ ] `render()` body contains no `if (state.x != previous)` diffing — rely on adapters/DiffUtil.
- [ ] `handleSideEffect` has `else -> Unit` (or handles all variants exhaustively).
- [ ] No custom view or leaf composable calls a repository or use case.

## 4. Module boundaries

- [ ] Grep `presentation/**` for `.impl.` — **zero matches**.
- [ ] Grep `impl/**` and `presentation/**` `.kt` files for top-level `class Foo` / `object Foo` — each should have `internal` visibility.
- [ ] `api/` exports interfaces and domain models only (no `*Impl`, no DTOs, no Room entities).
- [ ] No cross-feature `impl` dependency in any `build.gradle.kts` except `:app`.

## 5. DI

- [ ] Grep Hilt modules for `: [A-Z][a-zA-Z]*Impl` as return types — **zero matches**. `@Binds` returns the interface.
- [ ] Shared mutable state holders (data sources with `MutableStateFlow`) are `@Singleton`.
- [ ] Reducers / effect handlers / mappers are constructor-injected directly (no `@Provides`, no `@IntoSet` unless multi-handler is needed).
- [ ] Nav-arg qualifiers (`@PackageName`, etc.) live in the presentation submodule's `di/` package.

## 6. Reactive data flow

- [ ] Grep `api/**` for `MutableStateFlow` / `MutableSharedFlow` — **zero matches**.
- [ ] Repository interfaces expose `Flow<T>` / `StateFlow<T>` where reactive.
- [ ] Multi-source reactive composition happens in use cases (`combine { ... }`), not in ViewModels.

## 7. Navigation

- [ ] Grep reducers and effect handlers for `findNavController`, `NavController`, `Directions.` — **zero matches**.
- [ ] Navigation happens only in the fragment's `handleSideEffect`.
- [ ] Side-effect data classes carry plain data (no `View`, no lambdas, no `Context`).

## 8. File structure and naming

- [ ] One top-level type per file; file name matches type name.
- [ ] `android.namespace` in each module's `build.gradle.kts` matches the package root.
- [ ] Names follow `Foo{ViewModel,Reducer,EffectHandler,ViewStateMapper,State,ViewState,Command,SideEffect}`.

## 9. Build

- [ ] Version-catalog aliases used (`libs.plugins.logfox...`, `libs.androidx...`).
- [ ] Type-safe project accessors (`projects.feature.foo.api`, not string paths).
- [ ] Ran `./gradlew :feature:<name>:presentation:compileDebugKotlin --quiet` (or the affected module). Compilation passed.
- [ ] If tests exist: ran `./gradlew testDebugUnitTest --quiet`.

## Final validation

If any check failed, fix it and re-run the affected checks. Only then report the task done.
