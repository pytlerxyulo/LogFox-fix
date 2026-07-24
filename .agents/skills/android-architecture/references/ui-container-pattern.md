# UI Container Pattern

How `BaseStoreFragment` renders state and handles side effects. For navigation specifics see `navigation.md`.

## Container vs passive split

**Container** (= `BaseStoreFragment` subclass): owns the ViewModel, renders `ViewState`, handles `SideEffect`, wires adapter callbacks to `send(command)`.

**Passive** (custom views, leaf composables, `RecyclerView.Adapter`): render what they're given, expose callbacks. No `viewModelScope`, no repository access, no use cases, no `remember` as feature state source of truth.

## `BaseStoreFragment` contract

Two required overrides:

```kotlin
abstract fun render(state: ViewState)               // MUST be idempotent
abstract fun handleSideEffect(sideEffect: SideEffect)
```

`BaseStoreFragment` already:

- Collects `state` and `sideEffects` in `viewLifecycleOwner.lifecycleScope` using `repeatOnLifecycle(STARTED)`.
- Exposes `protected fun send(command: Command)` — use this from click listeners and adapter callbacks.
- Exposes `protected val binding: VB` between `onCreateView` and `onDestroyView`.
- Calls an optional `VB.onViewCreated(view, savedInstanceState)` hook for view setup.

Do **not** re-implement any of that in subclasses.

## Real example (trimmed from `AppCrashesFragment`)

```kotlin
@AndroidEntryPoint
internal class AppCrashesFragment : BaseStoreFragment<
    FragmentAppCrashesBinding,
    AppCrashesViewState,
    AppCrashesState,
    AppCrashesCommand,
    AppCrashesSideEffect,
    AppCrashesViewModel,
    >() {

    override val viewModel by viewModels<AppCrashesViewModel>()

    private val adapter = CrashesAdapter(
        click = { send(AppCrashesCommand.CrashClicked(it.lastCrashId)) },
        delete = { showAreYouSureDeleteDialog { send(AppCrashesCommand.DeleteCrash(it.lastCrashId)) } },
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAppCrashesBinding.inflate(inflater, container, false)

    override fun FragmentAppCrashesBinding.onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setupBackButtonForNavController()
        crashesRecycler.layoutManager = LinearLayoutManager(requireContext())
        crashesRecycler.adapter = this@AppCrashesFragment.adapter
    }

    override fun render(state: AppCrashesViewState) {
        binding.toolbar.title = state.appName ?: state.packageName
        adapter.submitList(state.crashes)
    }

    override fun handleSideEffect(sideEffect: AppCrashesSideEffect) {
        when (sideEffect) {
            is AppCrashesSideEffect.NavigateToCrashDetails -> {
                findNavController().navigate(
                    resId = Directions.action_appCrashesFragment_to_crashDetailsFragment,
                    args = bundleOf("crash_id" to sideEffect.crashId),
                )
            }
            // Business side effects — owned by EffectHandler
            else -> Unit
        }
    }
}
```

Things to copy from this example:

- Class is `internal`.
- `@AndroidEntryPoint` + `by viewModels()`.
- Adapter callbacks go straight to `send(Command...)`. No logic in between except confirmation dialogs (which are `ui/` helpers, not business code).
- `render` reads only from `state` — no `if (something changed)` tracking; `DiffUtil`/adapter handle that.
- `handleSideEffect` has an explicit `else -> Unit` comment reminding readers business effects are owned elsewhere.

## Anti-pattern contrast

**Bad — business logic in the fragment:**

```kotlin
override fun render(state: AppCrashesViewState) {
    binding.toolbar.title = state.appName ?: state.packageName
    // ❌ fetching data from the fragment
    lifecycleScope.launch {
        val extra = getAllCrashesFlowUseCase().first()
        adapter.submitList(state.crashes + extra)
    }
}
```

**Good — fragment is passive:**

```kotlin
override fun render(state: AppCrashesViewState) {
    binding.toolbar.title = state.appName ?: state.packageName
    adapter.submitList(state.crashes)   // already merged in the mapper
}
```

---

**Bad — leaf composable owns feature state:**

```kotlin
@Composable
fun CrashList(initialCrashes: List<Crash>) {
    var crashes by remember { mutableStateOf(initialCrashes) } // ❌ source of truth
    Button(onClick = { crashes = crashes.drop(1) }) { Text("Dismiss") }
    LazyColumn { items(crashes) { CrashRow(it) } }
}
```

**Good — leaf composable is passive:**

```kotlin
@Composable
fun CrashList(crashes: List<CrashItem>, onDismiss: (CrashItem) -> Unit) {
    LazyColumn {
        items(crashes, key = { it.id }) { crash ->
            CrashRow(crash, onDismiss = { onDismiss(crash) })
        }
    }
}
```

The container passes `state.crashes` and a callback that calls `send(CrashesCommand.Dismiss(it.id))`.

## Checklist (fragment review)

- [ ] `render` only reads `state` and calls view/adapter setters. No `launch`, no `when (someCondition)` against external state.
- [ ] `handleSideEffect` handles only UI-level effects, with `else -> Unit` for business effects.
- [ ] Adapter / composable callbacks route to `send(Command...)`, not to repositories.
- [ ] No `MutableStateFlow` in the fragment.
- [ ] Fragment class is `internal`.
