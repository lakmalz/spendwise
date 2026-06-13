# SpendWise — Development Roadmap

> One branch = one PR = one learning topic mapped to a real app feature.
> Each task builds on the previous. Follow the order.

---

## Current Foundation (already merged to master)

| Layer | What exists |
|---|---|
| Domain | `Expense`, `ExpenseSummary`, `ExpenseRepository` (interface), 5 use cases, `UiState<T>`, `AppException` |
| Data | `AppDatabase`, `ExpenseDao`, `ExpenseEntity` (Room) |
| DI | `DatabaseModule` (Hilt `@Provides` for DB + DAO) |
| App | `SpendWiseApplication` (@HiltAndroidApp + HiltWorkerFactory), `MainActivity` (@AndroidEntryPoint + SplashScreen) |
| Docs | `dependency-injection.md`, `domain-layer.md` |

---

## Branch Task List

---

### Task 1 — `feature/repository-layer`
**Topic:** Repository pattern · Data layer · Mapper functions

**What to build:**
- `data/repository/ExpenseRepositoryImpl.kt` — implements `ExpenseRepository`
- `data/local/mapper/ExpenseMapper.kt` — `ExpenseEntity.toDomain()` / `Expense.toEntity()`
- `di/RepositoryModule.kt` — `@Binds ExpenseRepository → ExpenseRepositoryImpl`

**Files to create:**
```
data/repository/ExpenseRepositoryImpl.kt
data/local/mapper/ExpenseMapper.kt
di/RepositoryModule.kt
```

**Key concepts:**
- `@Binds` vs `@Provides` — when to use each
- Why mapper lives in the data layer, not the domain layer
- `Flow<List<Expense>>` returned from `ExpenseDao` piped through `.map { it.toDomain() }`
- `withContext(Dispatchers.IO)` for suspend functions

**Definition of done:**
- App builds and installs with no crashes
- `ExpenseRepository` interface fully satisfied by `ExpenseRepositoryImpl`
- Hilt graph compiles (no missing bindings)

---

### Task 2 — `feature/navigation-setup`
**Topic:** Jetpack Navigation Compose · Type-safe routes (Navigation 2.8+)

**What to build:**
- `ui/navigation/Screen.kt` — `@Serializable` sealed class/objects for each route
- `ui/navigation/NavGraph.kt` — `NavHost` with composable destinations
- Update `MainActivity` to host `NavGraph`

**Files to create:**
```
ui/navigation/Screen.kt
ui/navigation/NavGraph.kt
```

**Key concepts:**
- Type-safe Navigation with `@Serializable` objects (no more string routes)
- `NavController` vs `NavBackStackEntry`
- Passing arguments between screens (no raw strings — typed objects)
- `hiltViewModel()` inside composables

**Definition of done:**
- Can navigate between Home and Add Expense screens (even if screens are empty stubs)
- Back stack works correctly
- No `IllegalArgumentException` from malformed routes

---

### Task 3 — `feature/home-screen`
**Topic:** MVI architecture · StateFlow · Jetpack Compose UI

**What to build:**
- `ui/home/HomeScreen.kt` — Composable with LazyColumn of expenses + FAB
- `ui/home/HomeViewModel.kt` — MVI: single `UiState<HomeUiState>` StateFlow
- `ui/home/HomeUiState.kt` — state data class

**Key concepts:**
- MVI: UI sends `Intent` → ViewModel updates `UiState` → UI re-renders
- `StateFlow` + `collectAsStateWithLifecycle()` (lifecycle-safe)
- `SharingStarted.WhileSubscribed(5000)` — why 5 seconds
- `stateIn()` to convert a cold Flow to a hot StateFlow
- Separation: ViewModel holds state, Screen only renders

**Definition of done:**
- Home screen displays list of expenses from Room (initially empty)
- Loading / Empty / Populated states all render correctly
- FAB navigates to Add Expense screen

---

### Task 4 — `feature/add-expense`
**Topic:** Form state · SharedFlow events · Input validation in Use Cases

**What to build:**
- `ui/addexpense/AddExpenseScreen.kt` — form: amount, category, note, date
- `ui/addexpense/AddExpenseViewModel.kt` — `SharedFlow<AddExpenseEvent>` for one-time events
- `AddExpenseUseCase` already validates — ViewModel catches `AppException` and maps to `UiState.Error`

**Key concepts:**
- `SharedFlow` for one-time navigation/toast events (vs `StateFlow` for screen state)
- Why validation lives in the Use Case, not the ViewModel
- `AppException` hierarchy → user-facing error messages
- `TextField` with `KeyboardOptions(keyboardType = KeyboardType.Decimal)`

