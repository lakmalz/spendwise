package com.lakmalz.spendwise.domain.common

// Shared UI state wrapper used by all ViewModels.
// Every screen maps its domain data into one of these three states.
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val exception: AppException) : UiState<Nothing>()
}

// Domain-level exceptions — no raw IllegalArgumentException leaking to the UI.
sealed class AppException(message: String) : Exception(message) {
    class InvalidAmountException : AppException("Amount must be greater than zero")
    class EmptyCategoryException : AppException("Category cannot be empty")
    class DatabaseException(cause: Throwable) : AppException(cause.message ?: "Database error")
    class UnknownException(cause: Throwable) : AppException(cause.message ?: "Unknown error")
}
