package com.anselm.books.lookup

import android.util.Log
import com.anselm.books.TAG
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField

abstract class JsonClient: SimpleClient() {

    protected fun stringToList(item: String): List<String> {
        return if (item.isEmpty()) { emptyList() } else { listOf(item) }
    }

    protected inline fun <reified T: Any> arrayToList(a: JSONArray?): List<T> {
        return if (a == null) {
            emptyList()
        } else {
            (0 until a.length()).mapNotNull {
                when (val item = (a.get(it) as T)) {
                    is String -> { item.ifEmpty { null } }
                    is List<*> -> { item.ifEmpty { null } }
                    else -> { item }
                }
            }
        }
    }

    private val dateFormatters = arrayOf(
        DateTimeFormatter.ofPattern("MMMM d, yyyy"),
        DateTimeFormatter.ofPattern("MMMM yyyy"),
        DateTimeFormatter.ofPattern("MMM d, yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM"),
        DateTimeFormatter.ofPattern("yyyy MMMM"),
        DateTimeFormatter.ofPattern("yyyy"),
    )

    protected fun publishDate(s: String): String {
        for (fmt in dateFormatters) {
            try {
                val date = fmt.parse(s)
                if (date != null) {
                    return date.get(ChronoField.YEAR).toString()
                }
            } catch (e: DateTimeParseException) {
                // Ignored.
            }
        }
        Log.d(TAG, "Failed to parse date: $s.")
        return ""
    }

    protected fun parse(resp: Response): JSONObject? {
        if (resp.isSuccessful) {
            val tok = JSONTokener(resp.body!!.string())
            val obj = tok.nextValue()
            if (obj !is JSONObject) {
                Log.e(TAG, "${resp.request.url}: parse failed got a ${obj.javaClass.name}.")
            } else {
                return obj
            }
        }
        return null
    }

}