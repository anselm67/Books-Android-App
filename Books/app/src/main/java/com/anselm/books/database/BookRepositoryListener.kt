package com.anselm.books.database

interface BookRepositoryListener {
    fun onBookCreated(book: Book)
    fun onBookInserted(book: Book)
    fun onBookDeleted(book: Book)
    fun onBookUpdated(book: Book)
}