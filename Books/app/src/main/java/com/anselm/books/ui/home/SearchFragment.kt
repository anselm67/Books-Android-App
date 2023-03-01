package com.anselm.books.ui.home

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BookViewModel
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.BookDao
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.hideKeyboard
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.launch

class SearchFragment : ListFragment() {
    // Button's Drawable to use to open a filter dialog.
    private lateinit var filterDrawable: Drawable
    // Button's Drawable to use when a filter value is selected, to clear it out.
    private lateinit var clearFilterDrawable: Drawable
    private var totalCount: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        val safeArgs: SearchFragmentArgs by navArgs()
        Log.d(TAG, "safeArgs query=${safeArgs.query}")

        // Displays filters in this view, that's the whole point.
        binding.idSearchFilters.isVisible = true
        binding.idCountView.isVisible = true
        binding.fabScanButton.isVisible = false

        // Caches the drawable for the filter buttons.
        filterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_arrow_drop_down_24)!!
        clearFilterDrawable = ContextCompat.getDrawable(
            requireContext(), R.drawable.ic_baseline_clear_24)!!

        // Customizes the toolbar menu.
        handleMenu(
            MenuItemHandler(R.id.idSortByDateAdded, {
                changeSortOrder(BookDao.SortByDateAdded)
            }),
            MenuItemHandler(R.id.idSortByTitle, {
                changeSortOrder(BookDao.SortByTitle)
            }),
            MenuItemHandler(R.id.idSearchView, null) {
                bindSearch(it)
            }
        )

        /*
         * We start with a fresh query, initialized with our arguments.
         * WARNING: The bookViewModel assignment initializes bookViewModel, so we have to get
         * isInitialized *before* we execute it.
         */
        val isInitialized = isModelInitialized()
        bookViewModel = ViewModelProvider(this, BookViewModel.Factory)[BookViewModel::class.java]
        if (safeArgs.query != null && ! isInitialized) {
            changeQuery(safeArgs.query!!)
        } else {
            changeQuery(bookViewModel.query)
        }

        // Let's go.
        refreshUi()

        return root
    }

    override fun onSelectionStart() {
        super.onSelectionStart()
        binding.fabScanButton.isVisible = false
        binding.fabEditButton.isVisible = true
    }

    override fun onSelectionStop() {
        super.onSelectionStop()
        binding.fabScanButton.isVisible = false
        binding.fabEditButton.isVisible = false
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

    fun changeQueryAndUpdateUI(query: Query) {
        super.changeQuery(query)
        refreshUi()
    }

    private fun clearFilter(type: Label.Type) {
        val query = bookViewModel.query.copy()
        query.clearFilter(type)
        changeQueryAndUpdateUI(query)
    }
    
    private fun dialogFilter(type: Label.Type) {
        view?.let { activity?.hideKeyboard(it) }
        val action = SearchFragmentDirections.toSearchDialogFragment(
            type, bookViewModel.query)
        findNavController().navigate(action)
    }

    data class Filter(
        val type: Label.Type,
        val button: Button,
        val filter: Query.Filter?,
        val label: Int,
    )

    private fun refreshUi() {
        // Refreshes the filters state.
        val filters = arrayOf(
            Filter(Label.Type.Location,
                binding.idLocationFilter,
                bookViewModel.query.firstFilter(Label.Type.Location),
                R.string.physicalLocationLabel),
            Filter(Label.Type.Genres,
                binding.idGenreFilter,
                bookViewModel.query.firstFilter(Label.Type.Genres),
                R.string.genreLabel),
            Filter(Label.Type.Publisher,
                binding.idPublisherFilter,
                bookViewModel.query.firstFilter(Label.Type.Publisher),
                R.string.publisherLabel),
            Filter(Label.Type.Authors,
                binding.idAuthorFilter,
                bookViewModel.query.firstFilter(Label.Type.Authors),
                R.string.authorLabel),
            Filter(Label.Type.Language,
                binding.idLanguageFilter,
                bookViewModel.query.firstFilter(Label.Type.Language),
                R.string.languageLabel),
        )
        val repository = app.repository
        viewLifecycleOwner.lifecycleScope.launch {
            for (f in filters) {
                if (f.filter != null) {
                    f.button.text = repository.label(f.filter.labelId).name
                    f.button.typeface = Typeface.create(f.button.typeface, Typeface.BOLD)
                    f.button.setOnClickListener { clearFilter(f.type) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, clearFilterDrawable, null
                    )
                } else {
                    f.button.text = getString(f.label)
                    f.button.setOnClickListener { dialogFilter(f.type) }
                    f.button.setCompoundDrawablesWithIntrinsicBounds(
                        /* left, top, right, bottom */
                        null, null, filterDrawable, null
                    )
                }
            }
            // Refreshes the book count.
            totalCount = repository.getPagedListCount(bookViewModel.query)
            binding.idCountView.text = getString(R.string.item_count_format, totalCount)
        }
    }

    private fun bindSearch(item: MenuItem) {
        // Handles the search view:

        // Expands the menu item.
        // As this pushes an event to the backstack, we need to pop it automatically so that
        // when back is pressed it backs out to HomeFragment rather than just collapsing
        // the SearchView.

        item.expandActionView()
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean  = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                findNavController().popBackStack()
                return true
            }
        })

        // Customizes the search view's action view.
        (item.actionView as SearchView).let {
            it.setQuery(bookViewModel.query.query, false)
            it.isIconified = false
            it.clearFocus()
            it.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?): Boolean {
                    changeQueryAndUpdateUI(bookViewModel.query.copy(
                        query = text, partial = false))
                    return false
                }
                override fun onQueryTextChange(text: String?): Boolean {
                    val emptyText = (text == null || text == "")
                    changeQueryAndUpdateUI(bookViewModel.query.copy(
                        query = if  (emptyText) null else text,
                        partial = ! emptyText))
                    return true
                }
            })
            it.setOnCloseListener {
                this.findNavController().popBackStack()
                false
            }
        }
    }

    /**
     * Collects and sets up the return value from our filter dialog.
     * This is largely inspired by this link:
     * https://developer.android.com/guide/navigation/navigation-programmatic
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        val navBackStackEntry = navController.getBackStackEntry(R.id.nav_search)

        // Create our observer and add it to the NavBackStackEntry's lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME
                && navBackStackEntry.savedStateHandle.contains("filter")) {
                val result =
                    navBackStackEntry.savedStateHandle.get<Query.Filter>("filter")
                if (result != null) {
                    val query = bookViewModel.query.copy()
                    query.setOrReplace(result)
                    changeQueryAndUpdateUI(query)
               }
            }
        }
        navBackStackEntry.lifecycle.addObserver(observer)

        // As addObserver() does not automatically remove the observer, we
        // call removeObserver() manually when the view lifecycle is destroyed
        viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                navBackStackEntry.lifecycle.removeObserver(observer)
            }
        })
    }
}


