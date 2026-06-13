package com.lakmalz.spendwise.domain.usecase

import com.lakmalz.spendwise.domain.model.ExpenseSummary
import com.lakmalz.spendwise.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

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
