package com.anselm.books.ui.home

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.GlideApp
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.databinding.RecyclerviewBookItemBinding

class BookViewHolder(
    private val binding: RecyclerviewBookItemBinding,
    private val onClick: (position: Int) -> Unit,
    private val onLongClick: (position: Int) -> Unit,
    private val onEditClick: ((position: Int) -> Unit)? = null,
): RecyclerView.ViewHolder(binding.root) {
    fun bind(book: Book, selected: Boolean) {
        show()
        val app = BooksApplication.app
        val uri = app.imageRepository.getCoverUri(book)
        binding.titleView.text = book.title
        binding.authorView.text = book.authors.joinToString { it.name }
        if (book.subtitle.isEmpty()) {
            binding.subtitleView.isVisible = false
        } else {
            binding.subtitleView.isVisible = true
            binding.subtitleView.text = book.subtitle
        }
        if (book.dateAdded != "") {
            binding.dateAddedView.text = app.getString(R.string.date_added_embedded, book.dateAdded)
        }
        binding.idCheckMark.visibility = if (selected) View.VISIBLE else View.GONE
        if (onEditClick != null) {
            binding.idEditBook.setOnClickListener {
                onEditClick.invoke(this.bindingAdapterPosition)
            }
        } else {
            binding.idEditBook.isVisible = false
        }
        GlideApp.with(app.applicationContext)
            .load(uri)
            .into(binding.coverImageView)
        binding.root.setOnClickListener {
            onClick(this.bindingAdapterPosition)
        }
        binding.root.setOnLongClickListener {
            onLongClick(this.bindingAdapterPosition)
            true
        }
    }

    fun hide() {
        binding.titleView.isVisible = false
        binding.authorView.isVisible = false
        binding.coverImageView.isVisible = false
        binding.idRuler.isVisible = false
    }

    private fun show() {
        binding.titleView.isVisible = true
        binding.authorView.isVisible = true
        binding.coverImageView.isVisible = true
        binding.idRuler.isVisible = true
    }
}