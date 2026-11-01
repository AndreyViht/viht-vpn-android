package com.pingwin.vpn

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ResumableApkTransferTest {
    private val data = ByteArray(97) { (it * 7).toByte() }
    private fun file() = File.createTempFile("viht-transfer-", ".apk").apply { deleteOnExit() }
    private class Reply(
        private val code: Int,
        private val range: (String) -> String,
        private val body: (String) -> InputStream
    ) : HttpURLConnection(URL("https://test.invalid/fixture.apk")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String?) = if (name.equals("Content-Range", true)) range(getRequestProperty("Range")) else null
        override fun getInputStream() = body(getRequestProperty("Range"))
    }
    private fun bounds(header: String): Pair<Int, Int> = header.removePrefix("bytes=").split('-').let { it[0].toInt() to it[1].toInt() }

    @Test fun interruptedResponseAutomaticallyResumesAtTheBytesActuallySaved() = runBlocking {
        val target = file(); val requests = mutableListOf<String>(); var attempts = 0
        try {
            ResumableApkTransfer.download(target, data.size.toLong(), open = {
                val first = attempts++ == 0
                Reply(206, { h -> val (start, end) = bounds(h); "bytes $start-$end/${data.size}" }, { h ->
                    requests += h
                    val (start, end) = bounds(h)
                    ByteArrayInputStream(data.copyOfRange(start, if (first) start + 5 else end + 1))
                })
            }, chunkBytes = 16, pause = {})
            assertArrayEquals(data, target.readBytes())
            assertEquals("bytes=0-15", requests[0])
            assertEquals("bytes=5-20", requests[1])
        } finally { target.delete() }
    }

    @Test fun serverIgnoringRangeReplacesPartialDataRatherThanAppendingASecondApk() = runBlocking {
        val target = file().apply { writeBytes(data.copyOfRange(0, 12)) }
        try {
            ResumableApkTransfer.download(target, data.size.toLong(), open = { Reply(200, { "" }, { ByteArrayInputStream(data) }) }, chunkBytes = 16, pause = {})
            assertArrayEquals(data, target.readBytes())
        } finally { target.delete() }
    }

    @Test fun mismatchedRangeNeverCorruptsAnExistingPartialFile() = runBlocking {
        val partial = data.copyOfRange(0, 12)
        val target = file().apply { writeBytes(partial) }
        try {
            val failure = runCatching {
                ResumableApkTransfer.download(target, data.size.toLong(), open = { Reply(206, { "bytes 0-15/97" }, { ByteArrayInputStream(data) }) }, pause = {})
            }
            assertTrue(failure.isFailure)
            assertArrayEquals(partial, target.readBytes())
        } finally { target.delete() }
    }

    @Test fun repeatedNetworkFailuresAreBoundedAndCancellationIsNeverRetried() = runBlocking {
        val target = file(); var attempts = 0
        try {
            assertTrue(runCatching {
                ResumableApkTransfer.download(target, data.size.toLong(), open = {
                    attempts++
                    Reply(206, { "bytes 0-96/97" }, { ByteArrayInputStream(ByteArray(0)) })
                }, pause = {})
            }.isFailure)
            assertEquals(4, attempts)
            attempts = 0
            val cancelled = runCatching {
                ResumableApkTransfer.download(target, data.size.toLong(), open = { attempts++; throw CancellationException("cancel fixture") }, pause = {})
            }.exceptionOrNull()
            assertTrue(cancelled is CancellationException)
            assertEquals(1, attempts)
        } finally { target.delete() }
    }
}
