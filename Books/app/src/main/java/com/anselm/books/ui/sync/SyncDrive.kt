package com.anselm.books.ui.sync

/*
 * ok http multipart
 * https://gist.github.com/balvinder294/e869944161cb0af250b1296f64e3129a#file-post-file-java
 */
import android.util.Log
import com.anselm.books.BooksApplication
import com.anselm.books.BooksApplication.Companion.app
import com.anselm.books.BooksApplication.Reporter
import com.anselm.books.Constants
import com.anselm.books.R
import com.anselm.books.TAG
import kotlinx.coroutines.launch
import java.io.File

private class CounterReporter(
    private val reporter: Reporter,
    private val totalCount: Int,
) {
    private var counter = 0

    fun incr() {
        counter++
        reporter.update(counter, totalCount)
    }
}

private class Node(
    val localDirectory: File,
    val localName: String,
) {
    var folderId: String? = null
    val localFiles = emptyList<String>().toMutableList()
    val remoteFiles = emptyList<GoogleFile>().toMutableList()
    val localChildren = emptyList<Node>().toMutableList()

    private fun getChild(name: String): Node? {
        return localChildren.firstOrNull { name == it.localName }
    }

    private fun diffChildren(job: SyncJob, doneCounter: CounterReporter) {
        check(folderId != null)
        remoteFiles.forEach {
            if ( ! localFiles.contains(it.name)) {
                localDirectory.mkdirs()
                job.get(it.id)
                    .onResponse { data ->
                        File(localDirectory, it.name).outputStream().use { out ->
                            out.write(data)
                        }
                        doneCounter.incr()
                    }.queue()
            }
        }
        localFiles.forEach { name ->
            if ( remoteFiles.firstOrNull{ it.name == name } == null ) {
                job.uploadFile(File(localDirectory, name), "image/heic", folderId)
                    .onResponse { doneCounter.incr() }
                    .queue()
            }
        }
        localChildren.forEach {
            it.diff(job, folderId, doneCounter)
        }
    }

    fun diff(
        job: SyncJob,
        parentFolderId: String? = null,
        doneCounter: CounterReporter,
    ) {
        if (folderId == null) {
            require(parentFolderId != null) { "parentFolderId required to createFolder." }
            job.createFolder(localName, parentFolderId)
                .onResponse {
                    doneCounter.incr()
                    folderId = it.id
                    diffChildren(job, doneCounter)
                }.queue()
        } else {
            diffChildren(job, doneCounter)
        }
    }

    fun countOps():Int {
        var count =  if (folderId == null) 1 else 0
        count += remoteFiles.filter { ! localFiles.contains(it.name) }.size
        count += localFiles.filter { name -> remoteFiles.firstOrNull{ it.name == name } == null }.size
        localChildren.forEach {
            count += it.countOps()
        }
        return count
    }

    private fun collectChildren(list: List<GoogleFile>) {
        list.forEach {
            if (it.folderId == folderId) {
                if (it.mimeType == MimeType.APPLICATION_FOLDER) {
                    var localChild = getChild(it.name)
                    if (localChild != null) {
                        localChild.folderId = it.id
                    } else {
                        Log.d(TAG, "MKDIR $localName/${it.name}")
                        localChild = Node(File(localDirectory, it.name), it.name)
                        localChild.folderId = it.id
                    }
                    localChildren.add(localChild)
                    localChild.collectChildren(list)
                } else {
                    remoteFiles.add(it)
                }
            }
        }
    }

    fun merge(list: List<GoogleFile>): Node {
        // Find the root from our config.
        val root = list.firstOrNull { it.id == SyncConfig.get().folderId }
        if (root == null) {
            Log.d(TAG, "Root not matching, nothing we can do!")
            return this
        }
        folderId = root.id
        Log.d(TAG, "Merging $localName with ${root.name}")
        collectChildren(list)
        return this
    }

    private fun space(len: Int): String {
        return " ".repeat(len)
    }

    @Suppress("unused")
    fun display(level: Int = 0) {
        Log.d(TAG, "${space(level)}DIR: $localName")
        localFiles.forEach {
            Log.d(TAG, "${space(level+2)}CHILD: $it")
        }
        localChildren.forEach {
            it.display(level + 4)
        }
    }

    companion object {
        fun fromFile(root: File, into: Node? = null): Node {
            val node = into ?: Node(root, root.name)
            root.list()?.forEach {
                val childFile = File(root, it)
                if (childFile.isDirectory) {
                    val child = Node(childFile, it)
                    node.localChildren.add(child)
                    fromFile(childFile, child)
                } else {
                    node.localFiles.add(it)
                }
            }
            return node
        }
    }
}

