package com.anselm.books

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.heifwriter.HeifWriter
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksApplication.Reporter
import com.anselm.books.database.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeFormatter

class ImageRepository(
    private val basedir: File,
) {
    // Name of the directory in which images are saved.
    // This name is the same within the exported zip, and it has to be that way.
    val imageDirectory = File(basedir, Constants.IMAGE_FOLDER_NAME)

    private fun getCoverUri(imageFilename: String, imgUrl: String): Uri? {
        return if (imageFilename != "") {
            File(basedir, imageFilename).toUri()
        } else if (imgUrl != "") {
            Uri.parse(imgUrl)
        } else {
            null
        }
    }

    fun getCoverUri(image: Book.Image): Uri? {
        return getCoverUri(image.imageFilename ?: "", image.imgUrl)
    }

    fun getCoverUri(book: Book): Uri? {
        return getCoverUri(book.imageFilename, book.imgUrl)
    }

    fun getCoverPath(book: Book): String {
        check(book.imageFilename.isNotEmpty()) { "getCoverPath requires a book imageFilename."}
        return File(basedir, book.imageFilename).path
    }

    private fun deleteImageFile(book: Book) {
        if (book.imageFilename.isNotEmpty()) {
            File(basedir, book.imageFilename).delete()
        }
        book.imageFilename = ""
    }

    /**
     * Fetches the book cover from its URL when needed.
     * Checks if the book's cover has already been loaded; If not fetches it, converts it
     * to the HEIF format, and stores it locally so it'll be included if you exportZip.
     */
    private suspend fun fetchCover(book: Book, onCompletion: (Boolean) -> Unit): Call {
        check(book.imgUrl.isNotEmpty()) { "Cannot fetch a cover without a URL, bookId: ${book.id}."}
        val call = app.okHttp.newCall(Request.Builder().url(book.imgUrl).build())
        call.enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "fetchCover $book.imgUrl failed.", e)
                onCompletion(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful && response.body != null) {
                        val bitmap = BitmapFactory.decodeStream(response.body!!.byteStream())
                        if (bitmap != null) {
                            app.applicationScope.launch {
                                saveBitmap(book, bitmap, onCompletion)
                            }
                            return
                        }
                    } else {
                        Log.e(TAG, "fetchCover ${book.imgUrl} failed code ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchCover $book.imgUrl failed to load.", e)
                }
                onCompletion(false)
            }

        })
        return call
    }

    private suspend fun saveBitmap(
        book: Book,
        origBitmap: Bitmap,
        onCompletion: (Boolean) -> Unit,
    ) {
        val (imageFilename, file) = getFileFor(book)
        var ok = false
        try {
            file.parentFile?.mkdirs()

            val bitmap = resize(origBitmap)
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { outputStream ->
                    HeifWriter.Builder(
                        outputStream.fd,
                        bitmap.width, bitmap.height,
                        HeifWriter.INPUT_MODE_BITMAP
                    ).build().apply {
                        start()
                        addBitmap(resize(bitmap))
                        stop(0)
                        close()
                    }
                }
            }
            deleteImageFile(book)
            book.imageFilename = imageFilename
            ok = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to $imageFilename")
        }
        onCompletion(ok)
    }

    /**
     * Do whatever it takes to save this book's image, if needed. Several cases:
     * a. If the book has no bitmap and an imageFilename and no imgUrl, we do nothing.
     * b. If the book has no bitmap an imgUrl and the matching file - md5(imgUrl) - exists, we do
     *   nothing.
     * Otherwise:
     * 1. bitmap and imgUrl: the bitmap must corresponds to the imgUrl, we save it in md5(imgUrl)
     *    as if we fetched it.
     * 2. imgUrl and no bitmap: we fetch the bitmap, and save it in a file named md5(imgUrl)
     * 3. bitmap and no imgUrl: could be either camera or media pick. we save it under a new filename
     */
    suspend fun save(book: Book, force: Boolean = false, onCompletion: (Boolean) -> Unit): Call? {
        // By resetting imageFilename, we force ourselves into case 2. below.
        if (force && book.imgUrl.isNotEmpty()) {
            deleteImageFile(book)
        }
        // Are we provided with a new bitmap image for this book?
        if (book.bitmap != null) {
            // Cases 1 and 3 here.
            saveBitmap(book, book.bitmap!!, onCompletion)
            return null
        } else if ( book.imgUrl.isNotEmpty() ) {
            val (imageFilename, imageFile) = getFileFor(book)
            if (imageFilename == book.imageFilename && imageFile.exists()) {
                // Case b.
            } else {
                // Case 2. here:
                Log.d(TAG, "${book.title}: $imageFilename vs ${book.imageFilename} exists ${imageFile.path} : ${imageFile.exists()}")
                return fetchCover(book, onCompletion)
            }
        } else {
            // Case a. here
        }
        onCompletion(false)
        return null
    }

    /**
     * Computes a unique image filename for this book.
     * If the book has an imgUrl, then it is used to drive a filename, otherwise - when the cover
     * is taken from the camera or the media picker - we only have a bitmap. In that case,
     * the image filename changes for every new bitmap. This ensures the Glide cache doesn't mess
     * with us.
     */

    private fun getImageFilename(book: Book): String {
        val input = if (book.imgUrl.isNotEmpty()) {
            book.imgUrl
        } else {
            val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            "$now:${book.title}:${book.authors.joinToString { it.name }}"
        }
        val path = MD5.from(input)
        val sep = File.separator
        return "${Constants.IMAGE_FOLDER_NAME}${sep}${path.substring(0, 2)}${sep}${path}"
    }

    private fun getFileFor(book: Book): Pair<String, File> {
        val imageFilename = getImageFilename(book)
        return Pair(imageFilename, File(basedir, imageFilename))
    }

    private fun resize(bitmap:Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(
            bitmap,
            Constants.IMAGE_SIZE.width,
            Constants.IMAGE_SIZE.height,
            true
        )
    }

    /**
     * This can be used during import to fix image filenames to use the convetions described
     * in save() above.
     */
    @Suppress("unused")
    fun fix(book: Book) {
        if (book.imageFilename.isNotEmpty() && book.imgUrl.isNotEmpty()) {
            val correctImageFilename = getImageFilename(book)
            if (correctImageFilename != book.imageFilename) {
                val src = File(basedir, book.imageFilename)
                val dst = File(basedir, correctImageFilename)
                src.renameTo(dst)
                book.imageFilename = correctImageFilename
            }
        }
    }

    private suspend fun doGarbageCollect(
        seen: Set<String>,
        seenCount: Int,
        dir: File = imageDirectory,
        reporter: Reporter,
    ): Int {
        var thisCount = seenCount
        reporter.update(app.getString(R.string.collecting_unused_images), thisCount, seen.size)
        var deletedCount = 0
        withContext(Dispatchers.IO) {
            dir.list()?.forEach {
                val file = File(dir, it)
                if (file.isDirectory) {
                    deletedCount += doGarbageCollect(seen, thisCount, file, reporter)
                } else {
                    val imageFilename = file.relativeTo(basedir).path
                    if (!seen.contains(imageFilename)) {
                        file.delete()
                        deletedCount++
                    } else {
                        thisCount++
                        reporter.update(thisCount, seen.size)
                    }
                }
            }
        }
        return deletedCount
    }

    suspend fun garbageCollect(seen: Set<String>, reporter: Reporter) {
        val count = doGarbageCollect(seen, 0, reporter = reporter)
        Log.d(TAG, "Deleted $count unused image.")
    }
}