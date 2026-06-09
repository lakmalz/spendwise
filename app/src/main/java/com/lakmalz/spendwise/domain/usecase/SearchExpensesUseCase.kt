package com.lakmalz.spendwise.domain.usecase

import com.lakmalz.spendwise.domain.model.Expense
import com.lakmalz.spendwise.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchExpensesUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    operator fun invoke(query: String, category: String): Flow<List<Expense>> =
        repository.searchExpenses(query, category)
}
