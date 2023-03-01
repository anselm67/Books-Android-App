package com.anselm.books.ui.home

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.anselm.books.database.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookAdapter (
    private val onClick: (position: Int, Book) -> Unit,
    private val onEditClick: ((position: Int, Book) -> Unit)? = null,
    private val selectionListener: SelectionListener,
) : PagingDataAdapter<Book, BookViewHolder>(BooksComparator())
{
    private val selected: MutableSet<Book> = emptySet<Book>().toMutableSet()
    var allSelected = false
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val editClick: ((Int) -> Unit)? =
            if (onEditClick != null) {
                { position ->
                    getItem(position)?.let { this@BookAdapter.onEditClick.invoke(position, it) }
                }
            } else {
                null
            }
        return BookViewHolder(
            RecyclerviewBookItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            { position ->       // onClick
                if ( selected.size > 0 ) {
                    select(position)
                } else {
                    getItem(position)?.let { onClick(position, it) }
                }
            },
            { position -> select(position) },     // onLongClick
            editClick,
        )
    }
    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = getItem(position)
        if (book != null) {
            holder.bind(book, selected.contains(book) || allSelected)
        } else {
            holder.hide()
        }
    }

    class BooksComparator: DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean {
            return oldItem == newItem
        }
    }

    private fun select(position: Int) {
        val book = getItem(position) ?: return
        if (selected.contains(book)) {
            selected.remove(book)
        } else {
            selected.add(book)
        }
        if (selected.size == 1) {
            selectionListener.onSelectionStart()
        } else if (selected.size == 0) {
            selectionListener.onSelectionStop()
        }
        selectionListener.onSelectionChanged(selected.size)
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll() {
        if ( allSelected ) {
            return
        }
        allSelected = true
        // Invoke the listeners as needed:
        if (selected.isEmpty()) {
            selectionListener.onSelectionStart()
        }
        selectionListener.onSelectionChanged(ALL)
        notifyDataSetChanged()
    }

    fun getSelectedBookIds(): List<Long> {
        check( ! allSelected )
        return selected.map { it.id }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun cancelSelection() {
        if (selected.size > 0) {
            selected.clear()
            allSelected = false
            selectionListener.onSelectionStop()
            selectionListener.onSelectionChanged(0)
            notifyDataSetChanged()
        }
    }

    companion object {
        const val ALL = -1
    }
}