# Modern Android Architecture — Interview Reference

> SpendWise project context. Covers Clean Architecture, MVI, MVVM, Jetpack stack,
> and how they all connect. Use this to answer architecture questions in interviews.

---

## 1. The Three-Layer Architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                         │
│   Composables, ViewModels, UiState, Navigation      │
├─────────────────────────────────────────────────────┤
│                  Domain Layer                       │
│   Use Cases, Repository Interfaces, Domain Models   │
│   (pure Kotlin — zero Android imports)              │
├─────────────────────────────────────────────────────┤
│                   Data Layer                        │
│   RepositoryImpl, Room, DAOs, DataStore, Retrofit   │
└─────────────────────────────────────────────────────┘
```

**Rule:** Dependencies only flow **downward**.
- UI depends on Domain
- Domain depends on **nothing** (no Android, no Room, no Retrofit)
- Data depends on Domain (implements its interfaces)

If you ever see a Room import inside a ViewModel or Use Case, that is an architecture violation.

---

## 2. Why Clean Architecture?

| Without Clean Architecture | With Clean Architecture |
|---|---|
| ViewModel imports `@Dao` directly | ViewModel calls a Use Case only |
| Business logic scattered in ViewModels | All logic in Use Cases |
| Changing the database breaks ViewModels | Swapping Room for API never touches UI |
| Hard to unit-test | Use Cases are pure Kotlin — JUnit only, no emulator |
| Hard to reuse logic | Use Cases reusable across ViewModels/screens |

---

## 3. Domain Layer Deep Dive

### What lives here
- **Domain models** — pure `data class`, no annotations
- **Repository interfaces** — contracts, not implementations
- **Use Cases** — one class, one action, one `invoke()` function

### Domain model vs Room entity

```kotlin
// domain/model/Expense.kt — pure Kotlin, no annotations
data class Expense(
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)

// data/local/ExpenseEntity.kt — Room-specific
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)
```

**Why two classes?** If the database schema changes (rename a column, split a table),
only `ExpenseRepositoryImpl` and the mapper change. The ViewModel never knows.

### Mapper — lives in the data layer
```kotlin
// data/local/mapper/ExpenseMapper.kt
fun ExpenseEntity.toDomain() = Expense(id, amount, category, note, date)
fun Expense.toEntity() = ExpenseEntity(id, amount, category, note, date)
```

### Use Case pattern
```kotlin
class AddExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense): Long {
        if (expense.amount <= 0) throw AppException.InvalidAmountException()
        if (expense.category.isBlank()) throw AppException.EmptyCategoryException()
        return repository.insertExpense(expense)
    }
}
```

`operator fun invoke()` lets you call it as `addExpense(expense)` instead of
`addExpense.execute(expense)` — cleaner and idiomatic Kotlin.

---

## 4. Data Layer Deep Dive

### Repository interface (domain) vs implementation (data)

```kotlin
// domain/repository/ExpenseRepository.kt — the contract
interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun searchExpenses(query: String, category: String): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense): Long
    suspend fun deleteExpense(expense: Expense)
    suspend fun deleteAllExpenses()
}

// data/repository/ExpenseRepositoryImpl.kt — the implementation
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> =
        dao.getAllExpenses().map { list -> list.map { it.toDomain() } }

    override suspend fun insertExpense(expense: Expense): Long =
        dao.insertExpense(expense.toEntity())

    override suspend fun deleteExpense(expense: Expense) =
        dao.deleteExpense(expense.toEntity())
}
```

### Why `@Binds` for the repository

```kotlin
// di/RepositoryModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindExpenseRepository(
        impl: ExpenseRepositoryImpl
    ): ExpenseRepository
}
```

`@Binds` tells Hilt: "whenever someone asks for `ExpenseRepository`, give them `ExpenseRepositoryImpl`."
No `@Provides` needed — Hilt can construct `ExpenseRepositoryImpl` itself since it has `@Inject constructor`.

---

## 5. UI Layer — MVI Architecture

### MVI vs MVVM

| | MVVM | MVI |
|---|---|---|
| State | Multiple `LiveData`/`StateFlow` fields | **Single** `UiState` data class |
| Events | Direct method calls | `Intent` objects (or sealed class) |
| Predictability | State can drift if multiple sources update | Single source of truth — state is always deterministic |
| Debugging | Hard — which field changed? | Easy — log the entire `UiState` |
| Testing | Mock individual observables | Assert whole state transitions |

SpendWise uses **MVI with StateFlow**:

```kotlin
// ui/home/HomeUiState.kt
data class HomeUiState(
    val expenses: List<Expense> = emptyList(),
    val summary: ExpenseSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

// ui/home/HomeViewModel.kt
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllExpenses: GetAllExpensesUseCase,
    private val getSummary: GetExpenseSummaryUseCase,
    private val addExpense: AddExpenseUseCase,
    private val deleteExpense: DeleteExpenseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadExpenses()
    }

    private fun loadExpenses() {
        viewModelScope.launch {
            getAllExpenses()
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { expenses ->
                    _uiState.update { it.copy(expenses = expenses, isLoading = false) }
                }
        }
    }

    fun onAddExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                addExpense(expense)
            } catch (e: AppException) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
