package com.anselm.books.database

import androidx.room.*
import androidx.room.Query

@Dao
interface BookDao {
    /**
     * Handling Book: insert, load and update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Query("SELECT * FROM book_table WHERE id = :bookId")
    suspend fun load(bookId: Long) : Book?

    @Query(" SELECT bt.* FROM book_table AS bt " +
            " WHERE bt.id IN (SELECT DISTINCT(id) FROM " +
            "    (SELECT bt.id AS id FROM book_table AS bt " +
            "     LEFT JOIN book_labels AS lb ON lb.bookId = bt.id " +
            "     WHERE bt.title = :title AND bt.id != :bookId " +
            "       AND ((:authorId = 0) OR (lb.labelId = :authorId)) " +
            "    UNION SELECT bt1.id AS id FROM book_table AS bt1 " +
            "            WHERE bt1.id != :bookId " +
            "              AND (:isbn != '' AND bt1.isbn = :isbn))) ")
    suspend fun getDuplicates(bookId: Long, title: String, authorId: Long, isbn: String): List<Book>

    @Query("SELECT EXISTS (SELECT * FROM book_table WHERE uid = :uid)")
    suspend fun uidExists(uid: String): Boolean

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)

    @Transaction
    suspend fun deleteBook(book: Book) {
        clearLabels(book.id)
        delete(book)
    }

    /**
     * Handling of labels.
     * This section covers lookup by id and value, insertion and fetching.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: Label): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg bookLabels: BookLabels)

    @Query("SELECT * FROM label_table WHERE type = :type AND name = :name")
    @TypeConverters(Converters::class)
    suspend fun label(type: Label.Type, name: String): Label?

    @Query("SELECT * FROM label_table WHERE id = :id")
    suspend fun label(id: Long): Label?

    @Query("UPDATE label_table SET name = :name WHERE id = :id")
    suspend fun updateLabel(id: Long, name: String)

    /*
     * The JOIN in the query below ensures we will only get back labels that exist.
     * That should always be the case but on occasion spurious stuff will happen.
     */
    @Query("SELECT labelId FROM book_labels AS bl" +
            " JOIN label_table AS lt ON lt.id = bl.labelId " +
            "WHERE bookId = :bookId ORDER BY sortKey ASC")
    suspend fun labels(bookId: Long): List<Long>

    @Query("DELETE FROM book_labels WHERE bookId = :bookId")
    suspend fun clearLabels(bookId: Long)

    /**
     * Gets the total number of books in the library.
     */
    @Query("SELECT COUNT(*) FROM book_table")
    suspend fun getTotalCount(): Int

    /**
     * Two queries for top level search.
     */
    @Query("SELECT * FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            " AND (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "   AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId1 = 0 OR labelId = :labelId1" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId2 = 0 OR labelId = :labelId2" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId3 = 0 OR labelId = :labelId3" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId4 = 0 OR labelId = :labelId4" +
            "    INTERSECT " +
            "      SELECT bookId FROM book_labels " +
            "          WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "   )) " +
            " ORDER BY " +
            "   CASE WHEN :sortOrder = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :sortOrder = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    @TypeConverters(Converters::class)
    suspend fun getTitlePagedList(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int, limit: Int, offset: Int
    ): List<Book>

