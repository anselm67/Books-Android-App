package com.anselm.books.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anselm.books.Constants

@Database(
    entities = [
        Book::class, BookFTS::class,
        Label::class, LabelFTS::class,
        BookLabels::class
    ],
    version = 23,
    exportSchema = false)
abstract class BookDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        fun getDatabase(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    Constants.DATABASE_NAME,
                ).fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}