**Definition of done:**
- Adding an expense saves to Room and pops back to Home screen
- Empty category and zero amount show inline error messages
- One-time "Saved" snackbar fires exactly once (no replay on re-composition)

---

### Task 5 — `feature/expense-detail`
**Topic:** AlertDialog · Edit flow · Delete confirmation

**What to build:**
- `ui/expensedetail/ExpenseDetailScreen.kt` — pre-filled edit form + delete button
- `ui/expensedetail/ExpenseDetailViewModel.kt`
- `DeleteExpenseUseCase` already exists — wire it up

**Key concepts:**
- Pre-populating a form from a nav argument (type-safe)
- Confirmation `AlertDialog` before destructive action
- Optimistic UI vs. waiting for DB confirmation

**Definition of done:**
- Tapping an expense opens a pre-filled detail screen
- Editing updates the expense in Room
- Delete shows confirmation dialog before removing

---

### Task 6 — `feature/search-filter`
**Topic:** `debounce` · `flatMapLatest` · Flow operators

**What to build:**
- Search bar in Home screen
- Category filter chips (All / Food / Transport / etc.)
- `SearchExpensesUseCase` already exists — wire up with debounce in ViewModel

**Key concepts:**
- `debounce(300)` — why search should wait for the user to stop typing
- `flatMapLatest` — cancels the previous search when a new query arrives
- `combine()` — merging query + category filter into a single Flow
- `FilterChip` in Compose

**Definition of done:**
- Search results update 300ms after typing stops
- Category filter works independently and combined with text search
- No unnecessary DB queries on every keystroke

---

### Task 7 — `feature/datastore-settings`
**Topic:** Jetpack DataStore · User preferences · Theme switching

**What to build:**
- `data/preferences/UserPreferencesRepository.kt` — DataStore reads/writes
- `ui/settings/SettingsScreen.kt` — toggle dark mode, select currency symbol
- Wire dynamic theme into `SpendwiseTheme`

**Key concepts:**
- DataStore vs SharedPreferences — why DataStore is coroutine-safe
- Proto DataStore vs Preferences DataStore — when to use each
- `Flow<UserPreferences>` collected in ViewModel
- Applying theme dynamically with `isSystemInDarkTheme()`

**Definition of done:**
- Dark/light mode persists across app restarts
- Currency symbol preference changes the format on Home and Reports screens
- No `StrictMode` violations from blocking I/O

---

### Task 8 — `feature/reports`
**Topic:** `GetExpenseSummaryUseCase` · Canvas drawing · Compose charts

**What to build:**
- `ui/reports/ReportsScreen.kt` — total spend, breakdown by category, bar/pie chart
- `ui/reports/ReportsViewModel.kt`
- Custom `Canvas` composable for bar chart (no third-party chart library)

**Key concepts:**
- `Canvas` API in Compose — `drawRect`, `drawArc`, `drawText`
- `ExpenseSummary.byCategory` → data for chart segments
- `animateFloatAsState` for chart enter animation
- Why drawing in Compose vs XML canvas is different

**Definition of done:**
- Reports screen shows total spend and a bar chart by category
- Chart animates in on first render
- Handles zero-data state gracefully

---

### Task 9 — `feature/security`
**Topic:** EncryptedSharedPreferences · BiometricPrompt · Security best practices

**What to build:**
- `data/security/EncryptedPreferencesDataSource.kt` — wraps `EncryptedSharedPreferences`
- `ui/auth/BiometricAuthManager.kt` — `BiometricPrompt` wrapper
- App lock screen shown on resume if biometric is enabled in Settings

**Key concepts:**
- `EncryptedSharedPreferences` vs plain DataStore — when encryption matters
- Android Keystore — keys never leave the secure hardware
- `BiometricPrompt` API — authenticators, callbacks
- Strong vs weak biometrics

**Definition of done:**
- Sensitive preferences (e.g., budget limit) stored encrypted
- Biometric prompt appears on app resume if enabled
- Cancelling biometric shows a fallback PIN/password prompt

---

### Task 10 — `feature/workmanager-notifications`
**Topic:** WorkManager · `@HiltWorker` · Notification channels

**What to build:**
- `worker/BudgetCheckWorker.kt` — `@HiltWorker`, checks if spending exceeds budget
- `di/WorkerModule.kt` — already wired via `HiltWorkerFactory` in `SpendWiseApplication`
- Notification channel + `NotificationCompat.Builder`
- Schedule in SettingsViewModel when budget is set

**Key concepts:**
- `@HiltWorker` + `@AssistedInject` — how Hilt injects into Workers
- `PeriodicWorkRequest` vs `OneTimeWorkRequest`
- `WorkManager` constraints — only run on Wi-Fi, battery not low
- Notification channels required for Android 8+