    suspend fun getTitlePagedList(
        query: String, labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int, limit: Int, offset: Int
    ): List<Book> {
        when (labelIds.size) {
            0 -> return getTitlePagedList(
                query, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            1 -> return getTitlePagedList(
                query, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            2 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            3 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            4 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            5 -> return getTitlePagedList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder, limit, offset
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT COUNT(*) FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            " AND (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "   AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))")
    @TypeConverters(Converters::class)
    suspend fun getTitlePagedListCount(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
    ): Int

    suspend fun getTitlePagedListCount(
        query: String, labelIds: List<Long>, withoutLabelOfType: Label.Type,
    ): Int {
        when (labelIds.size) {
            0 -> return getTitlePagedListCount(
                query, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType,
            )
            1 -> return getTitlePagedListCount(
                query, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType,
            )
            2 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType,
            )
            3 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType,
            )
            4 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType,
            )
            5 -> return getTitlePagedListCount(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return 0
    }


    @Query("SELECT * FROM book_table " +
            " WHERE (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) " +
            " AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))" +
            " ORDER BY " +
            "   CASE WHEN :sortOrder = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :sortOrder = 2 THEN date_added END DESC " +
            "LIMIT :limit OFFSET :offset")
    @TypeConverters(Converters::class)
    suspend fun getFilteredPagedList(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int, limit: Int, offset: Int
    ): List<Book>

    suspend fun getFilteredPagedList(
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int, limit: Int, offset: Int
    ): List<Book> {
        when (labelIds.size) {
            0 -> return getFilteredPagedList(
                0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            1 -> return getFilteredPagedList(
                labelIds[0], 0L, 0L,0L,  0L, withoutLabelOfType, sortOrder, limit, offset
            )
            2 -> return getFilteredPagedList(
                labelIds[0], labelIds[1],0L,  0L, 0L, withoutLabelOfType, sortOrder, limit, offset
            )
            3 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], labelIds[2],0L,  0L, withoutLabelOfType, sortOrder, limit, offset
            )
            4 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3],0L, withoutLabelOfType, sortOrder, limit, offset
            )
            5 -> return getFilteredPagedList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder, limit, offset
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT COUNT(*) FROM book_table " +
            " WHERE (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) " +
            " AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (" +
            "SELECT bookId FROM book_labels " +
            "    WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))")
    @TypeConverters(Converters::class)
    suspend fun getFilteredPagedListCount(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long,labelId5: Long,
        withoutLabelOfType: Label.Type,
    ): Int

    suspend fun getFilteredPagedListCount(
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        ): Int {
        when (labelIds.size) {
            0 -> return getFilteredPagedListCount(
                0L, 0L, 0L, 0L, 0L, withoutLabelOfType,
            )
            1 -> return getFilteredPagedListCount(
                labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType,
            )
            2 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType,
            )
            3 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType,
            )
            4 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType,
            )
            5 -> return getFilteredPagedListCount(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType,
            )
            else -> assert(value = false)
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return 0
    }

    /**
     * Queries for retrieving IDs of books in view.
     */
    @Query("SELECT book_table.id FROM book_table " +
            " JOIN book_fts ON book_table.id = book_fts.rowid " +
            " WHERE book_fts MATCH :query " +
            " AND (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            " AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "        OR id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))" +
            " ORDER BY " +
            "   CASE WHEN :sortOrder = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :sortOrder = 2 THEN date_added END DESC ")
    @TypeConverters(Converters::class)
    suspend fun getTitleIdsList(
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Long>

    suspend fun getTitleIdsList(
        query: String, labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Long> {
        when (labelIds.size) {
            0 -> return getTitleIdsList(
                query, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            1 -> return getTitleIdsList(
                query, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            2 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            3 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder,
            )
            4 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder,
            )
            5 -> return getTitleIdsList(
                query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_table.id FROM book_table " +
            " WHERE (:withoutLabelOfType = 0 OR id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) " +
            " AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) " +
            "    OR id IN (SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            ")) " +
            " ORDER BY " +
            "   CASE WHEN :sortOrder = 1 THEN book_table.title END ASC, " +
            "   CASE WHEN :sortOrder = 2 THEN date_added END DESC ")
    @TypeConverters(Converters::class)
    suspend fun getFilteredIdsList(
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Long>

    suspend fun getFilteredIdsList(
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Long> {
        when (labelIds.size) {
            0 -> return getFilteredIdsList(
                0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            1 -> return getFilteredIdsList(
                labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            2 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            3 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder,
            )
            4 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder,
            )
            5 -> return getFilteredIdsList(
                labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    /**
     * Histogram queries: get{Title,Filtered} Histo and search{Title,Filtered}Histo.
     */
    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE " +
            "    book_fts MATCH :query" +
            "    AND label_table.type = :type " +
            "    AND (:withoutLabelOfType = 0 OR book_table.id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))" +
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :sortOrder = 3 THEN count END DESC, " +
            "          CASE WHEN :sortOrder = 1 THEN label_table.name END ASC")
    @TypeConverters(Converters::class)
    suspend fun getTitleHisto(
        type: Label.Type,
        query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Histo>

    suspend fun getTitleHisto(
        type: Label.Type, query: String, labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortParam: Int = SortByCount,
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return getTitleHisto(
                type, query, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortParam,
            )
            1 -> return getTitleHisto(
                type, query, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortParam,
            )
            2 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortParam,
            )
            3 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortParam,
            )
            4 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortParam,
            )
            5 -> return getTitleHisto(
                type, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortParam,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_fts ON book_table.id = book_fts.rowid," +
            "       book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId, " +
            "       label_fts ON label_table.id = label_fts.rowid " +
            " WHERE " +
            "    book_fts MATCH :query " +
            "    AND label_fts MATCH :labelQuery" +
            "    AND label_table.type = :type" +
            " AND (:withoutLabelOfType = 0 OR book_table.id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))" +
            " GROUP BY labelId "+
            " ORDER BY CASE WHEN :sortOrder = 3 THEN count END DESC, " +
            "          CASE WHEN :sortOrder = 1 THEN label_table.name END ASC")
    @TypeConverters(Converters::class)
    suspend fun searchTitleHisto(
        type: Label.Type,
        labelQuery: String, query: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Histo>

    suspend fun searchTitleHisto(
        type: Label.Type,
        labelQuery: String, query: String,
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int = SortByCount
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return searchTitleHisto(
                type, labelQuery, query, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            1 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            2 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            3 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder,
            )
            4 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder,
            )
            5 -> return searchTitleHisto(
                type, labelQuery, query, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId " +
            " WHERE label_table.type = :type" +
            "    AND label_table.type = :type" +
            " AND (:withoutLabelOfType = 0 OR book_table.id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "    AND ((:labelId1 = 0 AND :labelId2 = 0 AND :labelId3 = 0 AND :labelId4 = 0) "+
            "         OR book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            "))" +
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :sortOrder = 3 THEN count END DESC, " +
            "          CASE WHEN :sortOrder = 1 THEN label_table.name END ASC")
    @TypeConverters(Converters::class)
    suspend fun getFilteredHisto(
        type: Label.Type,
        labelId1: Long, labelId2: Long,labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Histo>

    suspend fun getFilteredHisto(
        type: Label.Type,
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int = SortByCount
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return getFilteredHisto(
                type, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            1 -> return getFilteredHisto(
                type, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            2 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            3 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder,
            )
            4 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder,
            )
            5 -> return getFilteredHisto(
                type, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    @Query("SELECT book_labels.labelId, COUNT(*) as count FROM book_table " +
            "  JOIN book_labels ON book_labels.bookId = book_table.id," +
            "       label_table ON label_table.id = book_labels.labelId, " +
            "       label_fts ON label_table.id = label_fts.rowid " +
            " WHERE label_fts MATCH :labelQuery" +
            "    AND label_table.type = :type" +
            " AND (:withoutLabelOfType = 0 OR book_table.id NOT IN (" +
            "    SELECT bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "     WHERE lt.type = :withoutLabelOfType)) "+
            "    AND book_table.id IN (" +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId1 = 0 OR labelId = :labelId1" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId2 = 0 OR labelId = :labelId2" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId3 = 0 OR labelId = :labelId3" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId4 = 0 OR labelId = :labelId4" +
            " INTERSECT " +
            "   SELECT bookId FROM book_labels " +
            "       WHERE :labelId5 = 0 OR labelId = :labelId5" +
            ") " +
            " GROUP BY labelId " +
            " ORDER BY CASE WHEN :sortOrder = 3 THEN count END DESC, " +
            "          CASE WHEN :sortOrder = 1 THEN label_table.name END ASC")
    @TypeConverters(Converters::class)
    suspend fun searchFilteredHisto(
        type: Label.Type,
        labelQuery: String,
        labelId1: Long, labelId2: Long, labelId3: Long, labelId4: Long, labelId5: Long,
        withoutLabelOfType: Label.Type,
        sortOrder: Int,
    ): List<Histo>

    suspend fun searchFilteredHisto(
        type: Label.Type,
        labelQuery: String,
        labelIds: List<Long>,
        withoutLabelOfType: Label.Type,
        sortOrder: Int = SortByCount,
    ): List<Histo> {
        when (labelIds.size) {
            0 -> return searchFilteredHisto(
                type, labelQuery, 0L, 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            1 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], 0L, 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            2 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], 0L, 0L, 0L, withoutLabelOfType, sortOrder,
            )
            3 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], labelIds[2], 0L, 0L, withoutLabelOfType, sortOrder,
            )
            4 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], labelIds[2], labelIds[3], 0L, withoutLabelOfType, sortOrder,
            )
            5 -> return searchFilteredHisto(
                type, labelQuery, labelIds[0], labelIds[1], labelIds[2], labelIds[3], labelIds[4], withoutLabelOfType, sortOrder,
            )
            else -> assert(value = false) { "Too many filters in SQL Query."}
        }
        // NOT REACHED, not sure why the compiler doesn't see this.
        return emptyList()
    }

    /**
     * Stats queries.
     */
    @Query("SELECT type, COUNT(*) as count FROM label_table GROUP BY type")
    @TypeConverters(Converters::class)
    suspend fun getLabelTypeCounts(): List<LabelTypeCount>

    @Query("SELECT * FROM label_table WHERE type = :type ORDER BY name ASC")
    @TypeConverters(Converters::class)
    suspend fun getLabels(type: Label.Type): List<Label>

    @Query("SELECT DISTINCT(id) FROM " +
            "    (SELECT bt1.id AS id, bt1.title AS title " +
            "       FROM book_table AS bt1 " +
            "  LEFT JOIN book_labels AS bl1 ON bl1.bookId = bt1.id " +
            "      WHERE (title, labelId) IN " +
            "          (SELECT b.title AS title, lt.id AS labelId FROM book_table AS b " +
            "        LEFT JOIN book_labels AS bl ON bl.bookId = b.id " +
            "             JOIN label_table as lt on lt.id = bl.labelId " +
            "            WHERE lt.type = 1 " +
            "     GROUP BY title, subtitle, name HAVING count(*) > 1) " +
            "UNION " +
            "    SELECT id, title FROM book_table " +
            "     WHERE isbn != '' " +
            "    GROUP BY isbn HAVING count(*) > 1 " +
            "ORDER BY title ASC)")
    suspend fun getDuplicateBookIds(): List<Long>


    @Query("SELECT COUNT(*) FROM (SELECT DISTINCT(id) FROM " +
            "    (SELECT bt1.id AS id " +
            "       FROM book_table AS bt1 " +
            "  LEFT JOIN book_labels AS bl1 ON bl1.bookId = bt1.id " +
            "      WHERE (title, labelId) IN " +
            "          (SELECT b.title AS title, lt.id AS labelId FROM book_table AS b " +
            "        LEFT JOIN book_labels AS bl ON bl.bookId = b.id " +
            "             JOIN label_table as lt on lt.id = bl.labelId " +
            "            WHERE lt.type = 1 " +
            "     GROUP BY title, subtitle, name HAVING count(*) > 1) " +
            "UNION " +
            "    SELECT id FROM book_table " +
            "     WHERE isbn != '' " +
            "    GROUP BY isbn HAVING count(*) > 1 ))")
    suspend fun getDuplicateBookCount(): Int

    @Query("SELECT COUNT(*) FROM book_table " +
            " WHERE id NOT IN (" +
            "    SELECT bl.bookId FROM book_labels AS bl " +
            "      JOIN label_table AS lt ON lt.id = bl.labelId " +
            "      WHERE lt.type = :type" +
            ")")
    @TypeConverters(Converters::class)
    suspend fun getWithoutLabelBookCount(type: Label.Type): Int

    @Query(" SELECT COUNT(*) FROM book_table " +
            " WHERE image_filename = '' OR image_filename IS NULL")
    suspend fun getWithoutCoverBookCount(): Int

    @Query(" SELECT id FROM book_table " +
            " WHERE image_filename = '' OR image_filename IS NULL")
    suspend fun getWithoutCoverBooksIds(): List<Long>

    @Query("DELETE FROM label_table " +
           " WHERE id NOT IN (SELECT labelId FROM book_labels)")
    suspend fun deleteUnusedLabels(): Int

    @Query("DELETE FROM label_table WHERE id = :labelId")
    suspend fun deleteLabelById(labelId: Long): Int

    @Query("DELETE FROM book_labels WHERE labelId = :labelId")
    suspend fun deleteBookLabelsById(labelId: Long): Int

    @Transaction
    suspend fun deleteLabel(labelId: Long): Int {
        return deleteBookLabelsById(labelId) + deleteLabelById(labelId)
    }

    @Query(" SELECT * FROM label_table " +
            "  JOIN label_fts ON label_table.id = label_fts.rowid" +
            " WHERE type = :type" +
            "   AND label_fts MATCH :labelQuery")
    @TypeConverters(Converters::class)
    suspend fun searchLabels(type: Label.Type, labelQuery: String?): List<Label>

    @Query("UPDATE book_labels SET labelId = :toLabelId WHERE labelId = :fromLabelId")
    suspend fun mergeLabel(fromLabelId: Long, toLabelId: Long): Int

    companion object {
        const val SortByTitle = 1
        const val SortByDateAdded = 2
        const val SortByCount = 3
    }
}

data class LabelTypeCount(
    val type: Label.Type,
    val count: Int,
)

data class Histo(
    val labelId: Long,
    val count: Int)
{
    @Ignore
    var text: String? = null
}

