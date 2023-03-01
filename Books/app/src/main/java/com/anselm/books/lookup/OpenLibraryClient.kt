package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import okhttp3.Call
import org.json.JSONObject

class OpenLibraryClient: JsonClient() {
    private val basedir = "https://openlibrary.org"

    private fun firstOrEmpty(obj: JSONObject, key: String): String {
        val value = obj.optJSONArray(key) ?: return ""
        return value.optString(0, "")
    }

    private val languages = mapOf (
        "/languages/fre" to "French",
        "/languages/eng" to "English",
        "/languages/spa" to "Spanish",
        "/languages/ita" to "Italian",
    )

    private fun firstKeyOrNull(obj: JSONObject, key: String): String? {
        val values = obj.optJSONArray(key) ?: return null
        if (values.length() > 0) {
            val keyValue = values.get(0)
            if (keyValue is JSONObject) {
                return keyValue.optString("key")
            }
        }
        return null
    }

    private fun language(obj: JSONObject): String {
        val tag = firstKeyOrNull(obj, "languages") ?: return ""
        return languages[tag] ?: ""
    }

    private fun coverUrl(obj: JSONObject): String {
        val coverId = firstOrEmpty(obj, "covers")
        if (coverId != "") {
            return "https://covers.openlibrary.org/b/id/${coverId}-L.jpg"
        }
        return ""
    }

    // typedObject -> { "type": "/type/<some-type>", "valueKey": "the value" }
    private fun extractValue(typedObject: JSONObject?, valueKey: String): String {
        val value = typedObject?.optString(valueKey, "")
        return value ?: ""
    }

    // authors -> {JSONArray@26790} "[{"type":{"key":"\/type\/author_role"},"author":{"key":"\/authors\/OL35793A"}},{"type":{"key":"\/type\/author_role"},"author":{"key":"\/authors\/OL892424A"}}]"
    private fun extractAuthorsFromWork(work: JSONObject): List<String>? {
        val list = work.optJSONArray("authors") ?: return null
        if (list.length() == 0) {
            return null
        }
        val keys = mutableListOf<String>()
        for (i in 0 until list.length()) {
            val value = list.get(i)
            if (value is JSONObject) {
                val key = value.optJSONObject("author")?.optString("key")
                if (key != null) {
                    keys.add(key)
                }
            }
        }
        return keys
    }

    private fun doAuthors(
        tag: String,
        book: Book,
        work:JSONObject,
        onCompletion: () -> Unit,
    ) {
        val keys = extractAuthorsFromWork(work)
        if (keys == null || keys.isEmpty()) {
            Log.d(TAG, "$tag - no extracted authors. done.")
            onCompletion()
            return
        }
        Log.d(TAG, "$tag looking for ${keys.size}")
        val calls = arrayOfNulls<Call>(keys.size)
        val values = arrayOfNulls<String>(keys.size)
        var done = 0
        for (i in keys.indices) {
            val url = "https://openlibrary.org${keys[i]}.json"
            calls[i] = request(tag, url)
                .onResponse { response ->
                    val obj = parse(response)
                    if (obj != null) {
                        values[i] = obj.optString("name")
                    }
                    Log.d(TAG, "$tag ${done + 1} of ${keys.size}")
                    if (++done == keys.size) {
                        book.authors = values
                            .filter { ! it.isNullOrEmpty() }
                            .map { app.repository.labelB(Label.Type.Authors, it!!) }
                        onCompletion()
                    }
                }
                .onError {
                    Log.e(TAG, "$url: http request failed.", it)
                    for (call in calls) {
                        call?.cancel()
                    }
                    onCompletion()
                }
                .run()
        }
    }

    private fun getDescription(work: JSONObject): String {
        val value = work.opt("description")
        return if (value is String) {
            // We might have a direct string description.
            value
        } else {
            // Or an RDF-like typed value:
            extractValue(work.optJSONObject("description"), "value")
        }
    }