class SyncDrive(
    private val authToken: String,
    private val reporter: BooksApplication.Reporter
) {
    private val config = SyncConfig.get()

    private fun doCreateRoot(job: SyncJob, onDone: () -> Unit) {
        job.createFolder(Constants.DRIVE_FOLDER_NAME)
            .onResponse {
                config.folderId = it.id
                config.save()
                onDone()
            }.queue()
    }

    private fun createRoot(
        job: SyncJob,
        onDone: () -> Unit
    ) {
        if (config.folderId.isEmpty()) {
            doCreateRoot(job, onDone)
        } else {
            job.listFiles("name='${Constants.DRIVE_FOLDER_NAME}' and trashed = false")
                .onResponse { files ->
                    val root = files.firstOrNull { it.id == config.folderId }
                    if (root == null) {
                        Log.d(TAG, "createRoot: old root deleted, creating new root.")
                        doCreateRoot(job, onDone)
                    } else {
                        onDone()
                    }
                }.queue()
        }
    }

    private fun mergeRemoteJson(
        job: SyncJob,
        onDone: (jsonFileId: String?) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        job.listFiles("name='books.json' and trashed = false")
            .onResponse { files ->
                if (files.isEmpty()) {
                    onDone(null)
                } else if (files.size > 1) {
                    Log.d(TAG, "Found ${files.size} remote json files, deleting them.")
                    files.forEach { job.delete(it.id).queue() }
                    onDone(null)
                } else {
                    job.get(files[0].id)
                        .onResponse { bytes ->
                            val text = String(bytes, Charsets.UTF_8)
                            app.applicationScope.launch {
                                app.importExport.importJsonText(text, reporter)
                                onDone(files[0].id)
                            }
                        }
                        .onError {
                            Log.e(TAG, "mergeRemoteJson: failed to merge remote file.", it)
                            onError(it)
                        }.queue()
                }
            }
            .onError {
                Log.e(TAG, "mergeRemoteJson: failed to list for json.books (ignored).", it)
                onError(it)
            }.queue()
    }

    private fun uploadJson(job: SyncJob, jsonFileId: String?, onDone: () -> Unit) {
        val file = File(app.applicationContext.cacheDir, "books.json")
        file.deleteOnExit()
        app.applicationScope.launch {
            file.outputStream().use {
                app.importExport.exportJson(it, reporter)
            }
            if (jsonFileId != null) {
                job.updateFile(jsonFileId, MimeType.APPLICATION_JSON, file)
                    .onResponse{
                        Log.d(TAG, "updateJson: books.json uploaded, id: $jsonFileId")
                        onDone()
                    }
                    .onError{
                        Log.e(TAG, "uploadJson: failed, ignored.", it)
                        onDone()
                    }.queue()
            } else {
                job.uploadFile(file, "application/json", config.folderId)
                    .onResponse {
                        Log.d(TAG, "uploadJson: json.books created, id: ${it.id}")
                        onDone()
                    }
                    .onError {
                        Log.e(TAG, "uploadJson: failed, ignored.", it)
                        onDone()
                    }.queue()
            }
        }
    }

    private fun syncJson(job: SyncJob, onDone: () -> Unit, onError: (Exception) -> Unit) {
        mergeRemoteJson(job, onDone = { jsonFileId ->
            uploadJson(job, jsonFileId, onDone)
        }, onError = {
            Log.d(TAG, "mergeRemoteJson failed, not uploading new books.json version.")
            onError(it)
        })
    }

    private fun syncImages(job: SyncJob) {
        reporter.update(app.getString(R.string.sync_fetching_remote_database), 0, 100)
        val local = Node.fromFile(app.basedir)
        job.listFiles("trashed = false")
            .onResponse {  remoteFiles ->
                val root = local.merge(remoteFiles)
                val totalCount = root.countOps()
                reporter.update(app.getString(R.string.syncing_images), 0, 0)
                val doneCounter = CounterReporter(reporter, totalCount)
                root.diff(job, doneCounter = doneCounter)
            }.queue()
        // We're no longer explicitly adding requests to this job.
        job.done()
    }

    fun sync(
        onDone: (SyncJob, syncFailed: Boolean) -> Unit,
    ): SyncJob {
        val job = SyncJob(authToken)
        var syncFailed = false
        job.start {
            reporter.update(app.getString(R.string.sync_checking_root_directroy), 0, 100)
            createRoot(job) {
                syncJson(
                    job,
                    onDone = { syncImages(job) },
                    onError = { syncFailed = true }
                )
            }
            job.flush()
            config.save(updateLastSync = true)
            onDone(job, syncFailed)
        }
        return job
    }
}
