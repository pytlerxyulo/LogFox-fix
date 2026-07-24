# Layers and Modules — LogFox-specific deltas

`CLAUDE.md` already covers module layout, dependency direction (`presentation -> api`, `impl -> api`, `:app` aggregates), naming (`<Feature>ViewModel`, `<Feature>Reducer`, ...), convention plugins (`logfox.android.feature[.compose]`), version catalog (`libs`), type-safe project accessors, and `--quiet`. Don't repeat it — read it there.

This file adds only what CLAUDE.md doesn't spell out.

## Visibility rules

- Classes in `impl/` are **`internal`**. Only types in `api/` are public.
- Data sources, DTOs, and mappers are `internal` in `impl/` too — they never cross the module boundary.
- Presentation modules: ViewModels, Reducers, EffectHandlers, Mappers, Fragments → all `internal`.
- Leaking an `impl` type as public is a bug; fix visibility before landing.

## Package roots

Each feature module has a single package root matching its Gradle path:

```
com.f0x1d.logfox.feature.<name>.api
com.f0x1d.logfox.feature.<name>.impl
com.f0x1d.logfox.feature.<name>.presentation
```

`android.namespace` in the module's `build.gradle.kts` must match the package root exactly. Nested packages (`...api.model`, `...api.data`, `...api.domain`, `...presentation.details`, ...) are fine.

For per-ViewModel nav-arg injection (`@PackageName`, `@AppName` etc.), see `di-and-reactive-flow.md`.

## Module-level file organization

Within a presentation submodule (e.g. `presentation/appcrashes/`), the flat layout is:

```
appcrashes/
  AppCrashesCommand.kt
  AppCrashesEffectHandler.kt
  AppCrashesReducer.kt
  AppCrashesSideEffect.kt
  AppCrashesState.kt
  AppCrashesViewModel.kt
  AppCrashesViewState.kt
  AppCrashesViewStateMapper.kt
  di/
    AppCrashesViewModelModule.kt
  ui/
    AppCrashesFragment.kt
```

One top-level type per file, file name equals type name — same as CLAUDE.md, listed here only so the layout is concrete.

## Plugins

Feature modules pick from (in `libs.plugins`):

- `logfox.android.library` — plain Android library
- `logfox.android.feature` — Android lib + Hilt
- `logfox.android.feature.compose` — Android lib + Hilt + Compose
- `logfox.kotlin.jvm` — pure Kotlin

Presentation modules usually use `logfox.android.feature` or `logfox.android.feature.compose`. `api` modules use `logfox.android.library` or `logfox.kotlin.jvm`. `impl` uses `logfox.android.feature` when it needs Hilt.
