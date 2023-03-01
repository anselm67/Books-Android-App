package com.anselm.books.ui.sync

class SyncException(
    message: String,
    cause: Exception? = null
): Exception(message, cause)