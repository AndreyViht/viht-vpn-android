package com.pingwin.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection

/** Binary range transfer. The caller still verifies the complete APK's hash and signer. */
internal object ResumableApkTransfer {
    private class InvalidResponse(message: String) : IOException(message)

    suspend fun download(
        file: File,
        size: Long,
        open: () -> HttpURLConnection,
        activeConnection: (HttpURLConnection?) -> Unit = {},
        chunkBytes: Long = 4L * 1024 * 1024,
        pause: suspend (Long) -> Unit = { delay(it) }
    ) {
        require(size > 0 && chunkBytes > 0)
        if (file.length() > size) check(file.delete()) { "Не удалось очистить неполный APK" }
        var failures = 0
        var consecutive = 0
        while (file.length() < size) {
            currentCoroutineContext().ensureActive()
            val offset = file.length()
            val end = minOf(size - 1, offset + chunkBytes - 1)
            var connection: HttpURLConnection? = null
            try {
                val conn = open().also { connection = it; activeConnection(it) }
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.useCaches = false
                conn.setRequestProperty("Accept-Encoding", "identity")
                conn.setRequestProperty("Range", "bytes=$offset-$end")
                val code = conn.responseCode
                if (code !in setOf(200, 206)) {
                    if (code == 408 || code == 429 || code >= 500) throw IOException("Сервер загрузки временно недоступен: HTTP $code")
                    throw InvalidResponse("Сервер загрузки отклонил запрос: HTTP $code")
                }
                val append = code == 206 && offset > 0
                val responseEnd = if (code == 206) {
                    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
                        .matchEntire(conn.getHeaderField("Content-Range").orEmpty().trim())
                        ?: throw InvalidResponse("Сервер не подтвердил диапазон загрузки")
                    val (start, last, total) = match.destructured
                    if (start.toLong() != offset || last.toLong() !in offset..end || total.toLong() != size) {
                        throw InvalidResponse("Сервер вернул другой диапазон или размер APK")
                    }
                    last.toLong() + 1
                } else size
                var written = if (append) offset else 0L
                conn.inputStream.use { input ->
                    FileOutputStream(file, append).use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            currentCoroutineContext().ensureActive()
                            if (written + count > responseEnd) throw InvalidResponse("Сервер прислал лишние данные APK")
                            output.write(buffer, 0, count)
                            written += count
                        }
                    }
                }
                if (written != responseEnd) throw IOException("Соединение прервано; загруженная часть сохранена")
                consecutive = 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: InvalidResponse) {
                throw failure
            } catch (failure: IOException) {
                currentCoroutineContext().ensureActive()
                failures++; consecutive++
                if (consecutive >= 4 || failures >= 12) throw IOException("Не удалось возобновить загрузку после нескольких попыток. ${failure.message}", failure)
                // Re-select Wi-Fi/mobile and resume from the bytes actually saved to disk.
                pause(minOf(4000L, 1000L shl (consecutive - 1)))
            } finally {
                connection?.disconnect()
                activeConnection(null)
            }
        }
    }
}
