package com.anselm.books.database

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.MD5
import com.anselm.books.TAG
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.format.DateTimeFormatter

class BookRepository(
    private val dao: BookDao
) {
    private val listeners = emptyList<BookRepositoryListener>().toMutableList()

    fun addBookListener(listener: BookRepositoryListener) {
        listeners.add(listener)
    }

    suspend fun getTotalCount(): Int {
        return dao.getTotalCount()
    }

    suspend fun getPagedList(query: Query, limit: Int, offset: Int): List<Book> {
        check(query.type == Query.Type.Regular)
        Log.d(TAG, "getPagedList [$offset, $limit] ${query.query}/${query.partial}," +
                " filters: '${query.filters}'," +
                " withoutLabelOfType: ${query.withoutLabelOfType}" +
                " sort: ${query.sortBy}"
        )
        val books =  if ( query.query.isNullOrEmpty() ) {
            dao.getFilteredPagedList(
                query.filters.map { it.labelId },
                query.withoutLabelOfType,
                query.sortBy, limit, offset
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedList(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.filters.map { it.labelId },
                query.withoutLabelOfType,
                query.sortBy, limit, offset
            )
        }
        books.forEach { it.status = Book.Status.Loaded }
        return books
    }

    suspend fun getPagedListCount(query: Query): Int {
        check(query.type == Query.Type.Regular)
        Log.d(TAG, "getPagedListCount ${query.query}/${query.partial}," +
                " filters: '${query.filters}'," +
                " withoutLabelOfType: ${query.withoutLabelOfType}"
        )
        val count = if ( query.query.isNullOrEmpty() ) {
            dao.getFilteredPagedListCount(
                query.filters.map { it.labelId },
                query.withoutLabelOfType,
            )
        } else /* Requests text matching. */ {
            dao.getTitlePagedListCount(
                if (query.partial) query.query!! + '*' else query.query!!,
                query.filters.map { it.labelId },
                query.withoutLabelOfType,
            )
        }
        return count
    }

    suspend fun getIdsList(query: Query): List<Long> {
        when (query.type) {
            Query.Type.Regular -> return if (query.query.isNullOrEmpty()) {
                    dao.getFilteredIdsList(
                        query.filters.map { it.labelId },
                        query.withoutLabelOfType, query.sortBy,
                    )
                } else /* Requests text matching. */ {
                    dao.getTitleIdsList(
                        if (query.partial) query.query!! + '*' else query.query!!,
                        query.filters.map { it.labelId },
                        query.withoutLabelOfType, query.sortBy,
                    )
                }
            Query.Type.Duplicates -> return getDuplicateBookIds()
            Query.Type.NoCover -> return getWithoutCoverBookIds()
        }
    }

    fun deleteAll() {
        clearLabelCaches()
        app.database.clearAllTables()
        Log.d(TAG, "deleteAll: cleared all tables.")
    }

    suspend fun deleteBook(book: Book) {
        listeners.forEach { it.onBookDeleted(book) }
        dao.deleteBook(book)
        book.status = Book.Status.Deleted
    }

    suspend fun getHisto(
        type: Label.Type,
        labelQuery: String? = null,
        sortBy: Int = BookDao.SortByCount,
        query: Query = Query.emptyQuery,
    ): List<Histo> {
        check(query.filters.size <= 5)
        val histos = if ( query.query.isNullOrEmpty() ) {
            if (labelQuery.isNullOrEmpty()) {
                dao.getFilteredHisto(
                    type,
                    query.filters.map { it.labelId },
                    query.withoutLabelOfType,
                    sortBy,
                )
            } else {
                dao.searchFilteredHisto(
                    type,
                    labelQuery,
                    query.filters.map { it.labelId },
                    query.withoutLabelOfType,
                    sortBy,
                )
            }
        } else /* Requests text match. */ {
            if (labelQuery.isNullOrEmpty()) {
                dao.getTitleHisto(
                    type,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.filters.map { it.labelId },
                    query.withoutLabelOfType,
                    sortBy,
                )
            } else {
                dao.searchTitleHisto(
                    type,
                    labelQuery,
                    if (query.partial) query.query!! + '*' else query.query!!,
                    query.filters.map { it.labelId },
                    query.withoutLabelOfType,
                    sortBy,
                )
            }
        }
        histos.forEach { it.text = label(it.labelId).name }
        return histos
    }

    /**
     * Loads a book by id.
     * If there is any chance you'll save the book down the road, you have to
     * set decorate to true, as saving the book requires access to the authors.
     */
    suspend fun load(bookId: Long, decorate: Boolean = false): Book? {
        val book = dao.load(bookId)
        if (book != null && decorate) {
            decorate(book)
        }
        book?.status = Book.Status.Loaded
        return book
    }

    private fun uidAndVersion(book: Book) {
        book.uid.ifEmpty {
            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            book.uid = MD5.from("$now:${book.title}:${book.authors.joinToString { it.name }}")
        }
    }

    /**
     * Saves - inserts or updates - keeps track of dateAdded and last modified timestamps.
     */
    private suspend fun doSave(book: Book) {
        uidAndVersion(book)
        var bookId = book.id
        val timestamp = System.currentTimeMillis() / 1000
        if (book.id <= 0) {
            if (book.rawDateAdded <= 0) {
                book.rawDateAdded = timestamp
            }
            listeners.forEach { it.onBookInserted(book) }
            bookId = dao.insert(book)
            book.status = Book.Status.Saved
        } else {
            book.rawLastModified = timestamp
            listeners.forEach { it.onBookUpdated(book) }
            dao.update(book)
            book.status = Book.Status.Saved
        }
        if (book.labelsChanged) {
            dao.clearLabels(bookId)
            var sortKey = 0
            dao.insert(*book.labels!!.map {
                // Saves the label to the database if needed, by going through the cache.
                val label = label(it.type, it.name)
                BookLabels(bookId, label.id, sortKey++)
            }.toTypedArray())
        }
    }

    /**
     * Saves this book and its image.
     * The work is done within the application main scope, so it doesn't get canceled as the user
     * switches fragment during save.
     * Importing a book - for ex. - shouldn't increment its version number.
     */
    suspend fun save(book: Book, saveImage: Boolean = true) {
        if (saveImage) {
            app.imageRepository.save(book) {
                app.applicationScope.launch {
                    doSave(book)
                }
            }
        } else {
            doSave(book)
        }
    }

    suspend fun saveIfNone(book: Book, saveImage: Boolean = true) {
        if (book.uid.isEmpty() || ! dao.uidExists(book.uid)) {
            save(book, saveImage)
        }
    }

    /**
     * Returns the list of duplicates for the [book] not including itself.
     */
    suspend fun getDuplicates(book: Book): List<Book> {
        val authorId = if (book.authors.isEmpty()) 0 else book.authors[0].id
        val books = dao.getDuplicates(book.id, book.title, authorId, book.isbn)
        books.forEach { it.status = Book.Status.Loaded }
        return books
    }

    /**
     * Creates a new book for insertion.
     * This might set some default values for some fields based on preferences.
     */
    fun newBook(isbn: String? = null): Book {
        val book = Book()
        book.isbn = isbn ?: ""
        listeners.forEach { it.onBookCreated(book) }
        return book
    }

    /**
     * Handling of cached labels.
     * All labels are to be gotten through these methods which caches them as needed.
     */
    private val labelsByValue = HashMap<Pair<Label.Type,String>, Label>()
    private val labelsById = HashMap<Long, Label>()

    private fun clearLabelCaches() {
        labelsByValue.clear()
        labelsById.clear()
    }

    suspend fun label(type: Label.Type, rawName: String): Label {
        val name = rawName.trim()
        val key = Pair(type, name)
        var label = labelsByValue[key]
        if (label == null) {
            // No need for synchronization as the underlying sql table has a unique constraint.
            label = dao.label(type, name)
            if (label == null) {
                val id = dao.insert(Label(type, name))
                if (id < 0) {
                    // It must have been inserted while we weren't looking.
                    label = labelsByValue[key]
                    check(label != null) { "This should not happen. Period & Lol." }
                } else {
                    label = Label(id, type, name)
                    labelsByValue[key] = label
                    labelsById[label.id] = label
                }
            }
        }
        return label
    }

    // A (B)locking version of label, cause it's used everywhere and usually right in the cache.
    fun labelB(type: Label.Type, name: String): Label {
        var label: Label
        runBlocking {
            label = label(type, name)
        }
        return label
    }

    private suspend fun labelIfExists(type: Label.Type, name: String): Label? {
        return dao.label(type, name)
    }

    fun labelIfExistsB(type: Label.Type, name: String): Label? {
        var label: Label?
        runBlocking {
            label = labelIfExists(type, name)
        }
        return label
    }

    fun labelOrNullB(type: Label.Type, name: String):Label? {
        return if (name.isEmpty()) null else labelB(type, name)

    }

    suspend fun label(id: Long): Label {
        var label = labelsById[id]
        if (label == null) {
            label = dao.label(id)
            check(label != null)
            labelsByValue[Pair(label.type, label.name)] = label
            labelsById[label.id] = label
        }
        return label
    }

    suspend fun decorate(book: Book) {
        book.decorate(dao.labels(book.id).map { label(it) })
    }

    suspend fun rename(label: Label, newName: String) {
        dao.updateLabel(label.id, newName)
        // The ByIds cache doesn't need updating, but the ByValue one does.
        labelsByValue.remove(Pair(label.type, label.name))
        label.name = newName
        labelsByValue[Pair(label.type, label.name)] = label
    }

    /**
     * Stats queries.
     */
    suspend fun getDuplicateBookCount(): Int {
        return dao.getDuplicateBookCount()
    }

    private suspend fun getDuplicateBookIds(): List<Long> {
        return dao.getDuplicateBookIds()
    }

    suspend fun getWithoutCoverBookCount(): Int {
        return dao.getWithoutCoverBookCount()
    }

    private suspend fun getWithoutCoverBookIds(): List<Long> {
        return dao.getWithoutCoverBooksIds()
    }

    suspend fun getWithoutLabelBookCount(type: Label.Type): Int {
        return dao.getWithoutLabelBookCount(type)
    }

    suspend fun getLabelTypeCounts(): List<LabelTypeCount> {
        return dao.getLabelTypeCounts()
    }

    suspend fun getLabels(type: Label.Type): List<Label> {
        return dao.getLabels(type).map { label(it.id) }
    }

    suspend fun deleteUnusedLabels(): Int {
        return dao.deleteUnusedLabels()
    }

    suspend fun searchLabels(type: Label.Type, labelQuery: String?): List<Label> {
        return if (labelQuery == null) {
            dao.getLabels(type).map { label(it.id) }
        } else {
            dao.searchLabels(type, labelQuery).map { label(it.id) }
        }
    }

    // Deletes a label and clears its cache entries.
    // This should be synchronized with the lookup functions. But then it's unlikely anything
    // happens within the context of the app.
    suspend fun deleteLabel(label: Label) {
        labelsByValue.remove(Pair(label.type, label.name))
        labelsById.remove(label.id)
        dao.deleteLabel(label.id)
    }

    suspend fun mergeLabels(fromLabel: Label, intoLabel: Label) {
        dao.mergeLabel(fromLabel.id, intoLabel.id)
        deleteLabel(fromLabel)
    }
}