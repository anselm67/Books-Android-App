package com.anselm.books.ui.scan

import androidx.lifecycle.ViewModel

class ScanViewModel: ViewModel() {
    val lookupResults = emptyList<LookupResult>().toMutableList()
    var isDone: Boolean = false
    var stats: LookupStats = LookupStats()
}
