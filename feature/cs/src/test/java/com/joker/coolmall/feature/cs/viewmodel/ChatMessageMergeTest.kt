package com.joker.coolmall.feature.cs.viewmodel

import com.joker.coolmall.core.model.entity.CsMsg
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageMergeTest {

    @Test
    fun `first page keeps live messages received during request`() {
        val result = mergeFirstPageMessages(
            currentMessages = messages(12, 11, 10),
            messageIdsAtRequestStart = setOf(10),
            pageMessages = messages(11, 10, 9),
        )

        assertEquals(listOf(12L, 11L, 10L, 9L), result.map(CsMsg::id))
    }

    @Test
    fun `first page removes stale messages that existed before request`() {
        val result = mergeFirstPageMessages(
            currentMessages = messages(12, 10, 8),
            messageIdsAtRequestStart = setOf(10, 8),
            pageMessages = messages(10, 9),
        )

        assertEquals(listOf(12L, 10L, 9L), result.map(CsMsg::id))
    }

    @Test
    fun `older page appends in order without duplicate ids`() {
        val result = appendOlderMessages(
            currentMessages = messages(10, 9),
            pageMessages = messages(9, 8, 8, 7),
        )

        assertEquals(listOf(10L, 9L, 8L, 7L), result.map(CsMsg::id))
    }

    @Test
    fun `first page keeps live version when response contains same id`() {
        val liveMessage = CsMsg(id = 12, nickName = "live")
        val responseMessage = CsMsg(id = 12, nickName = "response")

        val result = mergeFirstPageMessages(
            currentMessages = listOf(liveMessage),
            messageIdsAtRequestStart = emptySet(),
            pageMessages = listOf(responseMessage, CsMsg(id = 11)),
        )

        assertEquals(listOf(12L, 11L), result.map(CsMsg::id))
        assertEquals("live", result.first().nickName)
    }

    @Test
    fun `empty older page keeps current messages unchanged`() {
        val currentMessages = messages(10, 9)

        val result = appendOlderMessages(
            currentMessages = currentMessages,
            pageMessages = emptyList(),
        )

        assertEquals(currentMessages, result)
    }

    private fun messages(vararg ids: Long): List<CsMsg> = ids.map { id ->
        CsMsg(id = id)
    }
}
