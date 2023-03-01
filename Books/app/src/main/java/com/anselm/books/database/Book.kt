package com.anselm.books.database

import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.text.SimpleDateFormat
import java.util.*

object BookFields {
    const val UID = "uid"
    const val TITLE = "title"
    const val SUBTITLE = "subtitle"
    const val AUTHOR = "author"
    const val PUBLISHER = "publisher"
    const val UPLOADED_IMAGE_URL = "uploaded_image_url"
    const val PHYSICAL_LOCATION = "physical_location"
    const val ISBN = "isbn"
    const val SUMMARY = "summary"
    const val YEAR_PUBLISHED = "year_published"
    const val NUMBER_OF_PAGES = "number_of_pages"
    const val GENRE = "genre"
    const val LANGUAGE = "language"
    const val DATE_ADDED = "date_added"
    const val IMAGE_FILENAME = "image_filename"
    const val LAST_MODIFIED = "last_modified"
    const val MIN_PUBLISHED_YEAR = 0
    const val MAX_PUBLISHED_YEAR = 2100
}

private val DATE_FORMAT = SimpleDateFormat("EEE, MMM d yyy", Locale.US)

@Entity(
    tableName = "book_table",
    indices = [
        Index(value = ["title", "subtitle"] ),
        Index(value = ["date_added"]),
        Index(value = ["uid"]),
    ]
)
data class Book(@PrimaryKey(autoGenerate=true) val id: Long = 0): Parcelable {
    @ColumnInfo(name = "uid")
    var uid = ""

    @ColumnInfo(name = "title")
    var title = ""

    @ColumnInfo(name = "subtitle")
    var subtitle = ""

    @ColumnInfo(name = "imgUrl")
    var imgUrl = ""

    @ColumnInfo(name = "ISBN")
    var isbn = ""

    @ColumnInfo(name = "summary")
    var summary = ""

    @ColumnInfo(name = "yearPublished")
    var yearPublished = ""

    @ColumnInfo(name = "numberOfPages")
    var numberOfPages = ""

    // Handles date added conversion type:
    // It's imported from json as a String encoded number of seconds. That's also how it
    // is stored in the database. The private _dateAdded stores the db value, the public
    // dateAdded returns it properly formatted.
    @ColumnInfo(name = "date_added")
    var rawDateAdded = 0L

    val dateAdded: String
        get() = if (rawDateAdded == 0L) ""
                else DATE_FORMAT.format(Date(rawDateAdded * 1000))

    @ColumnInfo(name = "last_modified")
    var rawLastModified = 0L

    val lastModified: String
        get() = if (rawLastModified == 0L) ""
        else DATE_FORMAT.format(Date(rawLastModified * 1000))

    @ColumnInfo(name = "image_filename")
    var imageFilename = ""

    constructor(parcel: Parcel) : this(parcel.readLong()) {
        val obj: JSONObject = JSONTokener(parcel.readString()).nextValue() as JSONObject
        fromJson(obj)
    }

    constructor(o: JSONObject) : this() {
        fromJson(o)
    }

    private fun arrayToLabels(type: Label.Type, obj: JSONObject, key: String) {
        val values = obj.optJSONArray(key)
        if (values != null) {
            for (i in 0 until values.length()) {
                addLabel(type, values.optString(i))
            }
        }
    }

    private fun stringToLabel(type: Label.Type, obj: JSONObject, key: String) {
        val text = obj.optString(key, "")
        if (text != "") {
            addLabel(type, text)
        }
    }

    private fun fromJson(obj: JSONObject) {
        this.uid = obj.optString(BookFields.UID, "")
        this.title = obj.optString(BookFields.TITLE, "")
        this.subtitle = obj.optString(BookFields.SUBTITLE, "")
        this.imgUrl = obj.optString(BookFields.UPLOADED_IMAGE_URL, "")
        this.isbn = obj.optString(BookFields.ISBN, "")
        this.summary = obj.optString(BookFields.SUMMARY, "")
        this.yearPublished = obj.optString(BookFields.YEAR_PUBLISHED, "")
        this.numberOfPages = obj.optString(BookFields.NUMBER_OF_PAGES, "")
        this.rawDateAdded = obj.optLong(BookFields.DATE_ADDED, 0)
        this.imageFilename = obj.optString(BookFields.IMAGE_FILENAME, "")
        this.rawLastModified = obj.optLong(BookFields.LAST_MODIFIED, 0)
        // Handles label fields:
        arrayToLabels(Label.Type.Authors, obj, BookFields.AUTHOR)
        arrayToLabels(Label.Type.Genres, obj, BookFields.GENRE)
        stringToLabel(Label.Type.Location, obj, BookFields.PHYSICAL_LOCATION)
        stringToLabel(Label.Type.Publisher, obj, BookFields.PUBLISHER)
        stringToLabel(Label.Type.Language, obj, BookFields.LANGUAGE)

    }

