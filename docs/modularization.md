# Modularization — Interview Reference

> SpendWise project context. Covers single-module vs multi-module tradeoffs,
> when to modularize, and what the full module graph would look like.

---

## 1. What is Modularization?

Modularization splits one large Gradle project (`:app`) into multiple smaller Gradle modules,
each with its own `build.gradle.kts`, source set, and dependency graph.

```
// Single-module (current SpendWise state)
settings.gradle.kts:
  include(":app")

// Multi-module (future state)
settings.gradle.kts:
  include(":app")
  include(":core:domain")
  include(":core:database")
  include(":core:network")
  include(":feature:home")
  include(":feature:addexpense")
  include(":feature:reports")
  include(":feature:settings")
```

---

## 2. Current SpendWise State — Single Module ✓ (correct for now)

```
app/src/main/java/com/lakmalz/spendwise/
├── di/                    ← Hilt modules
├── domain/
│   ├── common/            ← UiState, AppException
│   ├── model/             ← Expense, ExpenseSummary
│   ├── repository/        ← ExpenseRepository (interface)
│   └── usecase/           ← 5 use cases
├── data/
│   ├── local/             ← AppDatabase, ExpenseDao, ExpenseEntity
│   └── repository/        ← ExpenseRepositoryImpl (to be built)
└── ui/
    ├── components/        ← Shared composables
    ├── navigation/        ← NavGraph, Screen routes
    ├── theme/             ← SpendwiseTheme, Color, Typography
    └── [screen folders]   ← home/, addexpense/, reports/, settings/
```

This is a **well-structured single module**. The package layout already mirrors
what multi-module would look like — migration will be straightforward later.

---

## 3. Why NOT Multi-Module Yet?

| Reason | Detail |
|---|---|
| Codebase is small | ~400 LOC at foundation stage. One developer. |
| Build time is fast | No benefit from parallel compilation with 1 module |
| Adds overhead | Every module needs its own `build.gradle.kts`, Hilt setup, navigation wiring |
| Slows feature velocity | Moving a class between modules requires updating `build.gradle.kts` in both |

**Rule of thumb:** Modularize when you feel the pain — slow builds, team merge conflicts, accidental cross-layer imports.

---

## 4. When TO Modularize

| Signal | Why it triggers modularization |
|---|---|
| Build takes > 2 minutes | Multi-module enables parallel Gradle task execution |
| 3+ developers on the same screen | Reduces merge conflicts — each team owns a module |
| You want instant run / dynamic delivery | Feature modules required for Play Feature Delivery |
| Accidental imports across layers | Module boundaries enforce compile-time layer separation |
| Reusing code in another app | Extract `:core:domain` as a shared library |

---

## 5. Target Multi-Module Structure for SpendWise

```
spendwise/
├── app/                         ← Entry point only (Application, MainActivity, NavGraph)
│
├── core/
│   ├── domain/                  ← Pure Kotlin, ZERO Android deps
│   │   └── build.gradle.kts    (kotlin("jvm") plugin only)
│   ├── database/                ← Room, DAOs, Entities, Migrations
│   │   └── build.gradle.kts    (android library + KSP + Room)
│   ├── network/                 ← Retrofit, OkHttp, interceptors
│   ├── datastore/               ← DataStore, UserPreferences
│   ├── ui/                      ← Shared Composables, theme, icons
│   └── testing/                 ← Shared test utilities, fakes, fixtures
│
└── feature/
    ├── home/                    ← HomeScreen, HomeViewModel
    ├── addexpense/              ← AddExpenseScreen, AddExpenseViewModel
    ├── reports/                 ← ReportsScreen, charts
    └── settings/                ← SettingsScreen, DataStore wiring
```

### Dependency Graph

```
         :app
          │
          ├── :feature:home ──────────┐
          ├── :feature:addexpense ────┤
          ├── :feature:reports ───────┤──── :core:domain
          └── :feature:settings ──────┤         │
                                      │    :core:database
                                      │    :core:datastore
                                      └──  :core:ui
```

**Key rule:** `:feature` modules can depend on `:core` modules.
`:core` modules must NEVER depend on `:feature` modules.
`:core:domain` depends on nothing.

---

## 6. Module Types in Android

| Type | Gradle Plugin | Can produce APK? | Use for |
|---|---|---|---|
| App module | `com.android.application` | Yes | Entry point (`:app`) |
| Android library | `com.android.library` | No (AAR) | Feature modules, core libraries |
| Pure Kotlin module | `org.jetbrains.kotlin.jvm` | No (JAR) | Domain layer — no Android deps |

