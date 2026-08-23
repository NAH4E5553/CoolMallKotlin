package com.joker.coolmall.result

import com.joker.coolmall.core.model.response.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultHandlerTest {

    @Test
    fun `handleResult accepts successful response without data`() = runBlocking {
        var successCount = 0
        var errorMessage: String? = null

        ResultHandler.handleResult(
            scope = this,
            flow = flowOf(Result.Success(NetworkResponse<Unit>())),
            showToast = false,
            onSuccess = { successCount++ },
            onError = { message, _ -> errorMessage = message },
        ).join()

        assertEquals(1, successCount)
        assertNull(errorMessage)
    }

    @Test
    fun `handleResult rejects business failure`() = runBlocking {
        var successCalled = false
        var errorMessage: String? = null

        ResultHandler.handleResult(
            scope = this,
            flow = flowOf(
                Result.Success(
                    NetworkResponse(
                        data = "ignored",
                        code = 400,
                        message = "denied",
                    ),
                ),
            ),
            showToast = false,
            onSuccess = { successCalled = true },
            onError = { message, _ -> errorMessage = message },
        ).join()

        assertFalse(successCalled)
        assertEquals("denied", errorMessage)
    }

    @Test
    fun `handleResultWithData rejects successful response with null data`() = runBlocking {
        var dataCalled = false
        var errorMessage: String? = null

        ResultHandler.handleResultWithData(
            scope = this,
            flow = flowOf(Result.Success(NetworkResponse<String>())),
            showToast = false,
            onData = { dataCalled = true },
            onError = { message, _ -> errorMessage = message },
        ).join()

        assertFalse(dataCalled)
        assertEquals("响应数据为空", errorMessage)
    }

    @Test
    fun `handleResult propagates cancellation without reporting business error`() = runBlocking {
        var errorCalled = false
        var finallyCount = 0

        val job = ResultHandler.handleResult<String>(
            scope = this,
            flow = flow { throw CancellationException("cancelled") },
            showToast = false,
            onError = { _, _ -> errorCalled = true },
            onFinally = { finallyCount++ },
        )
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(errorCalled)
        assertEquals(1, finallyCount)
    }
}
