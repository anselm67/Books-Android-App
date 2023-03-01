package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.util.*


// https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/LookupExamples.html
class iTuneClient: JsonClient() {
    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private val markUpRE = Regex("<[^>]*>")
    private fun removeMarkUp(src: String): String {
        return markUpRE.replace(src, "")
    }

    private fun convert(book: Book, resp: JSONObject) {
        val itemCount = resp.getInt("resultCount")
        if (itemCount <= 0) {
            return
        }
        // Converts the match and let the callback know about the outcome.
        val item = resp.getJSONArray("results").get(0) as JSONObject
        setIfEmpty(
            Pair(book::title, item.optString("trackName")),
            Pair(book::authors, stringToList(item.optString("artistName")).map {
                app.repository.labelB(Label.Type.Authors, it)
            }),
            Pair(book::genres,  if ( app.bookPrefs.useOnlyExistingGenres) {
                arrayToList<String>(item.optJSONArray("genres"))
                    .mapNotNull { app.repository.labelIfExistsB(Label.Type.Genres, it) }
            } else {
                arrayToList<String>(item.optJSONArray("genres"))
                    .map { app.repository.labelB(Label.Type.Genres, it) }
            }),
            Pair(book::imgUrl, item.optString("artworkUrl100")),
            Pair(book::yearPublished, dateFormatter.parse(item.optString("releaseDate"))),
            Pair(book::summary, removeMarkUp(item.optString("description")))
        )
    }

    private val properties = listOf(
        Book::title,
        Book::authors,
        Book::genres,
        Book::imgUrl,
        Book::yearPublished,
        Book::summary,
    )

    override fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit,
    ) {
        if (hasAllProperties(book, properties) || ! ISBN.isValidEAN13(book.isbn)) {
            onCompletion()
            return
        }
        val url = "https://itunes.apple.com/lookup?isbn=${book.isbn}"
        request(tag, url)
            .onResponse{ response ->
                parse(response)?.let { convert(book, it) }
                onCompletion()
            }
            .onError {
                Log.e(TAG, "$url: http request failed.", it)
                onCompletion()
            }
            .run()
    }
}