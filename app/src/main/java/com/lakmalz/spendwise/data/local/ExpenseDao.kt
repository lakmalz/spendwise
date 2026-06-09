package com.lakmalz.spendwise.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // Flow = reactive. Room re-emits whenever the expenses table changes.
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("""
        SELECT * FROM expenses
        WHERE (:category = 'All' OR category = :category)
        AND (note LIKE '%' || :query || '%' OR :category = '')
        ORDER BY date DESC
    """)
    fun searchExpenses(query: String, category: String) : Flow<List<ExpenseEntity>>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}