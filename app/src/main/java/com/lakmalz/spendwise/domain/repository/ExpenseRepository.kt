package com.lakmalz.spendwise.domain.repository

import com.lakmalz.spendwise.domain.model.Expense
import kotlinx.coroutines.flow.Flow

// Contract defined by the domain layer.
// The data layer (ExpenseRepositoryImpl) implements this interface.
// ViewModels and use cases depend only on this interface — never on Room directly.
interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    fun searchExpenses(query: String, category: String): Flow<List<Expense>>
    suspend fun insertExpense(expense: Expense): Long
    suspend fun deleteExpense(expense: Expense)
    suspend fun deleteAllExpenses()
}
