package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.FunctionCall
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.ToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAIContextTrimmingTest {

    @Test
    fun `oversized opaque reasoning block trims the entire tool turn`() {
        val opaqueDetails = JsonArray(
            listOf(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("reasoning.encrypted"),
                        "data" to JsonPrimitive("x".repeat(1_024)),
                        "signature" to JsonPrimitive("provider-owned-signature"),
                    ),
                ),
            ),
        )
        val assistantToolTurn = Message(
            role = "assistant",
            tool_calls = listOf(
                ToolCall(
                    id = "call_search",
                    function = FunctionCall(name = "search", arguments = "{\"query\":\"weather\"}"),
                ),
            ),
            reasoningDetails = opaqueDetails,
        )
        val toolResult = Message(
            role = "tool",
            content = JsonPrimitive("sunny"),
            tool_call_id = "call_search",
        )
        val latestUserMessage = Message(role = "user", content = JsonPrimitive("and tomorrow?"))

        val result = trimOpenAIMessagesForContext(
            messages = listOf(assistantToolTurn, toolResult, latestUserMessage),
            contextWindowTokens = 64,
        )

        assertEquals(listOf(latestUserMessage), result)
    }
}
