# TEA and ViewState

Core TEA primitives, store/thread-safety rules, and the `ViewState` mapping contract. For UI-side rules (rendering, side-effect consumption) see `ui-container-pattern.md`.

## The primitives

From `core.tea`:

```kotlin
interface Reducer<State, Command, SideEffect> {
    fun reduce(state: State, command: Command): ReduceResult<State, SideEffect>
}

interface EffectHandler<SideEffect, Command> : Closeable {
    suspend fun handle(effect: SideEffect, onCommand: suspend (Command) -> Unit)
    override fun close() = Unit
}

data class ReduceResult<State, SideEffect>(
    val state: State,
    val sideEffects: List<SideEffect> = emptyList(),
)
```

Helpers — **always use these instead of building `ReduceResult` by hand:**

```kotlin
fun <S, E> S.noSideEffects(): ReduceResult<S, E>
fun <S, E> S.withSideEffects(vararg sideEffects: E): ReduceResult<S, E>
```

## Reducer example (real, from `CrashDetailsReducer`)

```kotlin
internal class CrashDetailsReducer @Inject constructor()
    : Reducer<CrashDetailsState, CrashDetailsCommand, CrashDetailsSideEffect> {

    override fun reduce(state: CrashDetailsState, command: CrashDetailsCommand) = when (command) {
        is CrashDetailsCommand.CrashLoaded -> state
            .copy(crash = command.crash, crashLog = command.crashLog)
            .noSideEffects()

        is CrashDetailsCommand.WrapLinesClicked -> state.withSideEffects(
            CrashDetailsSideEffect.SetWrapCrashLogLines(wrap = !state.wrapCrashLogLines),
        )

        is CrashDetailsCommand.ConfirmDelete -> {
            val appCrash = state.crash ?: return@when state.noSideEffects()
            state.withSideEffects(
                CrashDetailsSideEffect.DeleteCrash(appCrash),
                CrashDetailsSideEffect.Close,
            )
        }
    }
}
```

Reducer rules:

- Pure and deterministic. No clocks, no I/O, no `viewModelScope`, no random.
- Exhaustive `when` over sealed `Command` type — no `else` branch.
- If the command doesn't affect state, return `state.noSideEffects()`, never mutate.
- Guard conditions (nullable fields) return `state.noSideEffects()` — never throw.

## EffectHandler rules

- Handles every `SideEffect` variant. UI-only effects (navigation, dialogs) end with `Unit` — see `ui-container-pattern.md`.
- May use suspend APIs freely; background dispatchers are OK. `BaseStoreViewModel` guarantees feedback commands re-enter on Main.
- Emit feedback by calling `onCommand(...)` — never by touching state directly.
- Long-running flows (`.collect { }`) are fine — the store cancels handlers via `close()` on `onCleared()`.

Example feedback pattern:

```kotlin
internal class AppCrashesEffectHandler @Inject constructor(
    private val getAllCrashesFlowUseCase: GetAllCrashesFlowUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : EffectHandler<AppCrashesSideEffect, AppCrashesCommand> {

    override suspend fun handle(
        effect: AppCrashesSideEffect,
        onCommand: suspend (AppCrashesCommand) -> Unit,
    ) {
        when (effect) {
            is AppCrashesSideEffect.LoadCrashes -> {
                getAllCrashesFlowUseCase()
                    .flowOn(defaultDispatcher)
                    .collect { onCommand(AppCrashesCommand.CrashesLoaded(it)) }
            }
            // UI side effect — handled by Fragment
            is AppCrashesSideEffect.NavigateToCrashDetails -> Unit
        }
    }
}
```

## `Store.send()` thread-safety

- `Store.send()` reads and writes `_state.value` without a lock. It **must** be called from Main.
- `BaseStoreViewModel` re-dispatches effect-handler feedback through `Dispatchers.Main.immediate` automatically. Don't call `store.send` directly from a background thread — go through `viewModel.send(cmd)` from the UI, or use `onCommand(...)` from inside an effect handler.

## `ViewStateMapper` contract

```kotlin
interface ViewStateMapper<State, ViewState> {
    fun map(state: State): ViewState
}
```

Rules:

- Pure, no side effects, no suspend — `fun map(state): ViewState`.
- Injected into the `ViewModel`, never into the Fragment.
- Format strings, dates, and locale-aware bits live **here**, not in the reducer (reducer stays domain-only) and not in the fragment (fragment stays passive).
- Expensive mappers: pass `viewStateMappingDispatcher = Dispatchers.Default` to `BaseStoreViewModel`.

Real example:

```kotlin
internal class AppCrashesViewStateMapper @Inject constructor(
    private val dateTimeFormatter: DateTimeFormatter,
) : ViewStateMapper<AppCrashesState, AppCrashesViewState> {
    override fun map(state: AppCrashesState) = AppCrashesViewState(
        packageName = state.packageName,
        appName = state.appName,
        crashes = state.crashes.map { it.toPresentationModel(it.formattedDate()) },
    )

    private fun AppCrashesCount.formattedDate() =
        "${dateTimeFormatter.formatDate(lastCrash.dateAndTime)} " +
            dateTimeFormatter.formatTime(lastCrash.dateAndTime)
}
```

## `BaseStoreViewModel` contract

```kotlin
abstract class BaseStoreViewModel<ViewState, State, Command, SideEffect>(
    initialState: State,
    reducer: Reducer<State, Command, SideEffect>,
    effectHandlers: List<EffectHandler<SideEffect, Command>>,
    viewStateMapper: ViewStateMapper<State, ViewState>,
    initialSideEffects: List<SideEffect> = emptyList(),
    viewStateMappingDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel()
```

What it gives you:

- `val state: StateFlow<ViewState>` — mapped, cold-start-safe (initial value is `mapper.map(initialState)`).
- `val sideEffects: Flow<SideEffect>` — consumed by the container fragment.
- Cancels the store automatically in `onCleared()`.
- `initialSideEffects` are dispatched on init — use this to kick off startup flows (e.g. `listOf(FooSideEffect.LoadData)`) instead of polluting the fragment's `onViewCreated`.

Do **not**:

- Extend `ViewModel` directly for TEA features.
- Expose `MutableStateFlow`.
- Call `viewModelScope.launch { }` in the ViewModel — that's the effect handler's job.
