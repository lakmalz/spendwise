package com.lakmalz.spendwise.domain.usecase

import com.lakmalz.spendwise.domain.model.Expense
import com.lakmalz.spendwise.domain.repository.ExpenseRepository
import javax.inject.Inject

class AddExpenseUseCase @Inject constructor(
    private val repository: ExpenseRepository
) {
    suspend operator fun invoke(expense: Expense): Long {
        require(expense.amount > 0) { "Amount must be greater than zero" }
        require(expense.category.isNotBlank()) { "Category cannot be empty" }
        return repository.insertExpense(expense)
    }
}
