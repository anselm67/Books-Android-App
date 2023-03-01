package com.anselm.books.ui.home

abstract class SelectionListener {
    abstract fun onSelectionStart()
    abstract fun onSelectionStop()
    abstract fun onSelectionChanged(selectedCount: Int)
}