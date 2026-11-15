package com.sukisu.ultra.core.utils

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

class DataSourceChannelTest {
    private fun channel(contentRange: String? = "bytes 2-4/8", body: String = "cde"): DataSourceChannel {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val probe = chain.request().header("Range") == "bytes=0-0"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .apply {
                    val range = if (probe) "bytes 0-0/8" else contentRange
                    if (range != null) header("Content-Range", range)
                }
                .body((if (probe) "a" else body).toResponseBody())
                .build()
        }.build()
        return DataSourceChannel(client, "https://example.test/ota")
    }

    @Test
    fun readsTheRequestedRange() {
        channel().use { source ->
            source.streamRead(2, 3).use { input ->
                assertArrayEquals("cde".toByteArray(), input.readBytes())
            }
        }
    }

    @Test
    fun rejectsMismatchedRangeStartEndAndTotal() {
        for (range in listOf("bytes 0-2/8", "bytes 2-3/8", "bytes 2-4/9", "invalid", null)) {
            channel(range).use { source ->
                assertThrows(IOException::class.java) { source.streamRead(2, 3).close() }
            }
        }
    }

    @Test
    fun boundsTheResponseToTheRequestedLength() {
        channel(body = "cdefgh").use { source ->
            source.streamRead(2, 3).use { input ->
                assertArrayEquals("cde".toByteArray(), input.readBytes())
            }
        }
    }

    @Test
    fun rejectsNegativeRangesAndOverflowingSlices() {
        channel().use { source ->
            assertThrows(IllegalArgumentException::class.java) { source.streamRead(-1, 1).close() }
            assertThrows(IllegalArgumentException::class.java) { source.streamRead(0, -1).close() }
            assertThrows(IllegalArgumentException::class.java) { source.slice(2, Long.MAX_VALUE) }
        }
    }

    @Test
    fun emptyReadsAtEofReturnZero() {
        channel().use { source ->
            source.position(8)
            assertEquals(0, source.read(ByteBuffer.allocate(0)))
            source.streamRead(8, 3).use { assertEquals(-1, it.read()) }
            source.streamRead(0, 0).use { assertEquals(-1, it.read()) }
        }
    }

    @Test
    fun closedChannelsRejectStreamsAndSlices() {
        val source = channel()
        source.close()
        assertThrows(ClosedChannelException::class.java) { source.streamRead(2, 3).close() }
        assertThrows(ClosedChannelException::class.java) { source.slice(0, 8) }
    }

    @Test
    fun slicesUseAbsoluteHttpRanges() {
        channel().use { source ->
            source.slice(2, 3).use { slice ->
                slice.streamRead(0, Long.MAX_VALUE).use { input ->
                    assertArrayEquals("cde".toByteArray(), input.readBytes())
                }
            }
        }
    }
}
