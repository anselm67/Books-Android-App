package com.anselm.books.ui.home

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.anselm.books.BookViewModel
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.R
import com.anselm.books.database.BookDao
import com.anselm.books.database.Query
import com.anselm.books.databinding.BottomAddDialogBinding
import com.anselm.books.hideKeyboard
import com.anselm.books.showKeyboard
import com.anselm.books.ui.widgets.MenuItemHandler
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : ListFragment() {
    private var totalCount: Int = 0

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.idSearchFilters.isVisible = false
        binding.idCountView.isVisible = false
        binding.fabScanButton.isVisible = true
        binding.fabEditButton.isVisible = false

        // Handles the menu items we care about.
        handleMenu(
            MenuItemHandler(R.id.idGotoSearchView, {
                val action = HomeFragmentDirections.toSearchFragment(
                    Query(sortBy = bookViewModel.query.sortBy)
                )
                findNavController().navigate(action)
            }),
            MenuItemHandler(R.id.idSortByDateAdded, {
                changeSortOrder(BookDao.SortByDateAdded)
            }),
            MenuItemHandler(R.id.idSortByTitle, {
                changeSortOrder(BookDao.SortByTitle)
            })
        )

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                totalCount = app.repository.getTotalCount()
                app.title = getString(R.string.book_count, totalCount)
            }
        }
        binding.fabScanButton.setOnClickListener {
            showBottomAddDialog()
        }
        bookViewModel = ViewModelProvider(this, BookViewModel.Factory)[BookViewModel::class.java]

        changeQuery(bookViewModel.query)
        return root
    }

    override fun onSelectionChanged(selectedCount: Int) {
        super.onSelectionChanged(selectedCount)
        if (selectedCount == BookAdapter.ALL) {
            app.title = getString(R.string.book_selected_count, totalCount)
        } else if (selectedCount > 0) {
            app.title = getString(R.string.book_selected_count, selectedCount)
        } else {
            app.title = getString(R.string.book_count, totalCount)
        }
    }

    private fun showBottomAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val binding = BottomAddDialogBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)

        binding.idScan.setOnClickListener {
            val action = HomeFragmentDirections.toScanFragment()
            findNavController().navigate(action)
            dialog.dismiss()
        }
        binding.idType.setOnClickListener {
            val action = HomeFragmentDirections.toEditFragment(-1, app.repository.newBook())
            findNavController().navigate(action)
            dialog.dismiss()
        }
        binding.idIsbn.setOnClickListener {
            val enable = ! binding.idIsbnEdit.isVisible
            binding.idIsbnEdit.visibility =
                if (enable) { View.VISIBLE } else { View.GONE }
            binding.idIsbnButton.visibility = binding.idIsbnEdit.visibility
            if (enable) {
                binding.idIsbnEdit.requestFocus()
                requireContext().showKeyboard(binding.idIsbnEdit)
            } else {
                requireContext().hideKeyboard(binding.idIsbnEdit)
            }
            dialog.show()
        }
        binding.idIsbnEdit.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

            override fun afterTextChanged(s: Editable?) {
                val text = s.toString().trim()
                if ( ISBN.isValidEAN(text)) {
                    binding.idIsbnEdit.setTextColor(Color.BLACK)
                } else {
                    binding.idIsbnEdit.setTextColor(Color.RED)
                }
            }
        })
        binding.idIsbnButton.setOnClickListener{
            val isbn = binding.idIsbnEdit.text.toString().trim()
            view?.let { myself -> activity?.hideKeyboard(myself) }
            handleISBN(isbn)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun handleISBN(isbn: String) {
        var input = isbn
        if (isbn.length == 10) {
            // Convert it to an ISBN 13
            input = ISBN.toISBN13(isbn) ?: return
        } else if ( ! ISBN.isValidEAN13(input) ) {
            app.toast("Invalid ISBN number.")
            return
        }
        val reporter = app.openReporter(getString(R.string.looking_up_isbn))
        val like = app.repository.newBook(input)
        app.lookupService.lookup(like) { book ->
            if (book == null) {
                app.toast("Book not found.")
            } else {
                val activity = requireActivity()
                view?.let { myself -> activity.hideKeyboard(myself) }
                activity.lifecycleScope.launch(Dispatchers.Main) {
                    val action = HomeFragmentDirections.toEditFragment(-1, book)
                    findNavController().navigate(action)
                }
            }
            reporter.close()
        }
    }
}

