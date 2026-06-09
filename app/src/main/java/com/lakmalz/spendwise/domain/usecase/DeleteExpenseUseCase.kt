package com.lakmalz.spendwise.domain.usecase

import com.lakmalz.spendwise.domain.model.Expense
import com.lakmalz.spendwise.domain.repository.ExpenseRepository
import javax.inject.Inject

class DeleteExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense) = repository.deleteExpense(expense)
}
