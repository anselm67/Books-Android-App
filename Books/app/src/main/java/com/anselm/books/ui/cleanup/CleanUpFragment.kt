package com.anselm.books.ui.cleanup

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksApplication.Reporter
import com.anselm.books.R
import com.anselm.books.TAG
import com.anselm.books.database.Book
import com.anselm.books.database.Label
import com.anselm.books.database.Query
import com.anselm.books.databinding.CleanupHeaderLayoutBinding
import com.anselm.books.databinding.CleanupItemLayoutBinding
import com.anselm.books.databinding.FragmentCleanupBinding
import com.anselm.books.ui.widgets.BookFragment
import kotlinx.coroutines.launch
import okhttp3.Call
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

class CleanUpFragment: BookFragment() {
    private var _binding: FragmentCleanupBinding? = null
    private val binding get() = _binding!!
    private var reporter: Reporter? = null

    private fun ifEmptyReporter(block: () -> Unit) {
        if (reporter == null) {
            block()
        } else {
            app.warn(requireActivity(), getString(R.string.cleanup_task_already_running))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = FragmentCleanupBinding.inflate(inflater, container, false)


        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.repository.deleteUnusedLabels()
            Log.d(TAG, "Deleted $count unused labels.")
            bookSection(inflater, binding.idStatsContainer)
            labelSection(inflater, binding.idStatsContainer)
            imageSection(inflater, binding.idStatsContainer)
        }

        super.handleMenu()
        return binding.root
    }

