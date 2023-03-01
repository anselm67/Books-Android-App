package com.anselm.books.ui.edit

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.R
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.databinding.FragmentEditBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class EditFragment: BookFragment() {
    private var _binding: FragmentEditBinding? = null
    private lateinit var book: Book
    val binding get() = _binding!!

    private var editors = emptyList<Editor<*, *>>().toMutableList()
    private lateinit var titleEditor: TextEditor<Book>
    private lateinit var authorsEditor: MultiLabelEditor<Book>
    private lateinit var isbnEditor: TextEditor<Book>

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if ( this@EditFragment.isChanged() || book.id <= 0) {
                        AlertDialog.Builder(requireActivity())
                            .setMessage(getString(R.string.discard_changes_prompt))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                isEnabled = false
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            }
                            .setNegativeButton(R.string.no) { _, _ -> }
                            .show()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            })
    }

    /**
     * The order in which stuff is done matters a lot here: launchers can't be created after
     * any views is initialized, so we init them first thing, and conclude with the binding of the
     * book's rendering, which creates all the views.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentEditBinding.inflate(inflater, container, false)

        // Parses the arguments, we can have either:
        // - A bookId, which means we want to edit/update an existing book,
        // - A Book instance which is to be created/ inserted.
        val safeArgs: EditFragmentArgs by navArgs()
        if (safeArgs.bookId > 0) {
            runBlocking {
                book = app.repository.load(safeArgs.bookId, decorate = true)!!
            }
        } else if (safeArgs.book != null) {
            book = safeArgs.book!!
        } else {
            app.toast(getString(R.string.edit_no_book_error))
            findNavController().popBackStack()
        }

        // Handles our menu item.
        handleMenu(
            MenuItemHandler(R.id.idDeleteBook, {
                AlertDialog.Builder(requireActivity())
                    .setMessage(getString(R.string.delete_book_confirmation, book.title))
                    .setPositiveButton(R.string.yes) { _, _ -> deleteBook() }
                    .setNegativeButton(R.string.no) { _, _ -> }
                    .show()
            }),
        )

        // Binds our fab button for save and magic.
        binding.fabSaveButton.setOnClickListener {
            checkChanges()
        }
        binding.fabMagicButton.setOnClickListener {
            performMagic()
        }
        editors = mutableListOf(CoverImageEditor(this@EditFragment, inflater, book, Book::image))
        bind(inflater, book)
        updateMagicButton()
        return binding.root
    }

    private fun updateMagicButton() {
        binding.fabMagicButton.isEnabled =
            (titleEditor.value.isNotEmpty() && authorsEditor.value.isNotEmpty())
                    ||  isbnEditor.value.isNotEmpty()
    }

    private fun deleteBook() {
        val app = BooksApplication.app
        if (book.id >= 0) {
            app.applicationScope.launch {
                app.repository.deleteBook(book)
                app.toast(getString(R.string.book_deleted, book.title))
            }
        } else {
            book.status = Book.Status.Deleted
        }
        findNavController().popBackStack()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    /**
     * Creates and binds all field editors; Adds them all to the [editors] list.
     * Keep in mind that at this point, editors already contains the CoverImageEditor which
     * has to be initialized before any view is created. Cause it wants to
     * registerForActivityResult.
     */
    private fun bind(inflater: LayoutInflater, book: Book) {
        titleEditor = TextEditor(this, inflater, book, Book::title,
            R.string.titleLabel,
            onChange = { updateMagicButton() },
            validator = { it.isNotEmpty() })
        authorsEditor = MultiLabelEditor(this, inflater, book, Book::authors,
            R.string.authorLabel, Label.Type.Authors) { updateMagicButton() }
        isbnEditor = TextEditor(this, inflater, book, Book::isbn, R.string.isbnLabel,
            onChange = { updateMagicButton() },
            validator = { it.isEmpty() || ISBN.isValidEAN13(it) })
        editors.addAll(arrayListOf(
            titleEditor,
            TextEditor(this, inflater, book, Book::subtitle, R.string.subtitleLabel),
            authorsEditor,
            SingleLabelEditor(this, inflater, book, Book::publisher,
                R.string.publisherLabel,
                Label.Type.Publisher),
            MultiLabelEditor(this, inflater, book, Book::genres,
                R.string.genreLabel, Label.Type.Genres),
            SingleLabelEditor(this, inflater, book, Book::location,
                R.string.physicalLocationLabel,
                Label.Type.Location),
            isbnEditor,
            SingleLabelEditor(this, inflater, book, Book::language,
                R.string.languageLabel, Label.Type.Language,),
            TextEditor(this, inflater, book, Book::numberOfPages, R.string.numberOfPagesLabel,
                validator = { isValidNumber(it) }),
            TextEditor(this, inflater, book, Book::summary, R.string.summaryLabel),
            YearEditor(this, inflater, book, Book::yearPublished),
        ))
        editors.forEach {
            it.setup(binding.editView)?.let { view -> binding.editView.addView(view) }
        }
    }

    private fun isChanged(): Boolean {
        return editors.firstOrNull { it.isChanged() } != null
    }

    private fun saveChanges(): Boolean {
        var changed = false
        editors.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun validateChanges():Boolean {
        return editors.firstOrNull { ! it.isValid() } == null
    }

    /**
     * Check for changes, validates them and saves the book being edited if all ok.
     * This is a three steps trip:
     * 1. checkChanges checks that there are changes, and that the changes are valid,
     * 2. checkForDuplicates check the edited book for duplicates and prompts the user as needed.
     * 3. If all above steps agree, doSave() actually writes the book and its image to the database.
     * */
    private fun checkChanges() {
        val app = BooksApplication.app
        // Validates the edits first, reject invalid books.
        if (!validateChanges()) {
            app.toast("Adjust highlighted fields.")
            return
        }
        // Inserts or saves only when valid.
        if (saveChanges() || book.id <= 0) {
            checkForDuplicates()
        } else {
            // Nothing to save, head back.
            findNavController().popBackStack()
        }
    }

    // Checks for duplicates and save the book if the user is ok.
    private fun checkForDuplicates() {
        viewLifecycleOwner.lifecycleScope.launch {
            val dupes = app.repository.getDuplicates(book)
            if (dupes.isNotEmpty()) {
                val builder = AlertDialog.Builder(requireActivity())
                builder.setMessage(
                    getString(
                        R.string.duplicate_book_confirmation,
                        book.title,
                        dupes.size
                    )
                )
                .setPositiveButton(R.string.yes) { _, _ -> doSave() }
                .setNegativeButton(R.string.no) { _, _ -> }
                .show()
            } else {
                doSave()
            }
        }
    }

    // Saves the book no matter what, this is the last hop in the path to save the book being
    // edited: after saveChanges has checked that there are changes, and after checkForDuplicates
    // approved.
    private fun doSave() {
        val reporter = app.openReporter(getString(R.string.saving_changes))
        activity?.lifecycleScope?.launch {
            app.repository.save(book)
        }?.invokeOnCompletion {
            app.toast("${book.title} saved.")
            reporter.close()
            // When the save is very long, we might already have gone.
            if (isAdded ) {
                findNavController().popBackStack()
            }
        }
    }

    private fun isValidNumber(number: String): Boolean {
        return number.firstOrNull {
            ! it.isDigit()
        } == null
    }

    private fun mergeFrom(match: Book) {
        editors.forEach {
            @Suppress("UNCHECKED_CAST")
            (it as Editor<Book, Any>).value = it.property.getter(match)
        }
    }

    private fun performMagic() {
        val title = titleEditor.value
        val authors = authorsEditor.value
        if (book.isbn.isEmpty() && (title.isEmpty() || authors.isEmpty())) {
            app.toast(getString(R.string.edit_magic_missing_infos))
            return
        }
        val like = app.repository.newBook(book.isbn)
        if (book.id > 0) {
            // Clears the default 'like' location it doesn't apply on editing.
            like.location = null
        }
        like.title = title
        like.authors = authors
        val reporter = app.openReporter(getString(R.string.lookup_book))
        app.lookupService.lookup(like, stopAt = null) {
            reporter.close()
            if (it == null) {
                app.toast(getString(R.string.edit_no_match))
            } else {
                mergeFrom(it)
            }
        }
    }
}

