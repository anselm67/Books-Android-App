package com.anselm.books

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.database.BookRepository
import com.anselm.books.database.BookRepositoryListener
import com.anselm.books.database.Label

class LastLocationPreference(
    private val repository: BookRepository,
    private var lastLocation: Label? = null,
    private var isEnabled: Boolean = false,
) {
    private var preferenceListener: OnSharedPreferenceChangeListener

    init {
        isEnabled = app.prefs.getBoolean(BooksPreferences.USE_LAST_LOCATION, true)
        if (isEnabled) {
            getLastLocation()
        }
        // Listens to enable/disable signal from a property change.
        preferenceListener = object: OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(
                prefs: SharedPreferences?,
                key: String?
            ) {
                if (prefs == null || key != BooksPreferences.USE_LAST_LOCATION) {
                    return
                }
                val newValue = prefs.getBoolean(BooksPreferences.USE_LAST_LOCATION, true)
                if (newValue != isEnabled) {
                    lastLocation = null
                }
                val editor = prefs.edit()
                editor.putString(BooksPreferences.USE_LAST_LOCATION_VALUE, "")
                editor.apply()
                isEnabled = newValue
            }
        }
        app.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)

        // Hook into the repository to update our last known location.
        repository.addBookListener(object : BookRepositoryListener {
            override fun onBookDeleted(book: Book) { }
            override fun onBookUpdated(book: Book) { }

            override fun onBookInserted(book: Book) {
                handleInsert(book)
            }

            override fun onBookCreated(book: Book) {
                if ( isEnabled && lastLocation != null) {
                    book.location = getLastLocation()
                }
            }
        })
    }

    private fun handleInsert(book: Book) {
        if (!isEnabled || (book.location == lastLocation) || (book.location == null)) {
            return
        }
        lastLocation = book.location
        val editor = app.prefs.edit()
        editor.putString(BooksPreferences.USE_LAST_LOCATION_VALUE, lastLocation!!.name)
        editor.apply()
    }

    fun getLastLocation(): Label? {
        if (lastLocation == null) {
            val locationName = app.prefs.getString(BooksPreferences.USE_LAST_LOCATION_VALUE, "")!!
            if ( locationName.isNotEmpty()) {
                lastLocation = repository.labelB(Label.Type.Location, locationName)
            }
        }
        return lastLocation
    }

}