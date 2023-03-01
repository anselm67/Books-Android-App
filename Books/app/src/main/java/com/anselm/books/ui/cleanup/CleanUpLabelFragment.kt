package com.anselm.books.ui.cleanup

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.R
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.FragmentCleanupLabelBinding
import com.anselm.books.databinding.RecyclerviewLabelCleanupItemBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch

class CleanUpLabelFragment: BookFragment() {
    private var _binding: FragmentCleanupLabelBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: LabelCleanupArrayAdapter
    private lateinit var type: Label.Type

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentCleanupLabelBinding.inflate(inflater, container, false)

        val safeArgs: CleanUpLabelFragmentArgs by navArgs()
        type = safeArgs.type

        viewLifecycleOwner.lifecycleScope.launch {
            adapter = LabelCleanupArrayAdapter(app.repository.getLabels(type).toMutableList()) {
                val action = CleanUpLabelFragmentDirections.toSearchFragment(
                    Query().apply { filters = Query.asFilter(it) }
                )
                findNavController().navigate(action)
            }
            ItemTouchHelper(CleanUpLabelItemTouchHelper(this@CleanUpLabelFragment))
                .attachToRecyclerView(binding.idLabelRecyclerView)
            binding.idLabelRecyclerView.adapter = adapter
            binding.idLabelRecyclerView.layoutManager = LinearLayoutManager(
                binding.idLabelRecyclerView.context
            )
            binding.idLabelRecyclerView.addItemDecoration(
                DividerItemDecoration(requireActivity(), RecyclerView.VERTICAL))
        }
        binding.idSearchLabel.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }

            override fun afterTextChanged(s: Editable?) {
                val labelQuery = s.toString()
                if (labelQuery.isEmpty()) {
                    loadLabels()
                } else {
                    loadLabels(s.toString() + '*')
                }
            }
        })
        super.handleMenu()
        return binding.root
    }

    fun promptForMerge(from: Int, to: Int) {
        val fromLabel = adapter.label(from)
        val intoLabel = adapter.label(to)
        if (fromLabel == null || intoLabel == null) {
            return
        }
        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(getString(R.string.merge_labels_prompt, fromLabel.name, intoLabel.name))
            .setPositiveButton(R.string.yes) { _, _ -> adapter.merge(from, to) }
            .setNegativeButton(R.string.no) { _, _ -> }
            .show()
    }

    fun promptForDelete(position: Int) {
        val label = adapter.label(position) ?: return
        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(getString(R.string.delete_label_prompt, label.name))
            .setPositiveButton(R.string.yes) { _, _ -> adapter.removeAt(position) }
            .setNegativeButton(R.string.no) { _, _ -> adapter.notifyItemChanged(position)}
            .show()
    }

    private fun loadLabels(labelQuery: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val labels = app.repository.searchLabels(type, labelQuery)
            adapter.updateData(labels)
        }
    }
}

class LabelCleanupArrayAdapter(
    val labels: MutableList<Label>,
    val onClick: (Label) -> Unit,
): RecyclerView.Adapter<LabelCleanupArrayAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: RecyclerviewLabelCleanupItemBinding,
    ): RecyclerView.ViewHolder(binding.root) {

        private fun getDrawable(resId: Int): Drawable {
            return ContextCompat.getDrawable(
                binding.idEditLabel.context, resId
            )!!
        }

        private fun editLabel(label: Label) {
            binding.idLabelText.isVisible = false
            binding.idLabelEditor.isVisible = true
            binding.idEditLabel.setImageDrawable(getDrawable(R.drawable.ic_baseline_check_24))
            binding.idEditLabel.setOnClickListener {
                val newName = binding.idLabelEditor.text.toString().trim()
                binding.idLabelText.isVisible = true
                binding.idLabelEditor.isVisible = false
                binding.idEditLabel.setImageDrawable(getDrawable(R.drawable.ic_baseline_mode_edit_24))
                if (newName != label.name) {
                    binding.idLabelText.text = newName
                    app.applicationScope.launch {
                        app.repository.rename(label, newName)
                    }
                }
            }
        }

        fun bind(label: Label) {
            binding.idLabelText.text = label.name
            binding.idLabelEditor.setText(label.name)
            binding.idEditLabel.setOnClickListener {
                editLabel(label)
            }
            binding.root.setOnClickListener {
                onClick.invoke(label)
            }
        }

        fun onTarget() {
            binding.idLabelCleanupContainer.background =
                ResourcesCompat.getDrawable(
                    app.resources, R.drawable.textview_border, null)
        }

        fun offTarget() {
            binding.idLabelCleanupContainer.background = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewLabelCleanupItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(labels[position])
    }

    override fun getItemCount(): Int {
        return labels.size
    }

    fun removeAt(position: Int) {
        val label = labels[position]
        app.applicationScope.launch {
            app.repository.deleteLabel(label)
            labels.removeAt(position)
            app.postOnUiThread { notifyItemRemoved(position) }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newLabels: List<Label>) {
        labels.clear()
        labels.addAll(newLabels)
        notifyDataSetChanged()
    }

    fun label(position: Int): Label? {
        return if (position >= 0 && position < labels.size) {
            labels[position]
        } else {
            null
        }
    }

    fun merge(from: Int, to: Int) {
        val fromLabel = label(from)
        val intoLabel = label(to)
        if (fromLabel != null && intoLabel != null) {
            app.applicationScope.launch {
                app.repository.mergeLabels(fromLabel, intoLabel)
            }
            removeAt(from)
        }
    }
}