```

### StateFlow vs SharedFlow

| | `StateFlow` | `SharedFlow` |
|---|---|---|
| Replays last value | Yes — always | Configurable (default: 0) |
| Use for | Screen state (always needs a value) | One-time events (navigate, show toast) |
| Initial value | Required | Not required |
| New subscriber gets | Current state immediately | Nothing (unless replayCache > 0) |

```kotlin
// One-time events — use SharedFlow
private val _events = MutableSharedFlow<AddExpenseEvent>()
val events: SharedFlow<AddExpenseEvent> = _events.asSharedFlow()

sealed class AddExpenseEvent {
    object NavigateBack : AddExpenseEvent()
    data class ShowError(val message: String) : AddExpenseEvent()
}
```

### Collecting Flow safely in Compose

```kotlin
// WRONG — leaks when app is in background
val state = viewModel.uiState.collectAsState()

// CORRECT — stops collecting when lifecycle is STOPPED
val state = viewModel.uiState.collectAsStateWithLifecycle()
```

`collectAsStateWithLifecycle()` is from `lifecycle-runtime-compose`. It respects the Android
lifecycle — stops collecting when the screen is backgrounded, resumes when it returns.

---

## 6. UiState Wrapper

SpendWise uses a generic `UiState<T>` sealed class for all screens:

```kotlin
// domain/common/UiState.kt
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val exception: AppException) : UiState<Nothing>()
}

sealed class AppException(message: String) : Exception(message) {
    class InvalidAmountException : AppException("Amount must be greater than zero")
    class EmptyCategoryException : AppException("Category cannot be empty")
    class DatabaseException(cause: Throwable) : AppException(cause.message ?: "Database error")
    class UnknownException(cause: Throwable) : AppException(cause.message ?: "Unknown error")
}
```

**Why domain-level exceptions?**
Raw exceptions like `IllegalArgumentException` or `SQLiteException` must never reach the UI.
`AppException` is a sealed hierarchy — the UI can exhaustively `when`-match every possible error
and display a meaningful message to the user.

---

## 7. Jetpack Stack in SpendWise

### Room + Flow
```kotlin
// DAO returns Flow — Room emits a new list every time the table changes
@Query("SELECT * FROM expenses ORDER BY date DESC")
fun getAllExpenses(): Flow<List<ExpenseEntity>>
```
You never need to manually "refresh" data. When an insert/delete happens, the Flow emits automatically.

### Hilt — Dependency Injection
```kotlin
// Three annotations you need to know:
@HiltAndroidApp   // on Application — starts the DI graph
@AndroidEntryPoint // on Activity/Fragment — injects into Android classes
@HiltViewModel     // on ViewModel — enables @Inject constructor in ViewModels
```

### Navigation Compose — Type-safe routes (Navigation 2.8+)
```kotlin
// OLD (string routes — error-prone, no type safety)
navController.navigate("expense_detail/42")

// NEW (type-safe @Serializable objects)
@Serializable data class ExpenseDetail(val expenseId: Long)
navController.navigate(ExpenseDetail(expenseId = 42))
```
The `@Serializable` approach catches argument type mismatches at compile time, not at runtime.

### DataStore vs SharedPreferences

| | SharedPreferences | DataStore |
|---|---|---|
| Threading | Blocking I/O on main thread possible | Coroutine-safe, always off-main-thread |
| Type safety | `getString()` returns nullable String | `Flow<T>` with typed keys |
| Error handling | Silent failures | Exceptions via Flow |
| Recommendation | Legacy | Use DataStore for all new code |

### WorkManager + Hilt
```kotlin
// Worker must use @HiltWorker + @AssistedInject
@HiltWorker
class BudgetCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val getExpenseSummary: GetExpenseSummaryUseCase   // injected!
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result { ... }
}
```
`HiltWorkerFactory` must be provided in `Application.getWorkManagerConfiguration()` —
this is already wired in `SpendWiseApplication`.

---

## 8. How the Layers Connect — Full Flow

```
User taps "Add Expense"
        │
        ▼
