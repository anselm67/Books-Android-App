package com.anselm.books

import androidx.lifecycle.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.anselm.books.database.BookRepository
import com.anselm.books.database.Query
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

private const val PAGE_SIZE = 50
private const val MAX_SIZE = 250

class BookViewModel(
    private val repository: BookRepository
) : ViewModel() {
    var query = Query()
    val queryFlow = MutableStateFlow(query)
    @OptIn(ExperimentalCoroutinesApi::class)
    val bookList = queryFlow.flatMapLatest { query ->
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = true,
                maxSize = MAX_SIZE,
                jumpThreshold = 2 * PAGE_SIZE)
        ) {
            BookPagingSource(query, repository)
        }.flow.cachedIn(viewModelScope)
    }

    companion object {
        val Factory = object: ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BookViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BookViewModel(BooksApplication.app.repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