    private suspend fun bookSection(inflater: LayoutInflater, container: ViewGroup) {
        // Book section.
        container.addView(header(
            inflater,
            container,
            getString(R.string.book_count,app.repository.getTotalCount()),
        ))
        // Duplicate books.
        var count = app.repository.getDuplicateBookCount()
        if (count > 0) {
            container.addView(bookItem(
                inflater,
                container,
                getString(R.string.duplicate_books_cleanup, count),
                Query(type = Query.Type.Duplicates)
            ))
        }
        // Books without cover images.
        count = app.repository.getWithoutCoverBookCount()
        if (count > 0) {
            container.addView(bookItem(
                inflater, container,
                getString(R.string.without_cover_books_cleanup, count),
                Query(type = Query.Type.NoCover),
            ))
        }
        // Books without certain label type.
        count = app.repository.getWithoutLabelBookCount(Label.Type.Authors)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_authors_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Authors),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Genres)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_genres_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Genres),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Location)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_locations_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Location),
            ))
        }
        count = app.repository.getWithoutLabelBookCount(Label.Type.Language)
        if (count > 0) {
            container.addView(bookQueryItem(
                inflater, container,
                getString(R.string.without_languages_cleanup, count),
                Query(withoutLabelOfType = Label.Type.Language)
            ))
        }
    }

    private suspend fun labelSection(inflater: LayoutInflater, container: ViewGroup) {
        container.addView(header(inflater, container,getString(R.string.labels_cleanup_header)))
        val types = app.repository.getLabelTypeCounts()
        listOf(
            Pair(R.string.authors_cleanup, Label.Type.Authors),
            Pair(R.string.genres_cleanup, Label.Type.Genres),
            Pair(R.string.publishers_cleanup, Label.Type.Publisher),
            Pair(R.string.languages_cleanup, Label.Type.Language),
            Pair(R.string.locations_cleanup, Label.Type.Location),
        ).map { (stringId, type) ->
            container.addView(labelItem(
                inflater, container,
                getString(stringId,
                    types.firstOrNull { it.type == type }?.count ?: 0),
                type,
            ))
        }
    }

    private fun header( inflater: LayoutInflater, container: ViewGroup, title: String): View {
        val header = CleanupHeaderLayoutBinding.inflate(inflater, container, false)
        header.idHeader.text = title
        return header.root
    }

    private fun item(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        onClick: (() -> Unit)? = null,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        onClick?.let { item.idItemText.setOnClickListener { it() } }
        return item.root
    }

    private fun bookQueryItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        query: Query,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toSearchFragment(query)
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun bookItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        query: Query,
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toPagerFragment(
                    query = query,
                )
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun labelItem(
        inflater: LayoutInflater,
        container : ViewGroup,
        text: String,
        labelType: Label.Type
    ): View {
        val item = CleanupItemLayoutBinding.inflate(inflater, container, false)
        item.idItemText.text = text
        item.idItemText.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val action = CleanUpFragmentDirections.toCleanupLabelFragment(labelType)
                findNavController().navigate(action)
            }
        }
        return item.root
    }

    private fun imageSection(
        inflater: LayoutInflater,
        container: ViewGroup,
    ) {
        container.addView(header(
            inflater,
            container,
            getString(R.string.cleanup_book_cover_section),
        ))
        container.addView(item(
            inflater, container,
            getString(R.string.check_for_broken_images)
        ) {
            ifEmptyReporter {
                viewLifecycleOwner.lifecycleScope.launch {
                    checkImages()
                }
            }
        })
        container.addView(item(
            inflater, container,
            getString(R.string.check_gc_images),
        ) {
            ifEmptyReporter {
                viewLifecycleOwner.lifecycleScope.launch {
                    deleteUnusedImages()
                }
            }
        })
    }

    class FixCoverStats(
        var totalCount : Int = 0,       // Total number of books seen.
        var checkedCount: Int = 0,      // Total number cover image checked.
        var brokenCount: Int = 0,       // Number of un-loadable bitmaps from files.
        var unfetchedCount: Int = 0,    // Number of covers that weren't fetched.
        var fetchCount: Int = 0,        // Number of covers we fetched.
        var fetchFailedCount: Int = 0   // Number of failed cover fetches.
    ) {
        private val calls = emptyList<Call>().toMutableList()
        private val urls = emptyList<String>().toMutableList()
        private val lock = ReentrantLock()
        private val cond = lock.newCondition()

        fun cancel() {
            calls.forEach { it.cancel() }
        }

        fun addCall(call: Call) {
            calls.add(call)
        }

        fun addUrl(url: String) {
            lock.withLock {
                urls.add(url)
            }
        }

        fun removeUrl(url: String) {
            lock.withLock {
                urls.remove(url)
                if (urls.isEmpty()) {
                    Log.d(TAG, "cond signaled.")
                    cond.signalAll()
                }
            }
        }

        fun join() {
            while (true) {
                lock.withLock {
                    if (urls.isEmpty()) {
                        return@join
                    }
                    try {
                        cond.await(500, TimeUnit.MILLISECONDS)
                    } catch (e: InterruptedException) { /* ignored */ }
                    Log.d(TAG, "cond awaited, empty? ${calls.isEmpty()}")
                }
            }
        }
    }

    private fun fixCover(stats: FixCoverStats, book: Book) {
        // If the book doesn't have a URL, there's nothing we can do.
        if (book.imgUrl.isEmpty()) {
            return
        }
        stats.fetchCount++
        app.applicationScope.launch {
            stats.addUrl(book.imgUrl)
            val call = app.imageRepository.save(book, force = true) {
                stats.removeUrl(book.imgUrl)
                if (it) {
                    app.applicationScope.launch {
                        app.repository.save(book)
                    }
                }
                if (book.imageFilename.isEmpty()) {
                    stats.fetchFailedCount++
                }
            }
            call?.let { stats.addCall(call) }
        }
    }

    private fun checkImage(stats: FixCoverStats, book: Book) {
        // Nothing we can do without an url to fetch the image from.
        if (book.imgUrl.isEmpty()) {
            return
        }
        if (book.imageFilename.isEmpty()) {
            // The book's image was never loaded, we can fix this.
            stats.unfetchedCount++
            fixCover(stats, book)
        } else /* book.imageFilename.isNotEmpty() */ {
            // Verifies we can load this bitmap and fix if we can't.
            val path = app.imageRepository.getCoverPath(book)
            var failed = true
            try {
                failed = (BitmapFactory.decodeFile(path) == null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode bitmap.")
            }
            if (failed) {
                stats.brokenCount++
                fixCover(stats, book)
            }
        }
    }

    private suspend fun checkImages() {
        val bookIds = app.repository.getIdsList(Query())
        val stats = FixCoverStats()

        reporter = app.openReporter(
            getString(R.string.cleanup_check_image_progress_title),
            isIndeterminate = false) { stats.cancel() }
        bookIds.forEach { bookId ->
            val book = app.repository.load(bookId, decorate = true)
            stats.totalCount++
            if (book != null) {
                stats.checkedCount++
                checkImage(stats, book)
            }
            reporter?.update(stats.totalCount, bookIds.size)
        }
        // Wait until al calls have returned.
        stats.join()
        reporter?.close()
        reporter = null
        Log.d(TAG, "Done ${stats.totalCount}: " +
                "broken: ${stats.brokenCount}, unfetched: ${stats.unfetchedCount} " +
                "fetched: ${stats.fetchCount} of which ${stats.fetchFailedCount} failed."
        )
    }

    private suspend fun doDeleteUnusedImages(reporter: BooksApplication.Reporter) {
        val bookIds = app.repository.getIdsList(Query())
        val seen = mutableSetOf<String>()
        var count = 0
        reporter.update(getString(R.string.listing_existing_images), 0, 0)
        bookIds.forEach {
            val book = app.repository.load(it, decorate = true)
            if (book?.imageFilename?.isNotEmpty() == true) {
                seen.add(book.imageFilename)
            }
            count++
            reporter.update(count, bookIds.size)
        }
        app.imageRepository.garbageCollect(seen, reporter)
    }

    private fun deleteUnusedImages() {
        thread {
            reporter = app.openReporter(getString(R.string.deleting_unused_images), isIndeterminate = false)
            app.applicationScope.launch {
                doDeleteUnusedImages(reporter!!)
                reporter?.close()
                reporter = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}