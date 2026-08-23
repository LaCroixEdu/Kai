package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.data.ReasoningRequestMode
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenRouterReasoningDetailsRoundTripTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val expectedDetails = json.parseToJsonElement(
        """
        [
          {
            "type": "reasoning.encrypted",
            "format": "provider-specific",
            "signature": "opaque-signature"
          },
          {
            "type": "reasoning.text",
            "text": "opaque-text-fragment"
          }
        ]
        """.trimIndent(),
    ).jsonArray

    private fun decodeAssistantHistory(): History {
        val response = json.decodeFromString<OpenAICompatibleChatResponseDto>(
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": null,
                    "reasoning": "human-readable trace",
                    "reasoning_details": $expectedDetails,
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "get_local_time",
                          "arguments": "{}"
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val message = response.choices.single().message!!
        val toolCall = message.toolCalls!!.single()
        return History(
            role = History.Role.ASSISTANT,
            content = "",
            isThinking = true,
            toolCalls = persistentListOf(
                ToolCallInfo(
                    id = toolCall.id,
                    name = toolCall.function.name,
                    arguments = toolCall.function.arguments,
                ),
            ),
            reasoningContent = message.effectiveReasoning,
            reasoningDetails = message.reasoningDetails,
        )
    }

    @Test
    fun `OpenRouter reasoning_details survive response to next tool request`() {
        val requestMessage = decodeAssistantHistory().toGroqMessageDto(
            ReasoningRequestMode.REASONING_CONTENT_AND_DETAILS,
        )

        val encoded = json.encodeToJsonElement(
            OpenAICompatibleChatRequestDto.Message.serializer(),
            requestMessage,
        ).jsonObject

        assertEquals(expectedDetails, encoded["reasoning_details"])
        assertEquals("human-readable trace", requestMessage.reasoningContent)
    }

    @Test
    fun `strict provider mode omits reasoning_details`() {
        val requestMessage = decodeAssistantHistory().toGroqMessageDto(ReasoningRequestMode.NONE)

        val encoded = json.encodeToJsonElement(
            OpenAICompatibleChatRequestDto.Message.serializer(),
            requestMessage,
        ).jsonObject

        assertFalse("reasoning_details" in encoded)
    }
}
