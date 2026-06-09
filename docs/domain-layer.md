# Domain Layer — Android Clean Architecture

> Interview reference for the SpendWise project.

---

## 1. What is the Domain Layer?

The domain layer is the **middle layer** in Clean Architecture. It contains pure business logic —
no Android framework, no Room, no Retrofit. Just plain Kotlin.

```
┌─────────────────────────────────────┐
│            UI Layer                 │  Composables, ViewModels
├─────────────────────────────────────┤
│          Domain Layer               │  Use Cases, Repository Interfaces, Domain Models
├─────────────────────────────────────┤
│           Data Layer                │  Room, Retrofit, DAOs, Entities
└─────────────────────────────────────┘
```

**Key rule:** Dependencies only flow **downward**.
- UI depends on Domain
- Domain depends on nothing (no Android imports)
- Data depends on Domain (implements its interfaces)

---

## 2. Why Use a Domain Layer?

| Without Domain | With Domain |
|---|---|
| ViewModel calls Repository directly | ViewModel calls a Use Case |
| Business logic scattered in ViewModels | Business logic centralised in Use Cases |
| Hard to reuse logic across screens | Use Cases reusable anywhere |
| ViewModels are hard to unit test | Use Cases are pure Kotlin — trivial to test |
| Changing the database breaks ViewModels | Changing the database never touches ViewModels |

---

## 3. Domain Layer Structure in SpendWise

```
domain/
├── model/
│   └── Expense.kt                    ← pure Kotlin data class (no Room annotations)
├── repository/
│   └── ExpenseRepository.kt          ← interface (contract only)
└── usecase/
    ├── GetAllExpensesUseCase.kt       ← fetch all expenses reactively
    ├── AddExpenseUseCase.kt           ← validate + insert an expense
    ├── DeleteExpenseUseCase.kt        ← delete a single expense
    ├── SearchExpensesUseCase.kt       ← filter by query and category
    └── GetExpenseSummaryUseCase.kt    ← totals + category breakdown
```

---

## 4. Domain Model vs Entity

Two separate classes represent an expense:

| | `Expense` (domain) | `ExpenseEntity` (data) |
|---|---|---|
| Location | `domain/model/` | `data/local/` |
| Purpose | Business logic | Database table |
| Annotations | None | `@Entity`, `@PrimaryKey` |
| Used by | ViewModels, Use Cases | Room, Repository Impl |

```kotlin
// Domain model — pure Kotlin
data class Expense(
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)

// Data entity — Room specific
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)
```

The `ExpenseRepositoryImpl` maps between the two:
```kotlin
fun ExpenseEntity.toDomain() = Expense(id, amount, category, note, date)
fun Expense.toEntity() = ExpenseEntity(id, amount, category, note, date)
```

---

## 5. Repository Interface

The domain layer **defines the contract**. It never knows how data is stored.

```kotlin
// domain/repository/ExpenseRepository.kt
interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun searchExpenses(query: String, category: String): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense): Long
    suspend fun deleteExpense(expense: Expense)
    suspend fun deleteAllExpenses()
}
```

The data layer (`ExpenseRepositoryImpl`) implements this interface using Room.
Tomorrow you could swap Room for a remote API — ViewModels don't change.

---

## 6. Use Cases

Each use case = **one action** the app can perform. Follows the Single Responsibility Principle.

```kotlin
// GetAllExpensesUseCase — no business logic, just delegates
class GetAllExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<List<Expense>> = repository.getAllExpenses()
}

// AddExpenseUseCase — has business logic (validation)
class AddExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense): Long {
        require(expense.amount > 0) { "Amount must be greater than zero" }
        require(expense.category.isNotBlank()) { "Category cannot be empty" }
        return repository.insertExpense(expense)
    }
}

// GetExpenseSummaryUseCase — business logic (aggregation)
class GetExpenseSummaryUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(): Flow<ExpenseSummary> =
        repository.getAllExpenses().map { expenses ->
            ExpenseSummary(
                totalAmount = expenses.sumOf { it.amount },
                totalCount = expenses.size,
                byCategory = expenses.groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
            )
        }
}
```

---

## 7. How It All Connects

```
HomeViewModel
  │  @Inject
  ├── GetAllExpensesUseCase
  ├── AddExpenseUseCase          → ExpenseRepository (interface)
  ├── DeleteExpenseUseCase              ↓ implemented by
  └── GetExpenseSummaryUseCase   → ExpenseRepositoryImpl → ExpenseDao → Room DB
```

The ViewModel **never imports Room**. It only calls use cases.

---

## 8. Use Cases in the ViewModel

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getAllExpenses: GetAllExpensesUseCase,
    private val addExpense: AddExpenseUseCase,
    private val deleteExpense: DeleteExpenseUseCase,
    private val getSummary: GetExpenseSummaryUseCase
) : ViewModel() {

    val expenses = getAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val summary = getSummary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onAddExpense(expense: Expense) {
        viewModelScope.launch { addExpense(expense) }
    }

    fun onDeleteExpense(expense: Expense) {
        viewModelScope.launch { deleteExpense(expense) }
    }
}
```

---

## 9. Interview Quick-Fire Answers

**Q: Why separate domain model from Room entity?**
> To decouple the UI from the database. If the Room schema changes (e.g., rename a column), only `ExpenseRepositoryImpl` changes — ViewModels and Composables are untouched.

**Q: When should a Use Case have business logic vs just delegate?**
> Delegate-only use cases are fine when they improve readability and testability. Add logic when there's validation, transformation, or aggregation that shouldn't live in the ViewModel.

**Q: Why does the domain layer have no Android imports?**
> It makes the domain layer testable with plain JUnit — no Robolectric or Android emulator needed. Pure Kotlin = fast tests.

**Q: What is the `operator fun invoke()` pattern?**
> It allows calling the use case like a function: `addExpense(expense)` instead of `addExpense.execute(expense)`. Clean, readable, and idiomatic Kotlin.

**Q: Where does validation live?**
> In Use Cases. Not in the ViewModel (too close to UI), not in the Repository (too close to data). The domain layer owns business rules.
