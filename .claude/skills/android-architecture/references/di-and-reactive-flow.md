# DI and Reactive Flow

Hilt binding/scoping patterns and Flow exposure rules.

## `@Binds` module pattern

Repositories and use cases: `@Binds` in an `internal interface` module, return the interface, bind the `*Impl`.

Real example (`impl/di/RepositoriesModule.kt`):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoriesModule {

    @Binds
    fun bindCrashesRepository(impl: CrashesRepositoryImpl): CrashesRepository

    @Binds
    fun bindCrashLogRepository(impl: CrashLogRepositoryImpl): CrashLogRepository

    @Binds
    fun bindDisabledAppsRepository(impl: DisabledAppsRepositoryImpl): DisabledAppsRepository
}
```

Rules:

- Module is `internal interface`, not `object`.
- Return type is the `api` interface, never the `*Impl`.
- `*Impl` constructors are `@Inject constructor` — Hilt builds them.
- `SingletonComponent` for repositories and stateful data sources; `ViewModelComponent` for per-ViewModel wiring (e.g. `AppCrashesViewModelModule`).

## When to use `@Provides` instead of `@Binds`

Use `@Provides` only when you need to **configure construction**:

- Retrofit-style factory calls
- Third-party builders (`Room.databaseBuilder`, `OkHttpClient.Builder`)
- Pulling values out of `SavedStateHandle` with a qualifier (see `layers-and-modules.md`)

If you're just swapping an interface for its single implementation, always `@Binds`.

## Per-ViewModel nav-arg injection

When a ViewModel needs per-instance params (e.g. a package name from nav args), don't fish them out of `SavedStateHandle` inside the ViewModel. Declare a Hilt qualifier and a `ViewModelComponent`-scoped module in the presentation submodule:

```kotlin
// di/AppCrashesViewModelModule.kt
@Qualifier annotation class PackageName
@Qualifier annotation class AppName

@Module
@InstallIn(ViewModelComponent::class)
internal object AppCrashesViewModelModule {
    @Provides @PackageName
    fun providePackageName(savedStateHandle: SavedStateHandle): String =
        checkNotNull(savedStateHandle["package_name"])

    @Provides @AppName
    fun provideAppName(savedStateHandle: SavedStateHandle): String? =
        savedStateHandle["app_name"]
}
```

Then the ViewModel, reducer, and effect handler take them as normal constructor params:

```kotlin
@HiltViewModel
internal class AppCrashesViewModel @Inject constructor(
    @PackageName packageName: String,
    @AppName appName: String?,
    reducer: AppCrashesReducer,
    effectHandler: AppCrashesEffectHandler,
    viewStateMapper: AppCrashesViewStateMapper,
) : BaseStoreViewModel<...>(...)

internal class AppCrashesEffectHandler @Inject constructor(
    @PackageName private val packageName: String,
    // ...
) : EffectHandler<...>
```

Why: keeps `SavedStateHandle` key lookups in one place, makes the params unit-test-injectable, and lets the effect handler depend on them without re-reading the bundle.

## Scoping

- **Shared mutable state holders** (data sources with `MutableStateFlow`, in-memory caches, the crash detector) → `@Singleton`.
- **Stateless bindings** → unscoped.
- **Per-ViewModel helpers** → `ViewModelScoped` only when they hold state across recompositions; otherwise unscoped.

Scope by lifecycle ownership, not by "I want fewer allocations."

## TEA component injection

Reducers, effect handlers, and view state mappers are **constructor-injected** directly into the ViewModel — no list bindings, no qualifiers, no `@Provides`. Just `@Inject constructor()`.

Only use `IntoSet` / multi-binding when a single ViewModel needs several effect handlers that should all run against the same side-effect stream.

## Reactive data flow

`MutableStateFlow` is **private** in the `*Impl`. The interface exposes `Flow<T>` or `StateFlow<T>`, read-only.

**Bad:**

```kotlin
// api
interface CrashesRepository {
    val crashes: MutableStateFlow<List<AppCrash>>   // ❌ mutability leaks
}
```

**Good:**

```kotlin
// api
interface CrashesRepository {
    val crashes: Flow<List<AppCrash>>
}

// impl
internal class CrashesRepositoryImpl @Inject constructor(
    // ...
) : CrashesRepository {
    private val _crashes = MutableStateFlow<List<AppCrash>>(emptyList())
    override val crashes: Flow<List<AppCrash>> = _crashes.asStateFlow()
}
```

## Use cases over multiple repositories

Compose reactive state with `combine` in the use case, not in the ViewModel:

```kotlin
internal class GetAppScreenUseCaseImpl @Inject constructor(
    private val onboardingRepository: OnboardingRepository,
    private val authRepository: AuthRepository,
) : GetAppScreenUseCase {
    override operator fun invoke(): Flow<AppScreen> = combine(
        onboardingRepository.wasOnboardingCompleted,
        authRepository.isAuthenticated,
    ) { onboardingDone, isAuthenticated ->
        when {
            !onboardingDone -> AppScreen.Onboarding
            !isAuthenticated -> AppScreen.Auth
            else -> AppScreen.Main
        }
    }
}
```

Failable use case operations return `Result<T>`; failable repository operations may throw (the use case wraps them). See CLAUDE.md.

## Quick review checks

- [ ] No `*Impl` type appears as a return type in any Hilt module.
- [ ] No `MutableStateFlow` / `MutableSharedFlow` crosses the `api` boundary.
- [ ] Singletons hold state; stateless classes are unscoped.
- [ ] Use cases combine; ViewModels don't.
