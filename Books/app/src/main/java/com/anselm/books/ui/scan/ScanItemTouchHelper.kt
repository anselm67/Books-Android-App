package com.anselm.books.ui.scan

import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.ui.widgets.TrashItemTouchHelper


class ScanItemTouchHelper(
    private val adapter: IsbnArrayAdapter,
): TrashItemTouchHelper(0, RIGHT or LEFT, trashIconPadding=25F) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.removeAt(viewHolder.bindingAdapterPosition)
    }
}