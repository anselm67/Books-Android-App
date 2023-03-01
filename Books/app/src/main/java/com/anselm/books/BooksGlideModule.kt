package com.anselm.books

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.Headers
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.load.model.stream.BaseGlideUrlLoader
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import java.io.InputStream


private class HeaderLoader(
    delegate: ModelLoader<GlideUrl, InputStream>
): BaseGlideUrlLoader<Uri>(delegate) {

    override fun getUrl(model: Uri?, width: Int, height: Int, options: Options?): String {
        return model.toString()
    }

    override fun getHeaders(model: Uri?, width: Int, height: Int, options: Options?): Headers? {
        // To fetch images from amazon, impersonate Chrome of face thw wrath of a 403
        return LazyHeaders.Builder()
            .addHeader("User-Agent", Constants.USER_AGENT)
            .build()
    }

    override fun handles(model: Uri): Boolean {
        return model.toString().startsWith("http://images.amazon.com/images/")
    }

    class Factory : ModelLoaderFactory<Uri, InputStream> {
        override fun build(factory: MultiModelLoaderFactory): ModelLoader<Uri, InputStream> {
            return HeaderLoader(factory.build(GlideUrl::class.java, InputStream::class.java))
        }

        override fun teardown() {
            // Nothing to release.
        }
    }
}

class GlideErrorHandler : RequestListener<Any> {
    override fun onLoadFailed(
        e: GlideException?,
        model: Any?,
        target: Target<Any>?,
        isFirstResource: Boolean
    ): Boolean {
        /*
         * We pass null uri to Glide whenever a book doesn't have an imageFilename or an imgUrl.
         * This isn't really worth mentioning in the logs.
         */
        if (model != null) {
            Log.e(TAG, "Glide $model failed.", e)
        }
        return false
    }

    override fun onResourceReady(
        resource: Any?,
        model: Any?,
        target: Target<Any>?,
        dataSource: DataSource?,
        isFirstResource: Boolean
    ) = false

}

@GlideModule
class BooksGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(Uri::class.java, InputStream::class.java, HeaderLoader.Factory())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // builder.setLogLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
        builder.setDefaultRequestOptions(
            RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)  // cache all
                .fallback(R.mipmap.ic_book_cover)
                .error(R.mipmap.broken_cover_icon_foreground)
        ).addGlobalRequestListener(GlideErrorHandler())
        // Using animations this was breaks centerCrop, commenting it out for now.
        // builder.setDefaultTransitionOptions(Drawable::class.java, withCrossFade())
    }

    override fun isManifestParsingEnabled(): Boolean = false
}