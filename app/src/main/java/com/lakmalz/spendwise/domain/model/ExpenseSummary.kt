package com.lakmalz.spendwise.domain.model

data class ExpenseSummary(
    val totalAmount: Double,
    val totalCount: Int,
    val byCategory: Map<String, Double>  // category → total amount
)
