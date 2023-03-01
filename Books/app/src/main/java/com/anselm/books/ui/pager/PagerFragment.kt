package com.anselm.books.ui.pager

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.databinding.FragmentPagerBinding
import kotlinx.coroutines.launch

class PagerViewModel: ViewModel() {
    var bookIds = emptyList<Long>().toMutableList()
    var position: Int = 0
}

class PagerFragment: Fragment() {
    private var _binding: FragmentPagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: BookPagerAdapter
    private lateinit var viewModel: PagerViewModel
    private var onBackPressedCallback: OnBackPressedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "PagerFragment.onBackPressedCallback.")
                val savedStateHandle =  findNavController()
                    .previousBackStackEntry
                    ?.savedStateHandle
                if (savedStateHandle?.contains("bookDeleted") == true) {
                    savedStateHandle.remove<Boolean>("bookDeleted")
                    deleteCurrentAndContinue()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        /**
         * When coming back from EditFragment/delete, DetailsFragment tries to restart - on a
         * deleted book - while PagerFragment tries to keep going by switching to a different book.
         * Unless PagerFragment onBackPress intervenes, PagerFragment is destroyed.
         * The goal of the back callback is to prevent this from happening, specifically in this
         * case and in the - simpler - case of DetailsFragment/delete.
         * Why it needs to be attached to the activity scope, I do not know. But if attached to the
         * fragment - this - it gets canceled before it can kick in to prevent its own destruction.
         */
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(),
            onBackPressedCallback!!,
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        Log.d(TAG, "Callback removed: $onBackPressedCallback")
        onBackPressedCallback?.remove()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentPagerBinding.inflate(inflater, container, false)
        val safeArgs: PagerFragmentArgs by navArgs()

        val isModelInitialized = ::viewModel.isInitialized
        viewModel = ViewModelProvider(this)[PagerViewModel::class.java]

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bookIds = app.repository.getIdsList(safeArgs.query).toMutableList()
                if (!isModelInitialized) {
                    viewModel.position = safeArgs.position
                }
                setupAdapter()
            }
        }
        return binding.root
    }

    private fun setupAdapter() {
        adapter = BookPagerAdapter(this, viewModel.bookIds)
        binding.idPager.adapter = adapter
        binding.idPager.registerOnPageChangeCallback(object: OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.position = position
                updatePosition()
            }
        })
        binding.idPager.setCurrentItem(viewModel.position, false)
    }

    private fun deleteCurrentAndContinue() {
        val position = viewModel.position
        if (position >= 0 && position < viewModel.bookIds.size) {
            viewModel.bookIds.removeAt(position)
            binding.idPager.adapter?.notifyItemRemoved(position)
        }
        var newPosition = position
        if (newPosition >= viewModel.bookIds.size) {
            if (viewModel.bookIds.isEmpty()) {
                findNavController().popBackStack()
            } else {
                newPosition = viewModel.bookIds.size - 1
            }
        }
        binding.idPager.currentItem = newPosition
        updatePosition()
    }

    private fun updatePosition() {
        val position = viewModel.position
        val itemCount = adapter.itemCount
        app.title = getString(R.string.pager_position, position + 1, itemCount)
    }
}