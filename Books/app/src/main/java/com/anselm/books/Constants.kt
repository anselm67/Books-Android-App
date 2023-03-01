package com.anselm.books

import android.util.Size

class Constants {

    companion object {
        const val LOCAL_FOLDER_NAME = "books"
        const val DRIVE_FOLDER_NAME = "Books"
        const val IMAGE_FOLDER_NAME = "images"      // Both local & drive.
        const val DATABASE_NAME = "books_database"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Safari/537.36"
        const val SYNC_PREFERENCES_NAME = "sync_preferences"
        val IMAGE_SIZE = Size(320, 427)
    }
}