package com.joker.coolmall.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ResultExtTest {

    @Test
    fun `asResult emits loading before success`() = runBlocking {
        val results = flowOf("payload").asResult().toList()

        assertEquals(listOf(Result.Loading, Result.Success("payload")), results)
    }

    @Test
    fun `asResult converts regular exception to error`() = runBlocking {
        val expected = IllegalStateException("broken")

        val results = flow<String> { throw expected }.asResult().toList()

        assertEquals(Result.Loading, results[0])
        assertSame(expected, (results[1] as Result.Error).exception)
    }

    @Test
    fun `asResult propagates cancellation`() = runBlocking {
        val expected = CancellationException("cancelled")

        try {
            flow<String> { throw expected }.asResult().toList()
            fail("CancellationException should be propagated")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
