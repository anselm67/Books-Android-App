package com.anselm.books.lookup

import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.database.Label

abstract class XmlClient: SimpleClient() {

    private val languages = mapOf(
        "fre" to "French",
        "eng" to "English",
        "ita" to "Italian",
        "spa" to "Spanish",
    )

    protected fun getLanguage(code: String): Label {
        return app.repository.labelB(
            Label.Type.Language,
            languages.getOrDefault(code, code)
        )
    }

    private val yearRE =
        Regex(".*(^|\\s+|\\p{P}+|\\p{S}+)([0-9]{4})($|\\s+).*")
    protected fun extractYear(s: String): String {
        val matchResult = yearRE.matchEntire(s)
        return matchResult?.groups?.get(2)?.value ?: ""
    }

    private val numberOfPagesRE =
        Regex("^.*[\\s(\\[]+([0-9]+)[\\s)\\]]+p.*$", RegexOption.IGNORE_CASE)
    protected fun extractNumberOfPages(format: String): String {
        val matchResult = numberOfPagesRE.matchEntire(format)
        return matchResult?.groups?.get(1)?.value ?: ""
    }



}