package com.anselm.books.ui.edit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.Property
import com.anselm.books.R
import com.anselm.books.database.Label
import com.anselm.books.databinding.BottomSheetMultiEditDialogBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class EditMultiDialogFragment: BottomSheetDialogFragment() {
    private var _binding: BottomSheetMultiEditDialogBinding? = null
    private val binding get() = _binding!!
    private val editors = emptyList<Editor<*, *>>().toMutableList()
    private lateinit var bookIds: List<Long>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val safeArgs: EditMultiDialogFragmentArgs by navArgs()
        bookIds = safeArgs.bookIds.toList()
        _binding = BottomSheetMultiEditDialogBinding.inflate(inflater, container, false)
        binding.idHeader.text = getString(R.string.edit_multiple_books, bookIds.size)

        binding.idCancelDialog.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.idApplyButton.setOnClickListener {
            applyChanges()
        }
        bind(inflater)

        handleMenu(requireActivity())
        updateApplyButton()
        return binding.root
    }

    private fun isChanged(): Boolean {
        var changed = false
        editors.forEach {
            if (it.isChanged()) {
                changed = true
                it.saveChange()
            }
        }
        return changed
    }

    private fun applyChanges() {
        // No changes, good news.
        if ( ! isChanged() )
            return
        // Gets the work done, even when painful.
        val reporter = app.openReporter(getString(R.string.saving_changes), isIndeterminate = false)
        app.applicationScope.launch {
            var count = 0
            bookIds.map { bookId ->
                val target = app.repository.load(bookId, true) ?: return@launch
                Property.setIfEmpty(
                    Pair(target::authors, values.authors),
                    Pair(target::genres, values.genres),
                    Pair(target::publisher, values.publisher),
                    Pair(target::language, values.language),
                    Pair(target::location, values.location),
                )
                count++
                app.repository.save(target)
                reporter.update(count, bookIds.size)
            }
            reporter.close()
            app.postOnUiThread { findNavController().popBackStack() }
        }
    }

    private fun handleMenu(menuHost: MenuHost) {
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.forEach {
                    it.isVisible = false
                }
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }


    private fun updateApplyButton() {
        binding.idApplyButton.isEnabled = editors.firstOrNull {
            Property.isNotEmpty(it.value)
        } != null
    }

    // This is just a holder for the values we want to collect.
    class Values(
        var authors: List<Label> = emptyList(),
        var genres: List<Label> = emptyList(),
        var publisher: Label? = null,
        var language: Label? = null,
        var location: Label? = null,
    )
    private val values = Values()

    private fun bind(inflater: LayoutInflater) {
        editors.addAll(arrayListOf(
            MultiLabelEditor(this, inflater, values, Values::authors,
                R.string.authorLabel, Label.Type.Authors) { updateApplyButton() },
            MultiLabelEditor(this, inflater, values, Values::genres,
                R.string.genreLabel, Label.Type.Genres) { updateApplyButton() },
            SingleLabelEditor(this, inflater, values, Values::publisher,
                R.string.publisherLabel, Label.Type.Publisher) { updateApplyButton() },
            SingleLabelEditor(this, inflater, values, Values::language,
                R.string.languageLabel, Label.Type.Language) { updateApplyButton() },
            SingleLabelEditor(this, inflater, values, Values::location,
                    R.string.physicalLocationLabel, Label.Type.Location) { updateApplyButton() }
        ))
        editors.forEach {
            it.setup(binding.idEditorContainer)?.let {
                    view -> binding.idEditorContainer.addView(view)
            }
        }
    }

}