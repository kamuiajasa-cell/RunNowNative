package com.joyodigitama.huaweiwear

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.FileHeader

/**
 * The export ZIP over zip4j, which reads the AES-256 encryption the Privacy Centre applies with the password the user
 * set (the JDK's ZipInputStream and Android's ZipFile cannot). zip4j needs a seekable [File].
 */
internal class Zip4jExportSource private constructor(
  private val zip: ZipFile,
  private val headers: Map<String, FileHeader>,
) : HuaweiExportSource, Closeable {
  override fun entries(): List<HuaweiExportEntry> = headers.map { (name, header) -> HuaweiExportEntry(name, header.uncompressedSize) }

  override fun open(entry: HuaweiExportEntry): InputStream =
    zip.getInputStream(headers[entry.name] ?: throw IOException("No such entry: ${entry.name}"))

  override fun close() = zip.close()

  companion object {
    /**
     * Opens [file] and checks the password against the smallest encrypted member, so a wrong one fails here and not
     * after minutes of work. [password] is copied by zip4j; the caller keeps ownership of the array.
     */
    fun open(file: File, password: CharArray): Zip4jExportSource {
      val zip = ZipFile(file, password)
      try {
        if (!zip.isValidZipFile) throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "This is not a ZIP file")
        val headers = LinkedHashMap<String, FileHeader>()
        for (header in zip.fileHeaders) if (!header.isDirectory) headers[header.fileName] = header
        val probe = headers.values.filter { it.isEncrypted }.minByOrNull { it.uncompressedSize }
        if (probe != null) {
          if (password.isEmpty()) throw HuaweiImportException(HuaweiImportException.Kind.BAD_PASSWORD, "This ZIP needs the password you set when you requested the data")
          try {
            zip.getInputStream(probe).use { it.read() }
          } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) {
              throw HuaweiImportException(HuaweiImportException.Kind.BAD_PASSWORD, "Wrong ZIP password", e)
            }
            throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "The ZIP could not be read: ${e.message}", e)
          }
        }
        return Zip4jExportSource(zip, headers)
      } catch (e: HuaweiImportException) {
        zip.close()
        throw e
      } catch (e: ZipException) {
        zip.close()
        throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "The ZIP could not be read: ${e.message}", e)
      } catch (e: IOException) {
        zip.close()
        throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "The ZIP could not be read: ${e.message}", e)
      }
    }
  }
}

/** An opened export plus whatever has to be released with it (a file descriptor, a temporary copy). */
internal class OpenedHuaweiExport(val source: Zip4jExportSource, private val release: () -> Unit) : Closeable {
  override fun close() {
    try {
      source.close()
    } finally {
      release()
    }
  }
}

/** Turns what the file picker returns into a [Zip4jExportSource]. */
internal object HuaweiExportFiles {
  private const val COPY_NAME = "huawei-export-import.zip"

  /**
   * The name the sender gave the file at [uriString]: the provider's `DISPLAY_NAME` for a `content://` URI (whose own last
   * segment is an opaque id such as `1ec426fd-...`), the file name for a `file://` URL or path. Null when it cannot be told.
   */
  fun displayName(context: Context, uriString: String): String? {
    val uri = Uri.parse(uriString)
    if (uri.scheme == null || uri.scheme == "file") return File(uri.path ?: uriString).name.ifEmpty { null }
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0).ifEmpty { null } else null
      }
    } catch (e: Exception) {
      null
    }
  }

  /**
   * [uriString] is a `content://` URI from the system file picker or a `file://` URL / path. A content URI backed by a
   * real file is opened in place through its file descriptor (`/proc/self/fd/N`), so a multi-GB export is not copied.
   * Providers that stream (no seeking) fall back to a copy in the cache directory, removed when the import ends.
   */
  fun open(context: Context, uriString: String, password: CharArray): OpenedHuaweiExport {
    val uri = Uri.parse(uriString)
    if (uri.scheme == null || uri.scheme == "file") {
      val file = File(uri.path ?: uriString)
      return OpenedHuaweiExport(Zip4jExportSource.open(file, password)) {}
    }
    val resolver = context.contentResolver
    val descriptor = try {
      resolver.openFileDescriptor(uri, "r")
    } catch (e: Exception) {
      null
    } ?: throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "The file could not be opened")
    try {
      return OpenedHuaweiExport(Zip4jExportSource.open(File("/proc/self/fd/${descriptor.fd}"), password)) { descriptor.close() }
    } catch (e: HuaweiImportException) {
      if (e.kind != HuaweiImportException.Kind.NOT_A_ZIP) {
        descriptor.close()
        throw e
      }
    }
    // Not seekable in place (or not a ZIP at all): read it into the cache and look again.
    val size = descriptor.statSize
    descriptor.close()
    val copy = File(context.cacheDir, COPY_NAME)
    try {
      if (size > 0 && context.cacheDir.usableSpace < size + SPACE_MARGIN) {
        throw HuaweiImportException(HuaweiImportException.Kind.STORAGE, "Not enough free space to read this file")
      }
      val input = resolver.openInputStream(uri) ?: throw HuaweiImportException(HuaweiImportException.Kind.NOT_A_ZIP, "The file could not be opened")
      input.use { source -> copy.outputStream().use { source.copyTo(it) } }
      return OpenedHuaweiExport(Zip4jExportSource.open(copy, password)) { copy.delete() }
    } catch (e: IOException) {
      copy.delete()
      throw HuaweiImportException(HuaweiImportException.Kind.STORAGE, "The file could not be read: ${e.message}", e)
    } catch (e: HuaweiImportException) {
      copy.delete()
      throw e
    }
  }

  private const val SPACE_MARGIN = 64L * 1024 * 1024
}
