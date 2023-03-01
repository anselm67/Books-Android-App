package com.anselm.books.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingData
import androidx.recyclerview.widget.LinearLayoutManager
import com.anselm.books.BookViewModel
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Query
import com.anselm.books.databinding.FragmentListBinding
import com.anselm.books.ui.widgets.BookFragment
import com.anselm.books.ui.widgets.MenuItemHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class ListFragment: BookFragment() {
    private var _binding: FragmentListBinding? = null
    protected val binding get() = _binding!!
    protected lateinit var bookViewModel: BookViewModel
    private lateinit var adapter: BookAdapter

    protected fun isModelInitialized() = ::bookViewModel.isInitialized

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentListBinding.inflate(inflater, container, false)

        binding.fabEditButton.isVisible = false
        binding.fabEditButton.setOnClickListener{
            viewLifecycleOwner.lifecycleScope.launch {
                val bookIds = if (adapter.allSelected) {
                    app.repository.getIdsList(bookViewModel.query).toLongArray()
                } else {
                    adapter.getSelectedBookIds().toLongArray()
                }
                val action = HomeFragmentDirections.toEditMultiDialogFragment(bookIds)
                findNavController().navigate(action)
            }
        }
        handleMenu()
        bindAdapter()
        return binding.root
    }

    /**
     * Updates the query and let the repository knows.
     * This invalidates the underlying data source and optionally triggers a full rebinding of
     * the adapter - e.g. if you're changing the sort order.
     * This is overwritten by SearchFragment.
     */
    private var refreshJob: Job? = null
    protected open fun changeQuery(query: Query) {
        Log.d(TAG, "Change query to $query")
        if (refreshJob != null) {
            refreshJob?.cancel()
            refreshJob = null
        }
        bookViewModel.query = query
        bookViewModel.queryFlow.value = query
        adapter.submitData(lifecycle, PagingData.empty())
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bookViewModel.bookList.collectLatest { adapter.submitData(it) }
            }
        }
    }

    /**
     * Changes the sort order of the list and takes action to have the UI reflect the change.
     */
    protected fun changeSortOrder(sortOrder: Int) {
        changeQuery(bookViewModel.queryFlow.value.copy(sortBy = sortOrder))
    }

    /**
     * Binds a new adapter to the recycler.
     * Quite frankly this is meant to work around the weakness of recyclerview to handle
     * changing sort order. It tries to resync the pre/post lists and ends up in hell.
     */
    private fun bindAdapter() {
        val onEditClick: ((Int, Book) -> Unit)?
            = if (app.bookPrefs.enableShortcutToEdit) {
                { _, book -> onEditClick(book) }
            } else {
                null
            }
        // Creates the new adapter and restarts the jobs.
        adapter = BookAdapter(
            { position, _ -> onClick(position) },
            onEditClick,
            object: SelectionListener() {
                override fun onSelectionStart() {
                    this@ListFragment.onSelectionStart()
                }
                override fun onSelectionStop() {
                    this@ListFragment.onSelectionStop()
                }
                override fun onSelectionChanged(selectedCount: Int) {
                    this@ListFragment.onSelectionChanged(selectedCount)
                }
            }
        )

        // Collects from the state and updates the progress bar accordingly.
        /*
        viewLifecycleOwner.lifecycleScope.launch {
            val reporter = app.openReporter(getString(R.string.loading_books))
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                adapter.loadStateFlow.collect {
                    reporter.isLoading = it.source.prepend is LoadState.Loading
                                || it.source.append is LoadState.Loading
                }
            }
        }*/
        binding.list.adapter = adapter
        binding.list.layoutManager = LinearLayoutManager(binding.list.context)
    }

    private var menuItemHandlers: Array<out MenuItemHandler> = emptyArray()
    open fun onSelectionStart() {
        binding.fabScanButton.isVisible = false
        binding.fabEditButton.isVisible = true
        menuItemHandlers = handleMenu(
            MenuItemHandler(R.id.idSelectAll, {
                adapter.selectAll()
            }),
            MenuItemHandler(R.id.idCancelView, {
                adapter.cancelSelection()
            })
        )
    }

    open fun onSelectionStop() {
        binding.fabScanButton.isVisible = true
        binding.fabEditButton.isVisible = false
        handleMenu(*menuItemHandlers)
    }

    open fun onSelectionChanged(selectedCount: Int) { }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onClick(position: Int) {
        val action = HomeFragmentDirections.toPagerFragment(
            query = bookViewModel.query, position = position
        )
        findNavController().navigate(action)
    }

    private fun onEditClick(book: Book) {
        val action = HomeFragmentDirections.toEditFragment(book.id)
        findNavController().navigate(action)
    }
}

