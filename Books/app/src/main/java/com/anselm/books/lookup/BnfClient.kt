package com.anselm.books.lookup

import android.util.Log
import android.util.Xml
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import okhttp3.internal.headersContentLength
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParser.TEXT

// https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=bib.isbn%20adj%20%229782072875519%22&recordSchema=unimarcXchange&maximumRecords=100&startRecord=1
// http://www.loc.gov/standards/sru/recordSchemas/index.html
// https://www.ifla.org/g/unimarc-rg/unimarc-bibliographic-3rd-edition-with-updates/
class BnfClient: XmlClient() {
    private var parser: XmlPullParser = Xml.newPullParser()

    private fun untilChild(name: String): String? {
        var level = 0
        parser.next()
        while ( true ) {
            when (parser.eventType) {
                START_TAG -> {
                    level++
                    if (level == 1 && parser.name == name) {
                        check(parser.next() == TEXT) {
                            "Expected a TEXT got ${parser.eventType} instead."
                        }
                        return parser.text
                    }
                    parser.next()
                }
                END_TAG -> {
                    if (level == 0) {
                        return null
                    }
                    --level
                    parser.next()
                }
                END_DOCUMENT -> {
                    return null
                }
                else -> {
                    parser.next()
                }
            }
        }
    }

    private fun until(name: String, namespace: String?): Boolean {
        parser.next()
        while (parser.eventType != END_DOCUMENT) {
            if (parser.eventType == START_TAG
                && parser.name == name
                && (namespace == null || parser.namespace == namespace)) {
                return true
            }
            parser.next()
        }
        return false
    }

    // Closes [count] tag and skip until the next START_TAG.
    private fun closeTag(howMany: Int): Boolean {
        var count = howMany
        while (count > 0) {
            parser.next()
            if (parser.eventType == END_DOCUMENT) {
                return false
            } else if (parser.eventType == END_TAG) {
                count--
            }
        }
        // Skip until we hit the next START_TAG:
        while (parser.eventType != START_TAG && parser.eventType != END_DOCUMENT) {
            parser.next()
        }
        return parser.eventType == START_TAG
    }

