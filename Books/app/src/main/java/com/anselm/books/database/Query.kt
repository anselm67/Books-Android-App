package com.anselm.books.database

import android.os.Parcelable
import com.anselm.books.BooksApplication.Companion.app
import kotlinx.parcelize.Parcelize

@Parcelize
data class Query(
    var type: Type = Type.Regular,
    var query: String? = null,
    var partial: Boolean = false,
    var filters: MutableList<Filter> = mutableListOf(),
    var withoutLabelOfType: Label.Type = Label.Type.NoType,
    var sortBy: Int = app.defaultSortOrder,
) : Parcelable {

    @Parcelize
    data class Filter(
        val type: Label.Type,
        val labelId: Long,
    ): Parcelable {
        constructor(label: Label) : this(label.type, label.id)
    }

    fun clearFilter(type: Label.Type) {
        this.filters = filters.filter { it.type != type }.toMutableList()
    }

    fun firstFilter(type: Label.Type): Filter? {
        return filters.firstOrNull { it.type == type }
    }

    fun setOrReplace(filter: Filter) {
        val index = filters.indexOfFirst { it.type == filter.type }
        if (index >= 0) {
            filters.removeAt(index)
        }
        filters.add(filter)
    }

    enum class Type {
        Regular,            // Full featured, using all the Query fields,
        Duplicates,         // Fake, get all duplicates.
        NoCover,            // Fake, get books without a cover.
    }

    companion object {
        fun asFilter(label: Label?): MutableList<Filter> {
            return if (label == null) arrayListOf() else arrayListOf(Filter(label))
        }
        val emptyQuery = Query()
    }
}
