# Navigation via SideEffect

Navigation and one-off UI actions (dialogs, toasts, clipboard, intents) travel as `SideEffect`s emitted by the reducer and consumed in the container.

> For general fragment structure (`render`, `handleSideEffect`, passive-view rules) see `ui-container-pattern.md`.

## Flow

1. User interaction → adapter callback → `send(Command.SomeClick)`.
2. Reducer emits a `SideEffect.Navigate*` (or `.ShowDialog`, `.CopyText`, ...) with any required payload in the effect.
3. `BaseStoreFragment` receives it via `handleSideEffect(effect)` and calls `findNavController().navigate(...)`.
4. `EffectHandler` ignores UI effects with a `-> Unit` branch.

## Reducer side (real)

```kotlin
is CrashDetailsCommand.OpenAppInfoClicked -> {
    val appCrash = state.crash ?: return@when state.noSideEffects()
    state.withSideEffects(CrashDetailsSideEffect.OpenAppInfo(appCrash.packageName))
}
```

Notice the payload (`packageName`) is pre-computed by the reducer — the fragment gets a fully-formed effect and doesn't reach back into state.

## Fragment side (real)

```kotlin
override fun handleSideEffect(sideEffect: AppCrashesSideEffect) {
    when (sideEffect) {
        is AppCrashesSideEffect.NavigateToCrashDetails -> {
            findNavController().navigate(
                resId = Directions.action_appCrashesFragment_to_crashDetailsFragment,
                args = bundleOf("crash_id" to sideEffect.crashId),
            )
        }
        else -> Unit   // business effects owned by AppCrashesEffectHandler
    }
}
```

## Rules

- **Never** call `findNavController()` from an adapter callback, click listener, or effect handler. Navigation is a UI concern that must be serialized through the side-effect stream so that back-stack ordering and state changes stay consistent.
- **Never** put a `NavController` reference in a ViewModel, reducer, or effect handler.
- **Effects carry data, not lambdas.** A `SideEffect` is a pure data class. No function types, no `View` references.
- **`Directions` and nav graph IDs** come from the navigation module (`com.f0x1d.logfox.feature.navigation.api.Directions`) — don't hardcode IDs.
- **Confirmation dialogs** are usually two commands: `...Clicked` → reducer emits `ConfirmX` side effect → fragment shows the dialog → on confirm, fragment sends `Confirm...` command back to the store (which then does the real work).

## What counts as a "UI side effect"

UI side effects are handled by the Fragment's `handleSideEffect`:

- Navigation
- Dialogs (including `showAreYouSureDeleteDialog`)
- Toasts, snackbars
- Clipboard (`CopyText`)
- `startActivity` for system settings, share sheets, file pickers
- Focus requests, soft-keyboard toggles

Business side effects are handled by `EffectHandler`:

- Repository calls, `.collect { }` subscriptions
- Use case invocations
- File I/O, export pipelines
- Anything that produces a feedback `Command`