    private fun toJsonArray(labels: List<Label>): JSONArray {
        val array = JSONArray()
        labels.forEach { array.put(it.name) }
        return array
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put(BookFields.UID, uid)
        obj.put(BookFields.TITLE, title)
        obj.put(BookFields.SUBTITLE, subtitle)
        obj.put(BookFields.UPLOADED_IMAGE_URL, imgUrl)
        obj.put(BookFields.ISBN, isbn)
        obj.put(BookFields.SUMMARY, summary)
        obj.put(BookFields.YEAR_PUBLISHED, yearPublished)
        obj.put(BookFields.NUMBER_OF_PAGES, numberOfPages)
        obj.put(BookFields.DATE_ADDED, rawDateAdded)
        obj.put(BookFields.IMAGE_FILENAME, imageFilename)
        obj.put(BookFields.LAST_MODIFIED, rawLastModified)
        // Handles label fields.
        obj.put(BookFields.AUTHOR, toJsonArray(authors))
        obj.put(BookFields.GENRE, toJsonArray(genres))
        obj.put(BookFields.PHYSICAL_LOCATION, location?.name ?: "")
        obj.put(BookFields.PUBLISHER, publisher?.name ?: "")
        obj.put(BookFields.LANGUAGE, language?.name ?: "")
        return obj
    }

    // Parcelable.
    override fun describeContents(): Int {
        return 0
    }

    // Parcelable
    override fun writeToParcel(dest: Parcel, flags: Int) {
        val jsonString = toJson().toString()
        dest.writeString(jsonString)
    }

    companion object CREATOR : Parcelable.Creator<Book> {
        override fun createFromParcel(parcel: Parcel): Book {
            return Book(parcel)
        }

        override fun newArray(size: Int): Array<Book?> {
            return arrayOfNulls(size)
        }
    }

    /**
     * Handling labels for this book.
     */
    @Ignore
    var labels: MutableList<Label>? = if (id == 0L) mutableListOf() else null
        private set
    @Ignore
    private var decorated = (id == 0L)
    @Ignore
    var labelsChanged = false
        private set

    fun decorate(databaseLabels: List<Label>): List<Label> {
        synchronized(this) {
            assert(this.labels == null)
            if ( ! decorated ) {
                this.labels = mutableListOf()
                this.labels!!.addAll(databaseLabels)
                decorated = true
            }
        }
        return this.labels!!
    }

    private fun addLabel(type: Label.Type, name: String) = addLabel(Label(type, name))

    private fun addLabel(label: Label) {
        check(decorated)
        labels!!.add(label)
        labelsChanged = true
    }

    private fun addLabels(labels: List<Label>) {
        check(decorated)
        this.labels!!.addAll(labels)
        labelsChanged = true
    }

    private fun clearLabels(type: Label.Type) {
        check(decorated)
        labels = labels?.filter {
            labelsChanged = labelsChanged || (it.type == type)
            it.type != type
        }?.toMutableList()
    }

    fun getLabels(type: Label.Type): List<Label> {
        check(decorated)
        return labels!!.filter { it.type == type }
    }

    fun firstLabel(type: Label.Type): Label? {
        check(decorated)
        return labels!!.firstOrNull { it.type == type }
    }

    private fun setOrReplaceLabel(type: Label.Type, tag: Label?) {
        check(decorated)
        val index = labels!!.indexOfFirst { it.type == type }
        if (index >= 0) {
            labels!!.removeAt(index)
            labelsChanged = true
        }
        if (tag != null) {
            labels!!.add(tag)
            labelsChanged = true
        }
    }

    private fun setOrReplaceLabels(type: Label.Type, labels: List<Label>?) {
        clearLabels(type)
        if (labels != null) {
            addLabels(labels)
        }
    }

    var publisher: Label?
        get() = firstLabel(Label.Type.Publisher)
        set(value) {
            setOrReplaceLabel(Label.Type.Publisher, value)
        }

    var language: Label?
        get() = firstLabel(Label.Type.Language)
        set(value) {
            setOrReplaceLabel(Label.Type.Language, value)
        }

    var location: Label?
        get() = firstLabel(Label.Type.Location)
        set(value) {
            setOrReplaceLabel(Label.Type.Location, value)
        }

    var genres: List<Label>
        get() = getLabels(Label.Type.Genres)
        set(value) {
            setOrReplaceLabels(Label.Type.Genres, value)
        }

    // This ensures that the authors get text-indexed by BookFTS.
    // The compiler warning is to be ignored here: this field is only read - and by the dao only -
    // upon inserting / saving a book to the database.
    // That is in fact what it is for.
    @Suppress("SuspiciousVarProperty")
    @ColumnInfo(name = "author_text")
    var authorText: String = ""
        get() = getLabels(Label.Type.Authors).joinToString { it.name }

    var authors: List<Label>
        get() = getLabels(Label.Type.Authors)
        set(value) {
            setOrReplaceLabels(Label.Type.Authors, value)
        }

    val sqlId: String
        get() = id.toString()

    enum class Status {
        Created,
        Loaded,
        Deleted,
        Saved,
    }
    @Ignore
    var status: Status = Status.Created

    /**
     * When changing the cover of a book, you can either provide a bitmap via this property and
     * call BookRepository.save(book) or set an imgUrl on the book and call save(). If you set both
     * an imgUrl and a bitmap, the bitmap is assumed to be the imgUrl's content.
     */
    @Ignore
    var bitmap: Bitmap? = null

    data class Image(
        val imageFilename: String? = null,  // null mens don't change.
        val bitmap: Bitmap? = null,
        val imgUrl: String = ""
    ) {
        constructor(bitmap: Bitmap?, imgUrl: String) : this(null, bitmap, imgUrl)
    }

    var image: Image
        get() = Image(imageFilename, bitmap, imgUrl)
        set(value) {
            value.imageFilename?.let { imageFilename = it }
            bitmap = value.bitmap
            imgUrl = value.imgUrl
        }
}

@Entity(tableName = "book_fts")
@Fts4(
    contentEntity = Book::class
)
data class BookFTS(
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author_text")
    val author: String
)
