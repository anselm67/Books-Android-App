package com.anselm.books.ui.sync

import com.anselm.books.ifNotEmpty
import org.json.JSONArray
import org.json.JSONObject

data class GoogleFile(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val folderId: String? = null,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        id.ifNotEmpty {  obj.put("id", id) }
        name.ifNotEmpty { obj.put("name", name) }
        name.ifNotEmpty { obj.put("mimeType", mimeType) }
        if (folderId != null) {
            val parents = JSONArray().apply {
                put(folderId)
            }
            obj.put("parents", parents)
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): GoogleFile {
            val parents = obj.optJSONArray("parents")
            var folderId: String? = null
            if (parents != null && parents.length() > 0) {
                folderId = parents.getString(0)
            }
            return GoogleFile(
                obj.optString("id"),
                obj.optString("name"),
                obj.optString("mimeType"),
                folderId,
            )
        }
    }
}
