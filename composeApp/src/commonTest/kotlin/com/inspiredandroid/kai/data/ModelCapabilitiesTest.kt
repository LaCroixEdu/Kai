package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertTrue

class ModelCapabilitiesTest {

    @Test
    fun `DeepSeek vision model accepts image attachments`() {
        assertTrue(modelSupportsImages("deepseek/deepseek-v4-flash-vision-exp"))
    }
}
