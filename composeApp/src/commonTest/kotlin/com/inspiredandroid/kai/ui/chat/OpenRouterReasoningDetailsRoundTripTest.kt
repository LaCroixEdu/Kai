package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.data.ReasoningRequestMode
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.providers.buildOpenAIMessages
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

    @Test
    fun `OpenRouter drops encrypted reasoning after model switch`() {
        val assistant = decodeAssistantHistory().copy(reasoningModelId = "x-ai/grok-4.6")
        val toolResult = History(
            role = History.Role.TOOL,
            content = "{\"year\":2026}",
            toolCallId = "call_1",
            toolName = "get_local_time",
        )

        val encodedAssistant = buildOpenAIMessages(
            service = Service.OpenRouter,
            messages = listOf(assistant, toolResult),
            systemPrompt = null,
            modelId = "google/gemini-3.7-flash",
            declaredToolNames = setOf("get_local_time"),
        ).first().let {
            json.encodeToJsonElement(OpenAICompatibleChatRequestDto.Message.serializer(), it).jsonObject
        }

        assertFalse("reasoning_details" in encodedAssistant)
        assertEquals("human-readable trace", encodedAssistant["reasoning_content"]?.toString()?.trim('"'))
    }
}
