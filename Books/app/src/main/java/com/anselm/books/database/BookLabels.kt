package com.anselm.books.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_labels",
    indices = [
        Index(value = [ "bookId", "sortKey" ])
    ]
)
data class BookLabels(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val bookId: Long,
    val labelId: Long,
    val sortKey: Int,
) {
    constructor(bookId: Long, labelId: Long, sortKey: Int) : this(0, bookId, labelId, sortKey)
}
