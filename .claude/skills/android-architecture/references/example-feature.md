# Example Feature: End-to-End Walkthrough

This is a minimal but complete LogFox feature built using every architectural piece. Copy this structure when adding a new feature. Based on the real `feature/crashes/.../appcrashes` slice, trimmed for clarity.

**Feature**: show crashes for a single app, let the user delete one, tap to open details.

## File layout

```
feature/crashes/
  api/src/main/kotlin/com/f0x1d/logfox/feature/crashes/api/
    model/AppCrash.kt
    data/CrashesRepository.kt
    domain/GetAllCrashesFlowUseCase.kt
    domain/DeleteCrashUseCase.kt

  impl/src/main/kotlin/com/f0x1d/logfox/feature/crashes/impl/
    data/CrashesRepositoryImpl.kt
    domain/GetAllCrashesFlowUseCaseImpl.kt
    domain/DeleteCrashUseCaseImpl.kt
    di/RepositoriesModule.kt
    di/CrashesUseCaseModule.kt

  presentation/src/main/kotlin/com/f0x1d/logfox/feature/crashes/presentation/appcrashes/
    AppCrashesState.kt
    AppCrashesViewState.kt
    AppCrashesCommand.kt
    AppCrashesSideEffect.kt
    AppCrashesReducer.kt
    AppCrashesEffectHandler.kt
    AppCrashesViewStateMapper.kt
    AppCrashesViewModel.kt
    di/AppCrashesViewModelModule.kt
    ui/AppCrashesFragment.kt
```

One top-level type per file. Everything under `impl/` and `presentation/` is `internal`.

## 1. `api` — domain surface

```kotlin
// api/data/CrashesRepository.kt
interface CrashesRepository {
    val crashes: Flow<List<AppCrash>>
    suspend fun delete(id: Long)
}

// api/domain/GetAllCrashesFlowUseCase.kt
interface GetAllCrashesFlowUseCase {
    operator fun invoke(): Flow<List<AppCrash>>
}

// api/domain/DeleteCrashUseCase.kt
interface DeleteCrashUseCase {
    suspend operator fun invoke(id: Long): Result<Unit>
}
```

Rules reminder: interfaces only, `operator fun invoke`, failable ops return `Result<T>`.

## 2. `impl` — private machinery

```kotlin
// impl/data/CrashesRepositoryImpl.kt
internal class CrashesRepositoryImpl @Inject constructor(
    private val dao: CrashesDao,
) : CrashesRepository {
    override val crashes: Flow<List<AppCrash>> = dao.observeAll().map { it.toDomain() }
    override suspend fun delete(id: Long) { dao.delete(id) }
}

// impl/domain/DeleteCrashUseCaseImpl.kt
internal class DeleteCrashUseCaseImpl @Inject constructor(
    private val repository: CrashesRepository,
) : DeleteCrashUseCase {
    override suspend operator fun invoke(id: Long): Result<Unit> = runCatching {
        repository.delete(id)
    }
}

// impl/di/RepositoriesModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {
    @Binds fun bindCrashesRepository(impl: CrashesRepositoryImpl): CrashesRepository
}

// impl/di/CrashesUseCaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
internal interface CrashesUseCaseModule {
    @Binds fun bindGetAllCrashes(impl: GetAllCrashesFlowUseCaseImpl): GetAllCrashesFlowUseCase
    @Binds fun bindDeleteCrash(impl: DeleteCrashUseCaseImpl): DeleteCrashUseCase
}
```

## 3. `presentation` — TEA pieces

### State and ViewState

```kotlin
// AppCrashesState.kt
internal data class AppCrashesState(
    val packageName: String,
    val appName: String?,
    val crashes: List<AppCrashesCount>,
)

// AppCrashesViewState.kt
internal data class AppCrashesViewState(
    val packageName: String,
    val appName: String?,
    val crashes: List<AppCrashItem>, // presentation model — formatted date strings, etc.
)
```

### Command and SideEffect

```kotlin
// AppCrashesCommand.kt
internal sealed interface AppCrashesCommand {
    data class CrashesLoaded(val crashes: List<AppCrashesCount>) : AppCrashesCommand
    data class CrashClicked(val crashId: Long) : AppCrashesCommand
    data class DeleteCrash(val crashId: Long) : AppCrashesCommand
}

// AppCrashesSideEffect.kt
internal sealed interface AppCrashesSideEffect {
    data object LoadCrashes : AppCrashesSideEffect                   // business
    data class DeleteCrash(val crashId: Long) : AppCrashesSideEffect // business
    data class NavigateToCrashDetails(val crashId: Long) : AppCrashesSideEffect // UI
}
```

