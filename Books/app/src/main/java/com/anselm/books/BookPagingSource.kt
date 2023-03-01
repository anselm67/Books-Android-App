package com.anselm.books

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.anselm.books.database.BookRepository
import com.anselm.books.database.Book
import com.anselm.books.database.Query
import java.lang.Integer.max

private const val START_PAGE = 0

class BookPagingSource(
    private val query: Query,
    private val repository: BookRepository
) : PagingSource<Int, Book>() {

    override val jumpingSupported: Boolean
        get() = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val page = params.key ?: START_PAGE
        val itemCount = -1

        return try {
            val books = repository.getPagedList(query, params.loadSize, page * params.loadSize)
            books.forEach { repository.decorate(it) }
            Log.d(TAG, "-> Got ${books.size}/$itemCount results," +
                    " page: $page" +
                    " before: ${page * params.loadSize}" +
                    " after: ${max(0, itemCount - (page + 1) * params.loadSize)}")
            LoadResult.Page(
                data = books,
                prevKey = when (page) {
                    START_PAGE -> null
                    else -> page - 1 },
                nextKey = if (books.isEmpty()) null else page + 1,
            /*
             * Proper handling of itemCount is required to set the scrollbar height properly. For now
             * I just don't know how to get it here properly even though it's available in
             * {List, Home, Search}Fragment
             * itemsBefore = page * params.loadSize,
             * itemsAfter = max(0, itemCount - (page + 1) * params.loadSize
              */
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Book>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

}