    private fun getAttributeValue(name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun forEachTag(
        name: String,
        tagCallback: () -> Unit,
        textCallback: () -> Unit,
    ) {
        parser.next()
        var level = 0
        var enableCallback = false
        while ( true ) {
            when (parser.eventType) {
                START_TAG -> {
                    ++level
                    if (level == 1 && parser.name == name) {
                        tagCallback()
                        enableCallback = true
                    }
                    if (enableCallback) {
                        tagCallback()
                    }
                    parser.next()
                    if (enableCallback && parser.eventType == TEXT) {
                        textCallback()
                    }
                }
                END_TAG -> {
                    --level
                    if (level == 0) {
                        enableCallback = false
                    }
                    parser.next()
                }
                END_DOCUMENT -> { return }
                else -> {
                    parser.next()
                }
            }
        }

    }

    private val setters = mapOf(
        "101.a" to { book: Book, value: String ->
            setIfEmpty(book::language, getLanguage(value))
        },
        "073.a" to { book: Book, value: String ->
            setIfEmpty(book::isbn, value)
        },
        "200.a" to { book: Book, value: String ->
            setIfEmpty(book::title, value)
        },
        "200.f" to { book: Book, value: String
            -> setIfEmpty(book::authors, listOf(app.repository.labelB(Label.Type.Authors, value)))
        },
        // Two ways to get to publisher, e.g. ISBNs 9782757206492 and 9782072987885
        "210.c" to {  book: Book, value: String ->
            setIfEmpty(book::publisher, app.repository.labelB(Label.Type.Publisher, value))
        },
        "214.c" to {  book: Book, value: String ->
            setIfEmpty(book::publisher, app.repository.labelB(Label.Type.Publisher, value))
        },
        "214.d" to { book: Book, value: String ->
            setIfEmpty(book::yearPublished, extractYear(value))
        },
        "215.a" to { book: Book, value: String ->
            setIfEmpty(book::numberOfPages, extractNumberOfPages(value))
        },
        "330.a" to { book: Book, value: String ->
            setIfEmpty(book::summary, value)
        },
        "606.x" to { book: Book, value: String ->
            if (app.bookPrefs.useOnlyExistingGenres) {
                val label = app.repository.labelIfExistsB(Label.Type.Genres, value)
                label?.let { setIfEmpty(book::genres, listOf(it)) }
            } else {
                setIfEmpty(book::genres, listOf(app.repository.labelB(Label.Type.Genres, value)))
            }
        }
    )

    private fun parseXml(
        tag: String,
        book: Book,
        text: String,
        onCompletion: () -> Unit,
    ) {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(text.reader())
        parser.nextTag()

        // Checks the root tag and the number of matches.
        if (parser.name != "searchRetrieveResponse") {
            Log.e(TAG, "Expected a srw:searchRetrieveResponse tag.")
            onCompletion()
            return
        }
        val numberOfRecords = untilChild("numberOfRecords")?.toIntOrNull()
        if (numberOfRecords == null || numberOfRecords < 1 ) {
            // No match found.
            onCompletion()
            return
        }
        closeTag(1)
        check(parser.eventType == START_TAG && parser.name == "records") {
            "Expected START_TAG got ${parser.eventType} instead."
        }

        // Move until the actual data of interest.
        until("record", namespace="info:lc/xmlns/marcxchange-v2")
        val arkId = getAttributeValue("id")
        check(arkId != null) { "No arkId attribute on returned record." }
        check(getAttributeValue("format") == "UNIMARC") {
            "UNIMARC format supported, got ${getAttributeValue("format")}"
        }

        // Parses each field into the corresponding book property.
        var tagName: String? = null
        var code: String? = null
        forEachTag(
            "datafield", {
                if (parser.name == "datafield") {
                    tagName = getAttributeValue("tag")
                } else if (parser.name == "subfield") {
                    code = getAttributeValue("code")
                }
            },
            {
                Log.d(TAG, "$tagName.$code: ${parser.text}")
                val setter = setters.getOrDefault("$tagName.$code", null)
                if (setter != null) {
                    setter(book, parser.text)
                }
                code = null
            }
        )

        // Gets the image when needed.
        // Makes sure to always call onCompletion.
        if (book.imgUrl.isEmpty()) {
            val imgUrl = "https://catalogue.bnf.fr/couverture?&appName=NE&idArk=$arkId&couverture=1"
            // We have to HEAD this because they always provide a default image even if none found.
            request(tag, imgUrl, useHead = true)
                .onResponse {
                    if (it.isSuccessful && it.headersContentLength() != 4658L) {
                        book.imgUrl = imgUrl
                    }
                    onCompletion()
                }
                .onError {
                    Log.e(TAG, "$imgUrl for image failed (no image from BNF).", it)
                    onCompletion()
                }
                .run()
        } else {
            onCompletion()
        }
    }

    override fun lookup(tag: String, book: Book, onCompletion: () -> Unit) {
        if (! ISBN.isValidEAN13(book.isbn) ) {
            onCompletion()
            return
        }
        val url = "https://catalogue.bnf.fr/api/SRU?version=1.2&operation=searchRetrieve&query=bib.isbn%20adj%20%22${book.isbn}%22&recordSchema=unimarcXchange&maximumRecords=100&startRecord=1"
        request(tag, url)
            .onResponse {
                if (it.isSuccessful) {
                    // It's up to parseXml to call onCompletion: it can make request to check
                    // the cover image url.
                    parseXml(tag, book, it.body!!.string(), onCompletion)
                } else {
                    Log.e(TAG, "$url: http request returned status ${it.code}.")
                    onCompletion()
                }
            }
            .onError {
                Log.e(TAG, "$url http request failed.")
                onCompletion()
            }
            .run()
    }
}