package com.skydoves.chatgpt.core.model.network

import com.skydoves.chatgpt.core.model.GPTChoice
import com.skydoves.chatgpt.core.model.GPTMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class GPTChatResponseTest {

  @Test
  fun extractAssistantText_prefersOutputMessageTextAndContent_overChoices() {
    val response = GPTChatResponse(
      choices = listOf(
        GPTChoice(
          index = 0,
          message = GPTMessage(role = "assistant", content = "fallback"),
          logProbs = null,
          finishReason = null,
        )
      ),
      output = listOf(
        GPTResponseOutput(
          type = "message",
          role = null, // accepted when type == "message"
          text = "  direct  ",
          content = listOf(
            GPTResponseOutputContent(text = "  first "),
            GPTResponseOutputContent(text = ""),
            GPTResponseOutputContent(text = "second"),
          ),
        )
      )
    )

    assertEquals("first\nsecond\ndirect", response.extractAssistantText())
  }

  @Test
  fun extractAssistantText_fallsBackToChoicesWhenNoAssistantOutputFound() {
    val response = GPTChatResponse(
      choices = listOf(
        GPTChoice(
          index = 0,
          message = GPTMessage(role = "assistant", content = "from choices"),
          logProbs = null,
          finishReason = null,
        )
      ),
      output = listOf(
        GPTResponseOutput(type = "reasoning", text = "not assistant message")
      )
    )

    assertEquals("from choices", response.extractAssistantText())
  }

  @Test
  fun extractReasoningText_collectsSummaryContentAndText_andTrimsAndJoins() {
    val response = GPTChatResponse(
      output = listOf(
        GPTResponseOutput(
          type = "reasoning",
          summary = listOf(
            GPTResponseOutputSummary(text = "  s1 "),
            GPTResponseOutputSummary(text = ""),
          ),
          content = listOf(
            GPTResponseOutputContent(text = "  c1 "),
            GPTResponseOutputContent(text = "c2"),
          ),
          text = "  t1  ",
        ),
        GPTResponseOutput(
          type = "reasoning",
          text = "t2",
        ),
      )
    )

    assertEquals("s1\nc1\nc2\nt1\nt2", response.extractReasoningText())
  }
}

