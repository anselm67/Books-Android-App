package com.anselm.books

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Book
import com.anselm.books.lookup.AmazonImageClient
import com.anselm.books.lookup.BnfClient
import com.anselm.books.lookup.GoogleBooksClient
import com.anselm.books.lookup.OclcClient
import com.anselm.books.lookup.OpenLibraryClient
import com.anselm.books.lookup.SimpleClient
import com.anselm.books.lookup.iTuneClient
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KProperty0

private data class LookupServiceClient(
    val preferenceKey: String,
    val preferenceGetter: KProperty0.Getter<Boolean>,
    val client: SimpleClient,
) {
    var lookupCount = 0
    var matchCount = 0
    var coverCount = 0
}

class LookupService {
    private val statsFile = File(app.applicationContext?.filesDir, "lookup_stats.json")
    private val prefs by lazy {
        app.bookPrefs
    }
    private val clients = with(BooksPreferences) {
        listOf(
            LookupServiceClient(USE_GOOGLE, prefs::useGoogle.getter, GoogleBooksClient()),
            LookupServiceClient(USE_BNF, prefs::useBNF.getter, BnfClient()),
            LookupServiceClient(USE_WORLDCAT, prefs::useWorldcat.getter, OclcClient()),
            LookupServiceClient(USE_ITUNES, prefs::useiTunes.getter, iTuneClient()),
            LookupServiceClient(USE_AMAZON, prefs::useAmazon.getter, AmazonImageClient()),
            LookupServiceClient(
                USE_OPEN_LIBRARY,
                prefs::useOpenLibrary.getter,
                OpenLibraryClient()
            ),
        )
    }

    init {
        loadStats()
    }

    fun clientKeys(handler: (String) -> Unit) {
        clients.forEach { handler(it.preferenceKey) }
    }

    fun stats(key: String): Triple<Int, Int, Int> {
        val client = clients.firstOrNull { it.preferenceKey == key }
        return Triple(client?.lookupCount ?: 0, client?.matchCount ?: 0, client?.coverCount ?: 0)
    }

    fun resetStats() {
        clients.forEach {
            it.lookupCount = 0
            it.matchCount = 0
            it.coverCount = 0
        }
        saveStats()
    }

    fun saveStats() {
        try {
            val obj = JSONObject()
            clients.forEach {
                obj.put("${it.preferenceKey}.lookup", it.lookupCount)
                obj.put("${it.preferenceKey}.match", it.matchCount)
                obj.put("${it.preferenceKey}.cover", it.coverCount)
            }
            statsFile.outputStream().use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use {
                    it.write(obj.toString(2))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save lookup statistics from ${statsFile.path} (ignored)", e)
        }
    }

    private fun loadStats() {
        try {
            var obj: JSONObject
            statsFile.inputStream().use { inputStream ->
                InputStreamReader(inputStream).use {
                    obj = JSONTokener(it.readText()).nextValue() as JSONObject
                }
            }
            clients.forEach {
                it.lookupCount = obj.getInt("${it.preferenceKey}.lookup")
                it.matchCount = obj.getInt("${it.preferenceKey}.match")
                it.coverCount = obj.getInt("${it.preferenceKey}.cover")
            }
        } catch (e: Exception) {
            // By deleting the file we make sure it'll get recreated proprly next time around.
            Log.e(TAG, "Failed to load lookup statistics from ${statsFile.path} (ignored)", e)
            statsFile.delete()
        }
    }

    private val requestIdCounter = AtomicInteger(1)
    private fun nextTag(): String {
        return "lookup-${requestIdCounter.incrementAndGet()}"
    }

    private fun stopNow(
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        book: Book,
    ): Boolean {
        return if (stopAt == null) {
            false
        } else {
            stopAt.firstOrNull {
                Property.isEmpty(it(book))
            } == null
        }
    }

    private fun onCompletion(
        index: Int,
        tag: String,
        book: Book,
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        onDone: (Book?) -> Unit): Boolean {
        for (i in index until clients.size) {
            val service = clients[i]
            if (stopNow(stopAt, book)) {
                break
            }
            if (service.preferenceGetter()) {
                val missTitleAndAuthor = book.title.isEmpty() || book.authors.isEmpty()
                val missImgUrl = book.imgUrl.isEmpty()
                service.lookupCount++
                service.client.lookup(tag, book) {
                    if (missTitleAndAuthor && book.title.isNotEmpty() && book.authors.isNotEmpty()) {
                        service.matchCount++
                    }
                    if (missImgUrl && book.imgUrl.isNotEmpty()) {
                        service.coverCount++
                    }
                    onCompletion(i + 1, tag, book, stopAt, onDone)
                }
                return false
            }
        }
        onDone(if (book.title.isNotEmpty()) book else null)
        return true
    }

    private val defaultStopAt = listOf(
        Book::title.getter,
        Book::authors.getter,
        Book::imgUrl.getter,
    )

    /**
     * Lookup the given ISBN through our enabled lookup services.
     * Lookup services errors are logged and simply considered no match, we only take in a
     * match callback.
     * Returns an okHttp tag that can be given to cancelHttpRequests to cancel all pending lookups
     * for the given ISBN; Or null if no requests were made and the lookup has finished.
     * When [stopAt] is not null, we end the lookup once all provided getters return non
     * empty values.
     */
    fun lookup(
        like: Book,
        stopAt: List<(Book) -> Any?>? = defaultStopAt,
        onDone: (book: Book?) -> Unit,
    ): String? {
        val tag = nextTag()
        return if ( onCompletion(0, tag, like, stopAt, onDone) ) null else tag
    }
}