**Definition of done:**
- Daily check fires and sends a notification when monthly spend exceeds budget
- Worker survives app restart and device reboot
- Notification deep-links back to Reports screen

---

### Task 11 — `feature/unit-testing`
**Topic:** Unit tests · MockK · Turbine · Room in-memory DB

**What to build:**
- `test/usecase/AddExpenseUseCaseTest.kt` — validates happy path + error cases
- `test/usecase/GetExpenseSummaryUseCaseTest.kt`
- `test/viewmodel/HomeViewModelTest.kt` — uses `TestCoroutineScheduler`
- `androidTest/data/ExpenseDaoTest.kt` — Room in-memory DB

**Key concepts:**
- `MockK` vs Mockito — why MockK is idiomatic for Kotlin
- `Turbine` for testing `Flow` emissions
- `TestCoroutineScheduler` / `UnconfinedTestDispatcher`
- Room `inMemoryDatabaseBuilder` — faster than real file, wiped after test
- `@HiltAndroidTest` for instrumented tests

**Definition of done:**
- `AddExpenseUseCase` tests cover: valid input, zero amount, blank category
- `HomeViewModel` test verifies `UiState` transitions: Loading → Success
- DAO test verifies insert, query, delete round-trip

---

### Task 12 — `feature/code-quality`
**Topic:** Detekt · KtLint · GitHub Actions CI

**What to build:**
- `detekt.yml` — custom rule config
- `.editorconfig` — KtLint style rules
- `.github/workflows/ci.yml` — runs lint, detekt, ktlint, tests on every PR

**Key concepts:**
- Detekt vs KtLint — static analysis vs formatter
- GitHub Actions: `actions/checkout`, `setup-java`, Gradle cache
- Fail-fast CI: lint errors block merge
- Suppressing false positives with `@Suppress` (sparingly)

**Definition of done:**
- CI runs on every PR to master
- Detekt and KtLint pass on the existing codebase
- A failing lint check blocks the PR from merging

---

### Task 13 — `feature/performance`
**Topic:** Baseline Profile · LeakCanary · Compose recomposition metrics

**What to build:**
- `app/src/main/baseline-prof.txt` — generated with Macrobenchmark
- `LeakCanary` added to debug build only
- Compose compiler metrics enabled — identify unnecessary recompositions

**Key concepts:**
- Baseline Profile — pre-compiles hot code paths for faster startup
- LeakCanary — detects Activity/Fragment/ViewModel leaks automatically
- `@Stable` / `@Immutable` — telling the Compose compiler a class won't change
- Compose compiler report — `restartable`, `skippable`, `unstable` labels

**Definition of done:**
- Cold start time measured before and after Baseline Profile
- LeakCanary shows zero leaks on the Home → Add → Back flow
- At least one Composable optimised from `unstable` to `stable`

---

### Task 14 — `feature/release-pipeline`
**Topic:** R8/ProGuard · App signing · AAB CI/CD

**What to build:**
- `proguard-rules.pro` — rules for Hilt, Room, Retrofit, Gson
- `app/build.gradle.kts` — signing config from environment variables
- `.github/workflows/release.yml` — builds signed AAB, uploads to Play Store internal track

**Key concepts:**
- R8 vs ProGuard — R8 is the default since AGP 3.4, runs shrinking + obfuscation + optimization
- Why you need `@Keep` / `-keep` rules for Hilt and Room generated classes
- Keystore — never commit to git; pass via CI secrets
- `bundletool` — test AAB locally before uploading

**Definition of done:**
- Release build compiles without R8 stripping Hilt/Room generated code
- Signed AAB produced in CI without the keystore in source control
- App passes Android vitals baseline checks (no ANRs, no crashes on startup)

---

## Tech Stack Summary

| Layer | Library | Version |
|---|---|---|
| UI | Jetpack Compose BOM | 2024.09.00 |
| UI | Navigation Compose | 2.9.8 |
| UI | Paging 3 Compose | 3.3.2 |
| UI | Splash Screen | 1.2.0 |
| Architecture | Hilt | 2.59.2 |
| Architecture | ViewModel + Lifecycle | 2.8.2 |
| Persistence | Room | 2.8.4 |
| Persistence | DataStore Preferences | 1.1.1 |
| Background | WorkManager | 2.11.2 |
| Network | Retrofit + OkHttp | 2.11.0 / 4.12.0 |
| Testing | MockK | 1.13.11 |
| Testing | Turbine | 1.1.0 |
| Build | KSP | 2.2.10-2.0.2 |
| Build | AGP | 9.1.1 |
| Build | Kotlin | 2.2.10 |
