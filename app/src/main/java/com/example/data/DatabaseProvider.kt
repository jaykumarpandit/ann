package com.example.data

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var database: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ann_database"
            )
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { database = it }
        }
    }
}
