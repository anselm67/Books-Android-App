package com.anselm.books.ui.home

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.database.BookDao
import com.anselm.books.database.Histo
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.SearchDialogFragmentBinding
import com.anselm.books.databinding.SearchDialogItemLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch


class HistoAdapter(
    private val dataSource: List<Histo>,
    private val onClick: (Histo) -> Unit
): RecyclerView.Adapter<HistoAdapter.ViewHolder>() {

    class ViewHolder(private val binding: SearchDialogItemLayoutBinding,
                     private val onClick: (Histo) -> Unit)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(histo: Histo) {
            binding.idHistoValueView.text = histo.text
            binding.idHistoCountView.text = histo.count.toString()
            binding.root.setOnClickListener { _: View -> histo.let { this.onClick(it) } }
        }
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    override fun onCreateViewHolder(parent: ViewGroup,viewType: Int): ViewHolder =
        ViewHolder(SearchDialogItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent, false), onClick)


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }
}

class SearchDialogFragment: BottomSheetDialogFragment() {
    private lateinit var adapter: HistoAdapter
    private val dataSource: MutableList<Histo> = mutableListOf()

    private var type = Label.Type.Location
    private var query = Query()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // set custom style for bottom sheet rounded top corners
        setStyle(STYLE_NORMAL, R.style.DialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = SearchDialogFragmentBinding.inflate(inflater, container, false)

        // Selects the column on which to filter.
        val safeArgs: SearchDialogFragmentArgs by navArgs()
        type = safeArgs.type
        if ( safeArgs.query != null ) {
            query = safeArgs.query!!
        }

        // Prepares the recycler view and kicks the values fetch.
        adapter = HistoAdapter(dataSource, onClick = { h: Histo -> selectHisto(h)  })
        binding.idHistoList.let {
            it.adapter = adapter
            it.layoutManager = LinearLayoutManager(requireActivity())
            it.addItemDecoration(DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL))
        }
        loadValues()

        // Handles cancellation without value selected.
        binding.idCancelDialog.setOnClickListener {
            dismiss()
        }

        binding.idAutoComplete.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

            override fun afterTextChanged(s: Editable?) {
                val labelQuery = s.toString()
                if (labelQuery.isEmpty()) {
                    loadValues()
                } else {
                    loadValues(s.toString() + "*")
                }
            }
        })
        return binding.root
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun updateData(histos: List<Histo>) {
        dataSource.clear()
        dataSource.addAll(histos)
        adapter.notifyDataSetChanged()
    }

    private fun loadValues(labelQuery: String? = null) {
        val repository = app.repository
        viewLifecycleOwner.lifecycleScope.launch {
            updateData(repository.getHisto(type, labelQuery, BookDao.SortByCount, query))
        }
    }

    /**
     * Selects the given [histo] value for the filter, and dismisses the dialog.
     */
    private fun selectHisto(histo: Histo) {
        findNavController().previousBackStackEntry?.savedStateHandle?.set(
            "filter", Query.Filter(type, histo.labelId))
        dismiss()
    }

//    requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParam s.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //  if you wanna show the bottom sheet as full screen,
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { dialog: DialogInterface ->
            (dialog as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.let {
                    val layoutParams = it.layoutParams
                    layoutParams.height = Resources.getSystem().displayMetrics.heightPixels
                    it.layoutParams = layoutParams
                    BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return bottomSheetDialog
    }
}