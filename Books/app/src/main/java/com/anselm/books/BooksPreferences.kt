package com.anselm.books

import android.content.SharedPreferences
import android.util.Log
import com.anselm.books.database.BookDao
import kotlin.reflect.KMutableProperty0

class BooksPreferences(
    private val prefs: SharedPreferences
) {
    var useGoogle = true
    var useBNF = true
    var useiTunes = true
    var useWorldcat = true
    var useAmazon = true
    var useOpenLibrary = true
    var useOnlyExistingGenres = false

    var displayLastModified = true
    var displayBookId = false
    var enableShortcutToEdit = false

    var wskey = ""
    var sortOrder = BookDao.SortByDateAdded

    private lateinit var preferenceMap: Map<String, Pair<Boolean, KMutableProperty0<Boolean>>>

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            val prop = preferenceMap.getOrDefault(key, null)
            if (prop != null) {
                val value = (prefs?.getBoolean(key, false) == true)
                prop.second.setter.invoke(value)
                Log.d(TAG, "$key changed to $value")
            } else if (key == OCLC_KEY) {
                wskey = prefs?.getString(key, "")!!
            } else if (key == SORT_ORDER) {
                sortOrder = when (prefs.getString(SORT_ORDER, "DateAdded")) {
                    "DateAdded" -> BookDao.SortByDateAdded
                    "Alphabetical" -> BookDao.SortByTitle
                    else -> BookDao.SortByDateAdded
                }
            }
        }

    init {
        BooksApplication.app.prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        preferenceMap = mapOf(
            USE_ONLY_EXISTING_GENRES to Pair(false, ::useOnlyExistingGenres),
            DISPLAY_LAST_MODIFIED to Pair(false, ::displayLastModified),
            DISPLAY_BOOK_ID to Pair(false, ::displayBookId),
            ENABLE_SHORTCUT_TO_EDIT to Pair(false, ::enableShortcutToEdit),
            USE_GOOGLE to Pair(true, ::useGoogle),
            USE_ITUNES to Pair(true, ::useiTunes),
            USE_WORLDCAT to Pair(true, ::useWorldcat),
            USE_AMAZON to Pair(true, ::useAmazon),
            USE_OPEN_LIBRARY to Pair(false, ::useOpenLibrary),
            USE_BNF to Pair(true, ::useBNF),
        )
        preferenceMap.forEach { (key: String, prop: Pair<Boolean, KMutableProperty0<Boolean>>) ->
            prop.second.setter(prefs.getBoolean(key, prop.first))
        }
        wskey = prefs.getString(OCLC_KEY, "")!!
    }

    companion object {
        const val OCLC_KEY = "oclc_wskey"
        const val USE_WORLDCAT = "use_worldcat"
        const val USE_ONLY_EXISTING_GENRES = "lookup_use_only_existing_genres"
        const val DISPLAY_LAST_MODIFIED = "display_last_modified"
        const val DISPLAY_BOOK_ID = "display_book_id"
        const val ENABLE_SHORTCUT_TO_EDIT = "enable_shortcut_to_edit"
        const val USE_GOOGLE = "use_google"
        const val USE_ITUNES = "use_itunes"
        const val USE_AMAZON = "use_amazon"
        const val USE_OPEN_LIBRARY = "use_open_library"
        const val USE_BNF = "use_bnf"
        const val USE_LAST_LOCATION = "lookup_use_last_location"
        const val USE_LAST_LOCATION_VALUE = "lookup_use_last_location_value"
        const val SORT_ORDER = "sort_order"
    }

}