`core:domain` should use `kotlin("jvm")` — this **enforces** the zero-Android rule at the
Gradle level. If you accidentally import `android.content.Context`, it won't compile.

---

## 7. Hilt in a Multi-Module Project

Each module that uses Hilt needs:
```kotlin
// every module's build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
}
dependencies {
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
}
```

Modules expose their Hilt bindings through `@InstallIn` modules. The `:app` module
collects the entire graph at compile time.

```kotlin
// core/database/di/DatabaseModule.kt
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase = ...
}
```

---

## 8. Navigation in a Multi-Module Project

Each feature module defines its own navigation **internally**.
The `:app` module (or a `:core:navigation` module) wires them together:

```kotlin
// feature/home/navigation/HomeNavigation.kt
fun NavGraphBuilder.homeGraph(navController: NavController) {
    composable<HomeRoute> { HomeScreen(navController) }
}

// app/NavGraph.kt — assembles all feature graphs
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        homeGraph(navController)
        addExpenseGraph(navController)
        reportsGraph(navController)
        settingsGraph(navController)
    }
}
```

This pattern keeps each feature self-contained. The `:app` module never knows about the
internals of `:feature:home`.

---

## 9. Build Speed Improvements from Modularization

```
// Single module — sequential compilation
:app compiles → (everything compiles together)

// Multi-module — parallel compilation
:core:domain     ─────────────┐
:core:database   ─────────────┤
:core:network    ─────────────┤──→ :feature:home ─┐
:core:ui         ─────────────┘                   ├──→ :app
                                :feature:reports ──┘
```

Gradle runs independent modules in parallel. If you only change `:feature:home`,
Gradle skips rebuilding `:feature:reports`, `:core:database`, etc. — only `:feature:home`
and `:app` recompile.

**In practice:** A 60-second clean build in single-module can become a 15-second incremental
build in multi-module, because only the changed module and its dependents recompile.

---

## 10. Migrating SpendWise from Single to Multi-Module

The current package structure maps directly to modules:

| Current package | Target module |
|---|---|
| `domain/model/`, `domain/usecase/`, `domain/repository/` | `:core:domain` |
| `data/local/` | `:core:database` |
| `data/repository/` | `:core:database` or `:data` |
| `di/DatabaseModule` | `:core:database` |
| `di/RepositoryModule` | `:core:database` |
| `ui/theme/`, `ui/components/` | `:core:ui` |
| `ui/home/` | `:feature:home` |

Migration steps:
1. Create the new Gradle module (folder + `build.gradle.kts`)
2. Move files
3. Update `import` statements
4. Add the new module as a dependency where needed
5. Remove old package from `:app`

---

## 11. Interview Quick-Fire Answers

**Q: What is modularization?**
> Splitting a Gradle project into multiple modules. Each module compiles independently, can be parallelized, and has strict dependency boundaries enforced at compile time.

**Q: When should you modularize?**
> When build times hurt, when multiple teams work on the same codebase, or when you want Play Feature Delivery (dynamic feature modules). For a single-developer MVP, a well-structured single module is fine.

**Q: What is the difference between an app module and a library module?**
> App module uses `com.android.application` plugin and produces an APK. Library module uses `com.android.library` and produces an AAR. Feature and core modules are always library modules — only `:app` is an application module.

**Q: How do you enforce Clean Architecture layer separation with modules?**
> The `:core:domain` module uses the `kotlin("jvm")` plugin — no Android dependencies are available. If a developer accidentally imports a Room class into a Use Case, the build fails. Module boundaries provide compile-time enforcement.

**Q: How does Hilt work across modules?**
> Each module defines its own `@Module` with `@InstallIn`. Hilt collects all modules at compile time and builds the full DI graph. The `@HiltAndroidApp` in `:app` triggers this collection.

**Q: How does Navigation work in a multi-module app?**
> Each feature module defines an extension function on `NavGraphBuilder` (e.g., `fun NavGraphBuilder.homeGraph()`). The `:app` module calls all these extensions inside its `NavHost`. Features stay self-contained — `:app` only assembles them.

**Q: What is the risk of over-modularizing?**
> Too many small modules increase configuration overhead, make refactors harder (moving a class = updating multiple `build.gradle.kts`), and can actually slow builds if modules are too fine-grained (Gradle task setup overhead). Modularize along feature lines, not class lines.

**Q: SpendWise is single-module — is that a problem?**
> No. At the foundation stage with one developer and ~400 LOC, a single well-structured module is the right choice. The package layout already mirrors a multi-module structure, so extracting modules later is a mechanical refactor — move files, update imports, add Gradle config. The architecture is ready for it; the timing isn't yet.
