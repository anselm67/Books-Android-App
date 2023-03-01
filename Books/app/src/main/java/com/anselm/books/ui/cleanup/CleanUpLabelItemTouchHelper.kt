package com.anselm.books.ui.cleanup

import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.LEFT
import androidx.recyclerview.widget.ItemTouchHelper.RIGHT
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.TAG
import com.anselm.books.ui.widgets.TrashItemTouchHelper

/**
 * For merging items in recycler view:
 * https://stackoverflow.com/questions/70226403/merge-items-in-recycler-view-when-dragged-dropped-on-one-another-in-android
 */

class CleanUpLabelItemTouchHelper(
    private val fragment: CleanUpLabelFragment,
) :  TrashItemTouchHelper(UP or DOWN or START or END, RIGHT or LEFT) {
    var target: RecyclerView.ViewHolder? = null
    private var moving: RecyclerView.ViewHolder? = null

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        fragment.promptForDelete(viewHolder.bindingAdapterPosition)
    }

    override fun onMove(recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean {
        this.target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
        this.target = target
        this.moving = viewHolder
        (target as LabelCleanupArrayAdapter.ViewHolder).onTarget()
        Log.d(TAG, "onMove ${this.target == this.moving}")
        return true
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.5f
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        Log.d(TAG, "clearView")
        target?.let { (it as LabelCleanupArrayAdapter.ViewHolder).offTarget() }
        fragment.promptForMerge(
            moving?.bindingAdapterPosition ?: -1,
            target?.bindingAdapterPosition ?: -1,
        )
        target = null
        moving = null
        viewHolder.itemView.alpha = 1f
    }
}

