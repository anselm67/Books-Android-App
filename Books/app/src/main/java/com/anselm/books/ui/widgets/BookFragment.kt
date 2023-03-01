package com.anselm.books.ui.widgets

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.anselm.books.MainActivity
import com.anselm.books.R

data class MenuItemHandler(
    val menuId: Int,
    val handler: (() -> Unit)? = null,
    val prepare: ((MenuItem) -> Unit)? = null
)

open class BookFragment: Fragment() {
    private var menuProvider: MenuProvider? = null
    private var listOfHandlers: Array<out MenuItemHandler> = emptyArray()

    /*
     * Displays and installs the provided menu items, returns the current settings sp they can be
     * restored if needed.
     */
    protected fun handleMenu(vararg items: MenuItemHandler): Array<out MenuItemHandler> {
        val activity = requireActivity()
        val returnValue = listOfHandlers
        if (menuProvider != null) {
            activity.removeMenuProvider(menuProvider!!)
            menuProvider = null
        }
        listOfHandlers = items
        menuProvider = object: MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Everything is invisible ...
                menu.forEach { it.isVisible = (it.itemId== R.id.idCheckMark) }
                // Unless requested by the fragment.
                items.forEach { handler ->
                    menu.findItem(handler.menuId)?.let {
                        handler.prepare?.invoke(it)
                        it.isVisible = true
                    }
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                val found = items.firstOrNull { menuItem.itemId == it.menuId }
                return if (found != null) {
                    found.handler?.invoke()
                    true
                } else {
                    false
                }
            }
        }
        activity.addMenuProvider(menuProvider!!, viewLifecycleOwner, Lifecycle.State.RESUMED)
        return returnValue
    }

    override fun onDestroy() {
        super.onDestroy()
        menuProvider = null
    }

    fun checkCameraPermission(): Boolean {
        return ((requireActivity() as MainActivity).checkCameraPermission())
    }
}