AddExpenseScreen (Composable)
  └── calls viewModel.onAddExpense(expense)
        │
        ▼
AddExpenseViewModel (@HiltViewModel)
  └── calls addExpense(expense)  ← Use Case invoke operator
        │
        ▼
AddExpenseUseCase
  ├── validates amount > 0        ← business rule
  ├── validates category not blank ← business rule
  └── calls repository.insertExpense(expense)
        │
        ▼
ExpenseRepositoryImpl
  └── calls dao.insertExpense(expense.toEntity())
        │
        ▼
ExpenseDao (Room)
  └── INSERT INTO expenses ...
        │
        ▼
Room emits updated list via Flow
        │
        ▼
GetAllExpensesUseCase collects the Flow
        │
        ▼
HomeViewModel updates _uiState
        │
        ▼
HomeScreen re-composes with new expense list
```

---

## 9. What "Modern" Means in 2025

| Old approach | Modern replacement | Why |
|---|---|---|
| `AsyncTask` | Coroutines + `viewModelScope` | Structured concurrency, lifecycle-aware |
| `LiveData` | `StateFlow` + `SharedFlow` | Works outside Android, testable |
| kapt | KSP | 2× faster compilation |
| Dagger 2 manually | Hilt | Opinionated, less boilerplate |
| XML layouts | Jetpack Compose | Declarative, single language |
| Navigation XML | Navigation Compose + `@Serializable` | Type-safe, no XML |
| SharedPreferences | DataStore Preferences | Coroutine-safe, no blocking I/O |
| `Handler` / `AlarmManager` | WorkManager | Guaranteed execution, battery-aware |
| `startActivityForResult` | ActivityResult API | Not deprecated |
| Fragments | Composables | No backstack bugs, testable |

---

## 10. Interview Quick-Fire Answers

**Q: What is Clean Architecture?**
> Three layers: UI, Domain, Data. Dependencies only flow downward. The domain layer contains pure Kotlin business logic — no Android imports. This makes it unit-testable without a device.

**Q: What is MVI?**
> Model-View-Intent. The screen has a single immutable `UiState` data class. Users trigger `Intent` events. The ViewModel reduces the intent + current state into a new state. All state changes flow through one place — easier to debug and test.

**Q: Why use Use Cases?**
> They encapsulate one business action. The ViewModel stays thin (no logic). Use Cases are reusable across ViewModels. They are plain Kotlin — testable with JUnit, no Robolectric needed.

**Q: StateFlow vs SharedFlow?**
> `StateFlow` always has a value — use it for screen state. `SharedFlow` has no initial value and doesn't replay by default — use it for one-time events like navigation or toasts.

**Q: Why KSP instead of kapt?**
> KSP (Kotlin Symbol Processing) runs natively in the Kotlin compiler pipeline — roughly 2× faster than kapt which had to compile to Java stubs first. All major libraries (Room, Hilt) now support KSP.

**Q: How does Hilt know which implementation to provide for an interface?**
> Through `@Binds` in a `@Module`. It tells Hilt: "whenever anyone asks for `ExpenseRepository`, give them `ExpenseRepositoryImpl`."

**Q: What is `collectAsStateWithLifecycle()`?**
> A Compose extension that stops collecting a Flow when the screen goes to the background (lifecycle state STOPPED). Plain `collectAsState()` keeps collecting, which wastes battery and can cause crashes.

**Q: Why separate domain model from Room entity?**
> If the DB schema changes — rename a column, add a relation, switch from Room to an API — only the data layer mapper changes. ViewModels and composables never touch Room types, so they need no changes.

**Q: Where should validation live — ViewModel, Use Case, or Repository?**
> Use Case. Not ViewModel (too close to UI, hard to reuse). Not Repository (too close to data, it shouldn't care about business rules). The domain layer owns all business rules.

**Q: What is `SharingStarted.WhileSubscribed(5000)`?**
> It keeps a SharedFlow active for 5 seconds after the last subscriber disappears. This handles configuration changes (rotation) without re-fetching data, but cancels background collection after 5 seconds if the app is truly backgrounded.

**Q: How does WorkManager integrate with Hilt?**
> Use `@HiltWorker` + `@AssistedInject` on the Worker. In the Application class, implement `Configuration.Provider` and return a `Configuration` built with `HiltWorkerFactory`. This replaces WorkManager's default initializer with Hilt-aware initialization.
