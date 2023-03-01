package com.anselm.books.lookup

import android.util.Log
import android.util.Xml
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.ISBN
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParser.TEXT
import org.xmlpull.v1.XmlPullParserException

class OclcClient: XmlClient() {
    private var parser: XmlPullParser = Xml.newPullParser()

    private fun until(name: String, handle: (String) -> Unit) {
        while (parser.next() != END_TAG) {
            if (parser.eventType == START_TAG) {
                if (parser.name == name) {
                    if (parser.next() != TEXT) {
                        throw XmlPullParserException("Expected TEXT.")
                    } else {
                        handle(parser.text)
                        check(parser.next() == END_TAG)
                        return
                    }
                } else {
                    parser.next() // TEXT
                    parser.next() // END_TAG
                }
            }
        }
    }

    private fun expect(expected: Int) {
        val got = parser.next()
        if (got == expected) {
            return
        } else {
            throw XmlPullParserException("Expected $expected, got $got.")
        }
    }

    private fun parseXml(
        book: Book,
        text: String,
    ) {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(text.reader())
        parser.nextTag()
        when (parser.name) {
            "oclcdcs" -> {
                expect(TEXT)
                val authors = emptyList<Label>().toMutableList()
                while (parser.next() == START_TAG) {
                    val name = parser.name
                    expect(TEXT)
                    val value = parser.text
                    expect(END_TAG)
                    when(name) {
                        "dc:creator", "dc:contributor" -> {
                            authors.add(app.repository.labelB(Label.Type.Authors, value))
                        }
                        "dc:title" -> {
                            setIfEmpty(book::title, value)
                        }
                        "dc:description" -> {
                            // We might get multiple of these, we just get the first one.
                            setIfEmpty(book::summary, value)
                        }
                        "dc:language" -> {
                            setIfEmpty(book::language, getLanguage(value))
                        }
                        "dc:format" -> {
                            setIfEmpty(book::numberOfPages, extractNumberOfPages(value))
                        }
                        "dc:date" -> {
                            setIfEmpty(book::yearPublished, extractYear(value))
                        }
                        "dc:publisher" -> {
                            setIfEmpty(book::publisher, app.repository.labelB(Label.Type.Publisher, value))
                        }
                        "dc:identifier" -> {
                            if (ISBN.isValidEAN13(value)) {
                                setIfEmpty(book::isbn, value)
                            }
                        }
                        else -> {
                            Log.d(TAG, "Unhandled tag: $name")
                        }
                    }
                    expect(TEXT)
                }
                setIfEmpty(book::authors, authors)
            }
            "diagnostics" -> {
                while (parser.next() != START_TAG) { /* Intended empty */ }
                check(parser.name == "diagnostic")
                until("message") {
                    Log.e(TAG, "Request failed, diagnostic: $it")
                }
            }
            else -> {
                Log.e(TAG, "XML parser git unexpected tag ${parser.name}")
            }
        }
    }

    private val properties = listOf(
        Book::authors,
        Book::title,
        Book::summary,
        Book::language,
        Book::numberOfPages,
        Book::yearPublished,
        Book::publisher,
        Book::isbn
    )

    override fun lookup(
        tag: String,
        book: Book,
        onCompletion: () -> Unit,
    ) {
        val wskey = app.bookPrefs.wskey
        if (wskey.isEmpty()) {
            onCompletion()
            return
        }
        if (hasAllProperties(book, properties) || ! ISBN.isValidEAN13(book.isbn)) {
            onCompletion()
            return
        }
        val url = "https://www.worldcat.org/webservices/catalog/content/isbn/${book.isbn}?wskey=$wskey&maximumRecords=1&recordSchema=info:srw/schema/1/dc"
        request(tag, url)
            .onResponse {
                if (it.isSuccessful) {
                    parseXml(book, it.body!!.string())
                } else {
                    Log.e(TAG, "$url: http request returned status ${it.code}.")
                }
                onCompletion()
            }
            .onError {
                Log.e(TAG, "$url: http request failed.", it)
                onCompletion()
            }
            .run()
    }
}


