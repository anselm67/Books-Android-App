package com.anselm.books

import com.anselm.books.database.Book
import com.anselm.books.database.Label
import kotlin.reflect.KMutableProperty0

class BookMerger {

    /**
     * Merges the [other] book into [into] and returns [into]
     */
    fun merge(into: Book, other: Book): Book {
        // Handles fields that [into] doesn't have a value for, but [other] has:
        listOf<Pair<KMutableProperty0<String>, () -> String>>(
            Pair(into::title, other::title.getter),
            Pair(into::subtitle, other::subtitle.getter),
            Pair(into::imgUrl, other::imgUrl.getter),
            Pair(into::isbn, other::isbn.getter),
            Pair(into::summary, other::summary.getter),
            Pair(into::yearPublished, other::yearPublished.getter),
            Pair(into::numberOfPages, other::numberOfPages.getter),
            Pair(into::imageFilename, other::imageFilename.getter),
            ).map {
            val intoValue = it.first.getter()
            if (intoValue.isEmpty()) {
                val otherValue = it.second()
                if ( otherValue.isNotEmpty() ) {
                    it.first.setter(otherValue)
                }
            }
        }
        if (into.title.isEmpty() && other.title.isNotEmpty()) {
            into.title = other.title
        }

        // Handles single label fields by keeping the one that has a value if any
        listOf<Pair<KMutableProperty0<Label?>, () -> Label?>>(
            Pair(into::publisher, other::publisher.getter),
            Pair(into::location, other::location.getter),
            Pair(into::language, other::language.getter),
        ).map {
            if (it.first() == null) {
                val label = it.second()
                if (label != null) {
                    it.first.setter(label)
                }
            }
        }

        // Handles multi label fields by adding the values.
        listOf<Pair<KMutableProperty0<List<Label>>, () -> List<Label>>>(
            Pair(into::genres, other::genres.getter),
            Pair(into::authors, other::authors.getter),
        ).map {
            val otherLabels = it.second()
            if (otherLabels.isNotEmpty()) {
                val intoValues = it.first().toMutableList()
                otherLabels.map { label ->
                    if ( ! intoValues.contains(label) ) {
                        intoValues.add(label)
                    }
                }
            }
        }
        return into
    }
}