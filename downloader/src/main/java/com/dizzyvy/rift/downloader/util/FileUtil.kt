package com.dizzyvy.rift.downloader.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.dizzyvy.rift.downloader.DownloaderRuntime.Companion.context
import com.dizzyvy.rift.downloader.R
import java.io.File
import okhttp3.internal.closeQuietly

const val AUDIO_REGEX = "(mp3|aac|opus|m4a)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) =
        path
            .runCatching {
                createIntentForOpeningFile(this)?.run { context.startActivity(this) }
                    ?: throw Exception()
            }
            .onFailure { onFailureCallback(it) }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? =
        createIntentForFile(path)?.let {
            it.apply {
                action = (Intent.ACTION_VIEW)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            if (!File(path).delete()) DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete()
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(title: String, downloadDir: String): List<String> =
        getDownloadFilesPostDownload(title, downloadDir)
            .toMutableList()
            .apply {
                MediaScannerConnection.scanFile(context, this.toTypedArray(), null, null)
            }

    fun getDownloadFilesPostDownload(title: String, downloadDir: String): List<String> =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile && it.absolutePath.contains(title) }
            .map { it.absolutePath }
            .filterNot {
                    it.contains(Regex(THUMBNAIL_REGEX)) || it.contains(Regex(SUBTITLE_REGEX))
                }
            .toList()

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    @CheckResult
    fun moveFilesToSdcard(tempPath: File, sdcardUri: String): Result<List<String>> =
        runCatching {
            val filePaths = tempPath.walkTopDown().filter(File::isFile).map(File::getAbsolutePath).toList()
            val copied = copyFilesToTree(filePaths, Uri.parse(sdcardUri), tempPath)
            check(tempPath.deleteRecursively()) { "Could not remove staged downloads after copying." }
            copied
        }

    fun copyFilesToTree(
        filePaths: List<String>,
        treeUri: Uri,
        baseDirectory: File,
    ): List<String> {
        val tree = checkNotNull(DocumentFile.fromTreeUri(context, treeUri)) {
            "The selected download folder is no longer available."
        }
        check(tree.canWrite()) { "The selected download folder is not writable." }

        val createdDestinations = mutableListOf<DocumentFile>()
        val copiedUris = try {
            filePaths.map { path ->
                val source = File(path)
                check(source.isFile) { "A downloaded file could not be found: ${source.name}" }
                val relativePath =
                    source.absolutePath.removePrefix(baseDirectory.absolutePath.trimEnd(File.separatorChar) + File.separator)
                check(
                    relativePath != source.absolutePath &&
                        relativePath != ".." &&
                        !relativePath.startsWith("..${File.separator}"),
                ) {
                    "Downloaded file is outside its staging directory."
                }
                val parentNames = relativePath.split(File.separatorChar).dropLast(1)
                val destinationFolder =
                    parentNames.fold(tree) { folder, name ->
                        val existing = folder.findFile(name)
                        existing?.takeIf(DocumentFile::isDirectory)
                            ?: existing?.let {
                                error("A file prevents creating the download folder $name.")
                            }
                            ?: checkNotNull(folder.createDirectory(name)) {
                                "Could not create the download folder $name."
                            }
                    }
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(source.extension) ?: "*/*"
                val destination =
                    checkNotNull(destinationFolder.createFile(mimeType, source.name)) {
                        "Could not create ${source.name} in the selected folder."
                    }
                try {
                    val output =
                        checkNotNull(context.contentResolver.openOutputStream(destination.uri)) {
                            "Could not write ${source.name} to the selected folder."
                        }
                    output.use { outputStream ->
                        source.inputStream().use { input -> input.copyTo(outputStream) }
                    }
                    createdDestinations += destination
                    destination.uri.toString()
                } catch (error: Throwable) {
                    if (!destination.delete()) {
                        Log.w(TAG, "Could not remove incomplete destination file: ${destination.uri}")
                    }
                    throw error
                }
            }
        } catch (error: Throwable) {
            createdDestinations.asReversed().forEach { destination ->
                if (!destination.delete()) {
                    Log.w(TAG, "Could not roll back destination file: ${destination.uri}")
                }
            }
            throw error
        }

        filePaths.forEach { path ->
            val source = File(path)
            if (!source.delete()) Log.w(TAG, "Copied download but could not remove staged file: $path")
        }
        return copiedUris
    }

    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory(): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloader"),
            "RIFT",
        ).also { check(it.isDirectory || it.mkdirs()) { "Could not create download storage." } }

    internal fun getExternalPrivateDownloadDirectory() =
        File(context.filesDir, "downloader/private")

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast("This directory is not supported")
            return getExternalDownloadDirectory().absolutePath
        }
        val last: String = path.split("primary:").last()
        return Environment.getExternalStorageDirectory().absolutePath + "/$last"
    }

    private const val TAG = "FileUtil"
}
