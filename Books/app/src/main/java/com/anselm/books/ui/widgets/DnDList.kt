package com.anselm.books.ui.widgets

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Label
import com.anselm.books.databinding.RecyclerviewEditLabelItemBinding

private class LabelArrayAdapter(
    var dataSource: MutableList<Label>,
    private val onChange: ((List<Label>) -> Unit)? = null
): RecyclerView.Adapter<LabelArrayAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: RecyclerviewEditLabelItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(label: Label) {
            binding.labelView.text = label.name
            binding.idDelete.setOnClickListener {
                this@LabelArrayAdapter.delete(this.bindingAdapterPosition)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            RecyclerviewEditLabelItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataSource[position])
    }

    override fun getItemCount(): Int {
        return dataSource.size
    }

    val differ = AsyncListDiffer(
        this, object : DiffUtil.ItemCallback<Label>() {
            override fun areItemsTheSame(oldItem: Label, newItem: Label) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: Label, newItem: Label) =
                oldItem == newItem
        }
    )

    fun moveItem(from: Int, to: Int) {
        val fromLocation = dataSource[from]
        dataSource[from] = dataSource[to]
        dataSource[to] = fromLocation
        differ.submitList(dataSource)
        onChange?.invoke(dataSource)
        app.postOnUiThread { notifyItemMoved(from, to) }

    }

    fun delete(position: Int) {
        dataSource.removeAt(position)
        differ.submitList(dataSource)
        app.postOnUiThread { notifyItemRemoved(position) }
        onChange?.invoke(dataSource)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun replaceData(newLabels: List<Label>) {
        dataSource.clear()
        dataSource.addAll(newLabels)
        differ.submitList(dataSource)
        app.postOnUiThread { notifyDataSetChanged() }
    }

    fun add(label: Label) {
        dataSource.add(label)
        differ.submitList(dataSource)
        app.postOnUiThread { notifyItemInserted(dataSource.size - 1) }
    }
}

class DnDList(
    val list: RecyclerView,
    labels: MutableList<Label>,
    onChange: ((List<Label>) -> Unit)? = null,
) {

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    or ItemTouchHelper.START or ItemTouchHelper.END, 0
        ) {

            override fun onMove(recyclerView: RecyclerView,
                                viewHolder: RecyclerView.ViewHolder,
                                target: RecyclerView.ViewHolder): Boolean {
                val adapter = (recyclerView.adapter as LabelArrayAdapter)
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }

    init {
        val adapter = LabelArrayAdapter(labels, onChange)
        itemTouchHelper.attachToRecyclerView(list)
        adapter.differ.submitList(labels)
        list.adapter = adapter
        list.layoutManager = LinearLayoutManager(list.context)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setLabels(labels: List<Label>) {
        val adapter = (list.adapter as LabelArrayAdapter)
        adapter.replaceData(labels)
    }

    fun getLabels(): List<Label> {
        return (list.adapter as LabelArrayAdapter).dataSource
    }

    fun addLabel(label: Label): Boolean {
        val labels = (list.adapter as LabelArrayAdapter).dataSource
        return if ( ! labels.contains(label) ) {
            val adapter = (list.adapter as LabelArrayAdapter)
            adapter.add(label)
            true
        } else {
            false
        }
    }
}