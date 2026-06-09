package com.lakmalz.spendwise.domain.model

// Pure Kotlin model — no Room annotations.
// The UI and domain layers never depend on Room/database internals.
data class Expense(
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long = System.currentTimeMillis()
)