### Reducer

```kotlin
internal class AppCrashesReducer @Inject constructor()
    : Reducer<AppCrashesState, AppCrashesCommand, AppCrashesSideEffect> {

    override fun reduce(state: AppCrashesState, command: AppCrashesCommand) = when (command) {
        is AppCrashesCommand.CrashesLoaded ->
            state.copy(crashes = command.crashes).noSideEffects()

        is AppCrashesCommand.CrashClicked ->
            state.withSideEffects(AppCrashesSideEffect.NavigateToCrashDetails(command.crashId))

        is AppCrashesCommand.DeleteCrash ->
            state.withSideEffects(AppCrashesSideEffect.DeleteCrash(command.crashId))
    }
}
```

### EffectHandler

```kotlin
internal class AppCrashesEffectHandler @Inject constructor(
    @PackageName private val packageName: String,
    private val getAllCrashesFlowUseCase: GetAllCrashesFlowUseCase,
    private val deleteCrashUseCase: DeleteCrashUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : EffectHandler<AppCrashesSideEffect, AppCrashesCommand> {

    override suspend fun handle(
        effect: AppCrashesSideEffect,
        onCommand: suspend (AppCrashesCommand) -> Unit,
    ) {
        when (effect) {
            is AppCrashesSideEffect.LoadCrashes -> {
                getAllCrashesFlowUseCase()
                    .map { all -> all.filter { it.packageName == packageName }.map(::AppCrashesCount) }
                    .flowOn(defaultDispatcher)
                    .collect { onCommand(AppCrashesCommand.CrashesLoaded(it)) }
            }
            is AppCrashesSideEffect.DeleteCrash -> {
                deleteCrashUseCase(effect.crashId)
            }
            // UI side effect — Fragment handles this
            is AppCrashesSideEffect.NavigateToCrashDetails -> Unit
        }
    }
}
```

### ViewStateMapper

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

### ViewModel

```kotlin
@HiltViewModel
internal class AppCrashesViewModel @Inject constructor(
    @PackageName packageName: String,
    @AppName appName: String?,
    reducer: AppCrashesReducer,
    effectHandler: AppCrashesEffectHandler,
    viewStateMapper: AppCrashesViewStateMapper,
) : BaseStoreViewModel<
    AppCrashesViewState,
    AppCrashesState,
    AppCrashesCommand,
    AppCrashesSideEffect,
    >(
    initialState = AppCrashesState(
        packageName = packageName,
        appName = appName,
        crashes = emptyList(),
    ),
    reducer = reducer,
    effectHandlers = listOf(effectHandler),
    viewStateMapper = viewStateMapper,
    initialSideEffects = listOf(AppCrashesSideEffect.LoadCrashes), // auto-kickoff
)
```

### Nav-args module

```kotlin
@Qualifier annotation class PackageName
@Qualifier annotation class AppName

@Module
@InstallIn(ViewModelComponent::class)
internal object AppCrashesViewModelModule {
    @Provides @PackageName
    fun providePackageName(h: SavedStateHandle): String = checkNotNull(h["package_name"])
    @Provides @AppName
    fun provideAppName(h: SavedStateHandle): String? = h["app_name"]
}
```

### Fragment

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

    override fun inflateBinding(i: LayoutInflater, c: ViewGroup?) =
        FragmentAppCrashesBinding.inflate(i, c, false)

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
            else -> Unit
        }
    }
}
```

## How a user interaction flows through the system

1. User taps a crash row → `CrashesAdapter` click callback
2. → `send(AppCrashesCommand.CrashClicked(id))`
3. → `AppCrashesReducer.reduce` returns `state.withSideEffects(NavigateToCrashDetails(id))`
4. → Store puts the effect in both the side-effect channel **and** calls `effectHandler.handle(...)`
5. → `AppCrashesEffectHandler` matches `NavigateToCrashDetails -> Unit` (not its job)
6. → Fragment's `handleSideEffect` matches and calls `findNavController().navigate(...)`

For data loading:

1. `initialSideEffects = listOf(LoadCrashes)` fires on ViewModel init
2. → `AppCrashesEffectHandler.handle(LoadCrashes)` collects the use case on `defaultDispatcher`
3. → Each emission → `onCommand(CrashesLoaded(...))` → Main dispatcher → `Store.send(CrashesLoaded)`
4. → `Reducer` merges the new list into state
5. → `ViewStateMapper.map` formats dates → `StateFlow<ViewState>` emits → Fragment `render` → adapter updates.

## Checklist for new features

Run through `checklist.md` after you've built all the files above.
