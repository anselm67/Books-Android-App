package com.anselm.books.ui.widgets

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.anselm.books.BooksApplication
import com.anselm.books.R


abstract class TrashItemTouchHelper(
    dragDirs: Int,
    swipeDirs: Int,
    private val trashIconPadding: Float = 2F,
) : ItemTouchHelper.SimpleCallback(dragDirs, swipeDirs) {

    private var icon: Drawable = ContextCompat.getDrawable(
        BooksApplication.app.applicationContext,
        R.drawable.ic_baseline_delete_24
    )!!

    private val greenPaint = Paint().apply {
        color = Color.parseColor("#50EEB7")
    }

    // https://medium.com/@zackcosborn/step-by-step-recyclerview-swipe-to-delete-and-undo-7bbae1fce27e
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // Get RecyclerView item from the ViewHolder
            val item: View = viewHolder.itemView
            val dx = dX.toInt()
            val padding = (trashIconPadding /* dp */ * BooksApplication.app.displayMetrics.density).toInt()
            val width = item.bottom - item.top - 2 * padding
            icon.bounds = if ( dX > 0) {
                c.drawRect(Rect(0, item.top, dx, item.bottom), greenPaint)
                Rect(
                    dx - width,
                    item.top + padding,
                    dx,
                    item.bottom - padding
                )
            } else {
                c.drawRect(Rect(item.right+dx, item.top, item.right-dx, item.bottom), greenPaint)
                Rect(
                    item.right + dx,
                    item.top + padding,
                    item.right + dx + width,
                    item.bottom - padding,
                )
            }
            DrawableCompat.setTint(icon, Color.WHITE)
            icon.draw(c)
        }
    }

}