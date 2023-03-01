package com.anselm.books.ui.sync

import android.util.Log
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.TAG
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

object MimeType {
    val APPLICATION_JSON = "application/json".toMediaType()
    val MULTIPART_RELATED = "multipart/related".toMediaType()
    const val APPLICATION_FOLDER = "application/vnd.google-apps.folder"
}

class SyncJob(
    private val authToken: String,
) {
    enum class Status {
        STARTED, FINISHED
    }
    private var status = Status.STARTED
    private val tag = nextTag()
    var isCancelled = false
        private set

    private fun builder(): Request.Builder {
        return Request.Builder()
            .tag(tag)
            .header("Authorization", "Bearer $authToken")
    }

    fun createFolder(name: String, parentFolderId: String? = null): SyncCallback<GoogleFile> {
        Log.d(TAG, "createFolder: $name, parent: $parentFolderId.")
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
        val metadata = GoogleFile(
            id = "",
            name = name,
            mimeType = MimeType.APPLICATION_FOLDER,
            folderId = parentFolderId
        ).toJson().toString()
        val req = builder()
            .url(url.build())
            .post(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .build()
        return JsonCallback(req) { obj -> GoogleFile.fromJson(obj) }
    }

    fun updateFile(fileId: String, mimeType: MediaType, content: File): SyncCallback<GoogleFile> {
        Log.d(TAG, "updateFile: ${content.name} type: $mimeType.")
        val url = "https://www.googleapis.com/upload/drive/v3/files/${fileId}?uploadType=media"
        val req = builder()
            .url(url)
            .method("PATCH", content.asRequestBody(mimeType))
            .build()
        return JsonCallback(req) { obj -> GoogleFile.fromJson(obj) }
    }

    fun uploadFile(
        content: File,
        mimeType: String,
        folderId: String? = null,
    ): SyncCallback<GoogleFile> {
        Log.d(TAG, "uploadFile: ${content.name} type: $mimeType.")
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val metadata = GoogleFile("", content.name, mimeType, folderId).toJson().toString()
        val multipartBody = MultipartBody.Builder()
            .setType(MimeType.MULTIPART_RELATED)
            .addPart(metadata.toRequestBody(MimeType.APPLICATION_JSON))
            .addPart(content.asRequestBody(mimeType.toMediaType()))
            .build()
        val req = builder()
            .url(url)
            .post(multipartBody)
            .build()
        return JsonCallback(req) { obj -> GoogleFile.fromJson(obj) }
    }

    fun listFiles(
        query: String,
        pageToken: String? = null,
        into: MutableList<GoogleFile>? = null,
    ): JsonCallback<List<GoogleFile>> {
        val files = into ?: mutableListOf()
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, parents)")
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("pageSize", "500")
        if (pageToken != null) {
            url.addQueryParameter("pageToken", pageToken)
        }
        val req = builder()
            .url(url.build())
            .build()
        val result = JsonCallback<List<GoogleFile>>(req)
        result.convert = { obj ->
            obj.optJSONArray("files")?.let { jsFileArray ->
                (0 until jsFileArray.length()).map { position ->
                    val jsFile = jsFileArray.get(position) as JSONObject
                    files.add(GoogleFile.fromJson(jsFile))
                }
            }
            val nextToken = obj.optString("nextPageToken")
            if (nextToken.isNotEmpty()) {
                val innerResult = listFiles(query, nextToken, files)
                innerResult.onResponseCallback = result.onResponseCallback
                innerResult.onErrorCallback = result.onErrorCallback
                result.onResponseCallback = null
                innerResult.queue()
            }
            files
        }
        return result
    }

    fun get(fileId: String): ResponseCallback {
        Log.d(TAG, "get: $fileId")
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val req = builder()
            .url(url)
            .build()
        return ResponseCallback(req)
    }

    fun delete(fileId: String): JsonCallback<Unit> {
        Log.d(TAG, "deleteFile: $fileId.")
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val req = builder()
            .url(url)
            .method("DELETE", null)
            .build()
        return JsonCallback(req) { }
    }

    private fun parseJson(response: Response): JSONObject? {
        val text = response.body?.string()
        if (text != null && text.isNotEmpty()) {
            val obj = JSONTokener(text).nextValue()
            if (obj !is JSONObject) {
                Log.e(TAG, "${response.request.url}: parse failed got a ${obj.javaClass.name}.")
            } else {
                return obj
            }
        }
        return null
    }

    abstract inner class SyncCallback<T>(
        val req: Request,
    ): Callback {
        internal var onResponseCallback: ((T) -> Unit)? = null
        internal var onErrorCallback: ((Exception) -> Unit)? = null

        fun onResponse(onResponseCallback: (T) -> Unit): SyncCallback<T> {
            this.onResponseCallback = onResponseCallback
            return this
        }

        fun onError(onErrorCallback: (Exception) -> Unit): SyncCallback<T> {
            this.onErrorCallback = onErrorCallback
            return this
        }

        fun queue() {
            this@SyncJob.queue(this)
        }
    }

    inner class JsonCallback<T>(
        req: Request,
        internal var convert: ((JSONObject) -> T)? = null,
    ): SyncCallback<T>(req) {

        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "${req.url}: request failed.", e)
            onErrorCallback?.invoke(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                try {
                    val obj = parseJson(response)
                    if (it.isSuccessful) {
                        val value = convert!!(obj ?: JSONObject())
                        onResponseCallback?.invoke(value)
                    } else {
                        // Throw and catch: all errors match an exception.
                        Log.d(TAG, "${req.url}: status ${response.code} body: $obj.")
                        throw SyncException("${req.url}: request failed.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${req.url}: handling failed.", e)
                    onErrorCallback?.invoke(e)
                }
            }
        }
    }

    inner class ResponseCallback(
        req: Request,
    ): SyncCallback<ByteArray>(req) {
        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "${req.url}: request failed.", e)
            onErrorCallback?.invoke(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                try {
                    if (it.isSuccessful) {
                        onResponseCallback?.invoke(it.body!!.bytes())
                    } else {
                        // Throw and catch: all errors match an exception.
                        Log.d(TAG, "${req.url}: status ${response.code}.")
                        throw SyncException("${req.url}: request failed.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${req.url}: handling failed.", e)
                    onErrorCallback?.invoke(e)
                }
            }
        }
    }

    private fun queue(callback: SyncCallback<*>) {
        if ( isCancelled ) {
            Log.e(TAG, "SyncJob $tag cancelled/erred, rejecting request.")
            return
        }
        app.okHttp.newCall(callback.req).enqueue(callback)
    }

    private val lock = ReentrantLock()
    private val cond = lock.newCondition()

    fun done() {
        lock.withLock {
            status = Status.FINISHED
            cond.signalAll()
        }
    }

    fun cancel() {
        lock.withLock {
            isCancelled = true
            cond.signalAll()
        }
        app.cancelHttpRequests(tag)
    }

    fun flush() {
        while (status != Status.FINISHED  && ! isCancelled) {
            try {
                lock.withLock {
                    cond.await()
                }
            } catch (_: InterruptedException) { }
        }
        app.flushOkHttp()
    }

    fun start(runnable: Runnable) {
        thread {
            runnable.run()
        }
    }


    companion object {
        private val idCounter = AtomicInteger(0)
        private fun nextTag() = "syncjob-${idCounter.incrementAndGet()}"
    }
}