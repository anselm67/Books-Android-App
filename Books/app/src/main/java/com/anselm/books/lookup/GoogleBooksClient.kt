package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.json.JSONObject
import java.net.URLEncoder

// https://developers.google.com/books/docs/v1/using
class GoogleBooksClient: JsonClient() {
    val repository by lazy { app.repository }

    private fun extractIsbn(obj: JSONObject): String {
        val ids = obj.optJSONArray("industryIdentifiers")
        return if (ids == null) {
            ""
        } else {
            arrayToList<JSONObject>(ids).firstOrNull {
                it.optString("type") == "ISBN_13"
            }?.optString("identifier") ?: ""
        }
    }

    private val languages = mapOf(
        "fr" to "French",
        "en" to "English",
        "es" to "Spanish",
        "it" to "Italian",
    )

    private fun getLanguage(str: String): Label? {
        return app.repository.labelOrNullB(Label.Type.Language, languages.getOrDefault(str, str))
    }

    private fun getNumberOfPages(num: Int): String {
        return if (num == 0) "" else num.toString()
    }

    private fun convertBook(book: Book, obj: JSONObject) {
        val volumeInfo = obj.optJSONObject("volumeInfo")
        check(volumeInfo != null) { "GoogleBook response has no volumeInfo." }
        setIfEmpty(
            Pair(book::isbn, extractIsbn(volumeInfo)),
            Pair(book::title, volumeInfo.optString("title")),
            Pair(book::subtitle, volumeInfo.optString("subtitle")),
            Pair(book::yearPublished, publishDate(volumeInfo.optString("publishedDate"))),
            Pair(book::summary, volumeInfo.optString("description")),
            Pair(book::imgUrl, volumeInfo.optJSONObject("imageLinks")?.optString("thumbnail") ?: ""),
            Pair(book::language, getLanguage(volumeInfo.optString("language"))),
            Pair(book::numberOfPages, getNumberOfPages(volumeInfo.optInt("pageCount", 0))),
            Pair(book::publisher, app.repository.labelOrNullB(
                Label.Type.Publisher, volumeInfo.optString("publisher"))),

            // Label-fields:
            Pair(book::authors, arrayToList<String>(volumeInfo.optJSONArray("authors"))
                .map { repository.labelB(Label.Type.Authors, it) }),
            if ( app.bookPrefs.useOnlyExistingGenres) {
                Pair(book::genres, arrayToList<String>(volumeInfo.optJSONArray("categories"))
                    .mapNotNull { repository.labelIfExistsB(Label.Type.Genres, it) })
            } else {
                Pair(book::genres, arrayToList<String>(volumeInfo.optJSONArray("categories"))
                    .map { repository.labelB(Label.Type.Genres, it) })
            }
        )
    }

    private fun convertResponse(
        book: Book,
        resp: JSONObject,
    ) {
        // Checks the kind, verifies we got some matches.
        check(resp.optString("kind") == "books#volumes")
        val itemCount = resp.getInt("totalItems")
        if (itemCount <= 0) {
            return
        }
        // Converts the first match and let the callback know about the outcome.
        val items = resp.getJSONArray("items")
        try {
            convertBook(book, items.getJSONObject(0))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON", e)
        }
    }

    private val properties = listOf(
        Book::isbn,
        Book::title,
        Book::subtitle,
        Book::yearPublished,
        Book::summary,
        Book::imgUrl,
        Book::language,
        Book::publisher,
        Book::authors,
        Book::genres,
    )

    override fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit
    ) {
        if (hasAllProperties(book, properties)) {
            onCompletion()
            return
        }
        val url = if ( ISBN.isValidEAN13(book.isbn) ) {
            "https://www.googleapis.com/books/v1/volumes?q=isbn:${book.isbn}"
        } else if (book.title.isNotEmpty() && book.authors.isNotEmpty()){
            @Suppress("DEPRECATION") // Min is 31, and it doesn't have the right version.
            val title = URLEncoder.encode(book.title /* , Charsets.UTF_8 */)
            @Suppress("DEPRECATION")
            val author = URLEncoder.encode(book.authors[0].name /* , Charsets.UTF_8 */)
            "https://www.googleapis.com/books/v1/volumes?q=intitle:$title+inauthor:${author}"
        } else {
            onCompletion()
            return
        }
        request(tag, url)
            .onResponse { response ->
                parse(response)?.let { convertResponse(book, it) }
                onCompletion()
            }
            .onError {
                Log.e(TAG, "$url: http request failed.", it)
                onCompletion()
            }
            .run()
    }
}