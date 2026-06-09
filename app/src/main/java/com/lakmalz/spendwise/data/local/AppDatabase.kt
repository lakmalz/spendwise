package com.lakmalz.spendwise.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

//version = 1 is your schema version. Every time you change any @Entity (add a field, rename a column, change a type),
//you MUST increment this number AND provide a Migration object.
//If version doesn't match what's on the device, Room throws IllegalStateException at launch.
//fallbackToDestructiveMigration() is fine during development — it drops and recreates tables. Remove it before production.

@Database(
    entities = [ExpenseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}