    private fun setupWorkAndAuthors(
        tag: String,
        book: Book,
        work:JSONObject,
        onCompletion: () -> Unit
    ) {
        setIfEmpty(
            Pair(book::summary, getDescription(work)),
            Pair(book::genres, if (app.bookPrefs.useOnlyExistingGenres) {
                arrayToList<String>(work.optJSONArray("subjects"))
                    .mapNotNull { app.repository.labelIfExistsB(Label.Type.Genres, it) }
            } else {
                book.genres = arrayToList<String>(work.optJSONArray("subjects"))
                    .map { app.repository.labelB(Label.Type.Genres, it) }
            }),
            Pair(book::subtitle, work.optString("subtitle")),
            Pair(book::imgUrl, coverUrl(work)),
        )
        if (book.authors.isEmpty()) {
            doAuthors(tag, book, work, onCompletion)
        } else {
            onCompletion()
        }
    }

    private val workProperties = listOf(
        Book::summary,
        Book::genres,
        Book::subtitle,
        Book::imgUrl,
        Book::authors,
    )

    private fun doWorkAndAuthors(
        tag: String,
        book: Book,
        obj:JSONObject,
        onCompletion: () -> Unit
    ) {
        val key = firstKeyOrNull(obj, "works")
        if (key == null || hasAllProperties(book, workProperties)) {
            Log.d(TAG, "$tag no work. done.")
            onCompletion()
        } else {
            val url = "https://openlibrary.org$key.json"
            request(tag, url)
                .onResponse { response ->
                    val work: JSONObject?
                    if (response.isSuccessful) {
                        work = parse(response)
                        if (work != null) {
                            setupWorkAndAuthors(tag, book, work, onCompletion)
                            return@onResponse
                        }
                    }
                    Log.e(TAG, "$url: http status ${response.code} parsing failed.")
                    onCompletion()
                }
                .onError {
                    Log.e(TAG, "$url: http request failed.", it)
                    onCompletion()
                }
                .run()
        }
    }


    private fun convert(
        tag: String,
        book: Book,
        obj: JSONObject,
        onCompletion: () -> Unit,
    ) {
        // Copies all the pass through fields.
        setIfEmpty(
            Pair(book::title, obj.optString("title")),
            Pair(book::subtitle, obj.optString("subtitle")),
            Pair(book::numberOfPages, obj.optString("number_of_pages")),
            Pair(book::isbn, firstOrEmpty(obj, "isbn_13")),
            Pair(book::language, app.repository.labelOrNullB(Label.Type.Language, language(obj))),
            Pair(book::publisher, app.repository.labelOrNullB(
                Label.Type.Publisher,
                arrayToList<String>(obj.optJSONArray("publishers")).joinToString())
            ),
            Pair(book::imgUrl, coverUrl(obj)),
            Pair(book::yearPublished, publishDate(obj.optString("publish_date", ""))),
        )
        // Continues our journey to fetch additional infos about the work, when available:
        doWorkAndAuthors(tag, book, obj, onCompletion)
    }

    private val allProperties = listOf(
        Book::title,
        Book::subtitle,
        Book::numberOfPages,
        Book::isbn,
        Book::language,
        Book::publisher,
        Book::imgUrl,
        Book::yearPublished,
    ) + workProperties

    override fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit,
    ) {
        if (hasAllProperties(book, allProperties) || ! ISBN.isValidEAN13(book.isbn)) {
            onCompletion()
            return
        }
        val url = "$basedir/isbn/${book.isbn}.json"
        request(tag, url)
            .onResponse { response ->
                if (response.isSuccessful) {
                    val obj = parse(response)
                    if (obj != null) {
                        convert(tag, book, obj, onCompletion)
                        return@onResponse
                    }
                }
                Log.e(TAG, "$url http status ${response.code} parsing failed.")
                onCompletion()
            }
            .onError {
                Log.e(TAG, "$url: http request failed.", it)
                onCompletion()
            }
            .run()
    }
}
