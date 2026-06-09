# Dependency Injection (DI) with Hilt — Android

> Interview reference for the SpendWise project.

---

## 1. What is Dependency Injection?

A class **should not create its own dependencies** — they should be **provided from outside**.

### Without DI (tightly coupled — bad)
```kotlin
class HomeViewModel : ViewModel() {
    // ViewModel builds everything itself
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "spendwise.db").build()
    private val dao = db.expenseDao()
    private val repo = ExpenseRepositoryImpl(dao)
    // Problems:
    // - Hard to test (can't swap fake repo)
    // - Manual lifecycle management
    // - Duplicate DB instances possible
}
```

### With DI (loosely coupled — good)
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ExpenseRepository   // Hilt creates and injects this
) : ViewModel()
// Benefits:
// - Easy to test (inject a fake repo)
// - Hilt manages lifecycle and singletons
// - One DB instance shared across the app
```

---

## 2. How Hilt Works — Step by Step

```
Step 1: @HiltAndroidApp         → Hilt starts up with the Application
          ↓
Step 2: @Module (DatabaseModule) → You teach Hilt HOW to create dependencies
          ↓
Step 3: @Inject / @HiltViewModel → Hilt injects dependencies automatically
```

### In SpendWise:

```
SpendWiseApplication (@HiltAndroidApp)
        ↓
DatabaseModule (@Module)
  @Provides AppDatabase    ← "build Room DB once, share everywhere"
  @Provides ExpenseDao     ← "get DAO from the DB"
        ↓
ExpenseRepositoryImpl (@Inject constructor(dao: ExpenseDao))
        ↓
HomeViewModel (@HiltViewModel, @Inject constructor(repo: ExpenseRepository))
        ↓
HomeScreen (Composable) ← hiltViewModel() picks it up automatically
```

---

## 3. The 3 Core Hilt Annotations

| Annotation | Where to use | What it does |
|---|---|---|
| `@HiltAndroidApp` | `Application` class | Bootstraps Hilt for the entire app — **required** |
| `@AndroidEntryPoint` | `Activity`, `Fragment`, `Service` | Enables injection into Android framework classes |
| `@HiltViewModel` | `ViewModel` | Allows constructor injection into ViewModels |

### Example — SpendWise usage:
```kotlin
// Application
@HiltAndroidApp
class SpendWiseApplication : Application()

// Activity
@AndroidEntryPoint
class MainActivity : ComponentActivity()

// ViewModel
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ExpenseRepository
) : ViewModel()
```

> **Important:** `AndroidManifest.xml` must declare the Application class:
> ```xml
> <application android:name=".SpendWiseApplication" ...>
> ```

---

## 4. Modules — Teaching Hilt How to Build Things

A `@Module` is needed when Hilt can't build something automatically
(e.g. Room database, Retrofit, interfaces).

```kotlin
@Module
@InstallIn(SingletonComponent::class)   // lives as long as the app
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "spendwise.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()
}
```

---

## 5. Where DI is Used in SpendWise

### Repository Layer
```kotlin
class ExpenseRepositoryImpl @Inject constructor(
    private val dao: ExpenseDao           // injected by Hilt
) : ExpenseRepository {
    override fun getAllExpenses() = dao.getAllExpenses()
    override suspend fun insert(expense: ExpenseEntity) = dao.insert(expense)
    override suspend fun delete(expense: ExpenseEntity) = dao.delete(expense)
}
```

### ViewModel Layer
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ExpenseRepository   // injected by Hilt
) : ViewModel() {
    val expenses = repo.getAllExpenses().stateIn(viewModelScope, ...)
}
```

### Networking (Retrofit — future)
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor())
            .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)
}
```

### DataStore / Preferences (future)
```kotlin
@Provides @Singleton
fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("user_settings")
    }
```

---

## 6. Scopes — How Long Does a Dependency Live?

| Scope | Annotation | Component | Lifetime |
|---|---|---|---|
| App-wide | `@Singleton` | `SingletonComponent` | Whole app lifetime |
| Per Activity | `@ActivityScoped` | `ActivityComponent` | Activity is alive |
| Per Fragment | `@FragmentScoped` | `FragmentComponent` | Fragment is alive |
| Per ViewModel | `@ViewModelScoped` | `ViewModelComponent` | ViewModel is alive |

### SpendWise scope decisions:
```kotlin
// Always @Singleton — never want two DB instances
@Singleton AppDatabase
@Singleton ExpenseDao
@Singleton ExpenseRepository

// @ViewModelScoped — tied to one screen's ViewModel
@ViewModelScoped SomeUseCase
```

---

## 7. Full Dependency Graph — SpendWise

```
SingletonComponent (app lifetime)
├── AppDatabase          @Singleton
├── ExpenseDao           @Singleton  (provided by DatabaseModule)
├── ExpenseRepository    @Singleton  (bound via RepositoryModule)
├── Retrofit             @Singleton  (future — NetworkModule)
└── DataStore            @Singleton  (future — PreferencesModule)

ViewModelComponent (per-screen lifetime)
├── HomeViewModel        @HiltViewModel
├── AddExpenseViewModel  @HiltViewModel  (future)
└── SettingsViewModel    @HiltViewModel  (future)

ActivityComponent
└── MainActivity         @AndroidEntryPoint
```

---

## 8. Interview Quick-Fire Answers

**Q: What is Dependency Injection?**
> Providing dependencies to a class from outside rather than letting the class create them. Improves testability, modularity, and lifecycle management.

**Q: Why Hilt over manual DI or Dagger?**
> Hilt is built on Dagger but removes the boilerplate. It integrates directly with Android lifecycle components (Activity, ViewModel, etc.) and is the Google-recommended DI solution for Android.

**Q: What is `@Singleton` and why is it important for Room?**
> `@Singleton` ensures only one instance is created for the app's lifetime. Room DB must be a singleton — multiple instances cause data inconsistency and waste memory.

**Q: What's the difference between `@Inject constructor` and `@Provides`?**
> Use `@Inject constructor` when you own the class (your own code). Use `@Provides` in a `@Module` when you don't own the class (third-party like Room, Retrofit) or when providing an interface implementation.

**Q: What is `@InstallIn`?**
> Defines which Hilt component the module belongs to, and therefore its scope/lifetime. `@InstallIn(SingletonComponent::class)` means the module's bindings live for the app's entire lifetime.
