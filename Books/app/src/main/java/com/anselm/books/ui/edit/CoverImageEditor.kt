package com.anselm.books.ui.edit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BuildConfig
import com.anselm.books.GlideApp
import com.anselm.books.MainActivity
import com.anselm.books.database.Book
import com.anselm.books.databinding.EditCoverImageLayoutBinding
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.io.FileInputStream
import kotlin.reflect.KMutableProperty1

/**
 * Editor for the book's cover image.
 * This really doesn't fit well the Editor framework, but it looks nicer this
 * way than a full exception. May be.
 */
class CoverImageEditor<T>(
    fragment: Fragment,
    inflater: LayoutInflater,
    target: T,
    property: KMutableProperty1<T, Book.Image>,
    onChange: ((Editor<T, Book.Image>) -> Unit)? = null,
) : Editor<T, Book.Image>(fragment, inflater, target, property, 0, onChange) {
    private var _binding: EditCoverImageLayoutBinding? = null
    private val editor get() = _binding!!
    private lateinit var coverCameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var coverPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>

    private var cameraImageFile: File? = null
    private var editCoverBitmap: Bitmap? = null
    private var editCoverImgUrl: String? = null

    // http://sylvana.net/jpegcrop/exif_orientation.html
    private val exifAngles = mapOf(
        1 to 0F,
        2 to 0F,
        3 to 180F,
        4 to 180F,
        5 to 90F,
        6 to 90F,
        7 to 270F,
        8 to 270F,
    )

    /*
     * We do this right away, cause by the time setup is reached, views have been created in the
     * EditFragment, and its to late to registerForActivityResult.
     */
    init {
        setupCoverCameraLauncher()
        setupCoverPickerLauncher()
    }

    override fun setup(container: ViewGroup?): View {
        super.setup(container)
        _binding = EditCoverImageLayoutBinding.inflate(inflater, container, false)
        editor.idCameraPickerButton.setOnClickListener {
            launchCoverCamera()
        }
        editor.idMediaPickerButton.setOnClickListener {
            launchCoverPicker()
        }
        editor.idUndoEdit.setOnClickListener {
            cameraImageFile = null
            editCoverBitmap = null
            editCoverImgUrl = null
            loadCoverImage(app.imageRepository.getCoverUri(property.getter(target)))
            setUnchanged(editor.idCoverImage, editor.idUndoEdit)
        }
        loadCoverImage(app.imageRepository.getCoverUri(property.getter(target)))
        return editor.root
    }

    override fun isChanged() = (editCoverBitmap != null)

    override fun saveChange() {
        check(editCoverBitmap != null)
        // Setting these fields will trigger saving of the cover.
        property.setter(target, Book.Image(editCoverBitmap, editCoverImgUrl ?: ""))
    }

    private fun setupCoverCameraLauncher() {
        coverCameraLauncher = fragment.registerForActivityResult(ActivityResultContracts.TakePicture()) {
            // No bitmap? the user cancelled on us.
            if ( ! it || cameraImageFile == null) {
                return@registerForActivityResult
            }
            val exifRotation = ExifInterface(cameraImageFile!!)
                .getAttribute(ExifInterface.TAG_ORIENTATION)
                ?.toIntOrNull()
            BitmapFactory.decodeFile(cameraImageFile!!.path, BitmapFactory.Options().apply {
                inSampleSize = 8 // Going from about 4,000px width down to about 500px.
            }).also { cameraBitmap ->
                editCoverBitmap = Bitmap.createBitmap(
                    cameraBitmap,
                    0, 0, cameraBitmap.width, cameraBitmap.height,
                    Matrix().apply {
                        postRotate(exifAngles.getOrDefault(key = exifRotation, defaultValue = 0F))
                    },
                    true)
                editCoverBitmap?.let { bitmap ->
                    loadCoverImage(bitmap)
                }
            }
        }
    }

    private fun launchCoverCamera() {
        if ( ! (fragment.requireActivity() as MainActivity).checkCameraPermission()) {
            return
        }
        if (cameraImageFile == null) {
            cameraImageFile = File.createTempFile("cover_edit", ".png", app.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
        }
        coverCameraLauncher.launch(
            FileProvider.getUriForFile(app.applicationContext,
            "${BuildConfig.APPLICATION_ID}.provider",
            cameraImageFile!!)
        )
    }

    private fun setupCoverPickerLauncher() {
        coverPickerLauncher = fragment.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                return@registerForActivityResult
            }
            app.contentResolver.openFileDescriptor(uri, "r").use { it?.let {
                    FileInputStream(it.fileDescriptor).use { inputStream ->
                        editCoverBitmap = BitmapFactory.decodeStream(inputStream)
                    }
                }
            }
            editCoverBitmap?.let {
                loadCoverImage(it)
            }
        }
    }

    private fun launchCoverPicker() {
        coverPickerLauncher.launch(
            PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun loadCoverImage(bitmap: Bitmap) {
        app.postOnUiThread {
            setChanged(editor.idCoverImage, editor.idUndoEdit)
            GlideApp.with(app.applicationContext)
                .load(bitmap)
                .into(editor.idCoverImage)
        }
    }

    private fun loadCoverImage(uri: Uri?) {
        GlideApp.with(app.applicationContext)
            .load(uri)
            .into(editor.idCoverImage)
    }

    override var value
        get() =  property.getter(target)
        set(value) {
            if (value.imgUrl.isEmpty() || property.getter(target).imgUrl == value.imgUrl) {
                return
            }
            // We're going to suggest this image.
            GlideApp.with(app.applicationContext)
                .asBitmap()
                .load(value.imgUrl)
                .into(object: CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        editCoverBitmap = resource
                        editCoverImgUrl = value.imgUrl
                        loadCoverImage(resource)
                    }
                    override fun onLoadCleared(placeholder: Drawable?) { }
                })
        }
}