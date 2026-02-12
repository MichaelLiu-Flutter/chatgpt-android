/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.skydoves.chatgpt.feature.chat.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.chatgpt.feature.chat.R
import com.skydoves.chatgpt.feature.chat.theme.ChatGPTStreamTheme

@Composable
fun LocalChatGPTMessages(
  onBackPressed: () -> Unit,
  viewModel: LocalChatViewModel = hiltViewModel()
) {
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val sending by viewModel.sending.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  val emptyResponse = stringResource(id = R.string.local_mode_empty_response)

  var input by rememberSaveable { mutableStateOf("") }

  fun onSendMessage() {
    if (input.isBlank() || sending) return
    viewModel.sendMessage(input)
    input = ""
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  ChatGPTStreamTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      LocalChatHeader(
        onBackPressed = onBackPressed,
        sending = sending,
        messageCount = messages.size
      )

      if (messages.isEmpty()) {
        LocalChatEmptyState(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
        )
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
          contentPadding = PaddingValues(vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          itemsIndexed(
            items = messages,
            key = { index, message -> "$index-${message.role}-${message.content.hashCode()}" }
          ) { index, message ->
            LocalChatMessageBubble(
              index = index,
              message = message,
              emptyResponse = emptyResponse
            )
          }
        }
      }

      LocalInputSection(
        input = input,
        sending = sending,
        onInputChange = { input = it },
        onSendClick = ::onSendMessage
      )
    }
  }
}

@Composable
private fun LocalChatHeader(
  onBackPressed: () -> Unit,
  sending: Boolean,
  messageCount: Int
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 2.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = stringResource(id = R.string.local_mode_title),
          style = MaterialTheme.typography.titleMedium
        )
        Text(
          text = if (sending) {
            stringResource(id = R.string.local_mode_subtitle_sending)
          } else {
            stringResource(
              id = R.string.local_mode_subtitle_ready,
              messageCount
            )
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      TextButton(onClick = onBackPressed) {
        Text(text = stringResource(id = R.string.local_mode_back))
      }
    }
  }
}

@Composable
private fun LocalChatEmptyState(modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = stringResource(id = R.string.local_mode_empty_title),
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = stringResource(id = R.string.local_mode_empty_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
private fun LocalChatMessageBubble(
  index: Int,
  message: LocalChatMessage,
  emptyResponse: String
) {
  val isUser = message.role == USER_ROLE
  val parsedMessage = remember(message.role, message.content, message.reasoning, emptyResponse) {
    ParsedLocalMessage(
      answer = message.content.trim().ifBlank { emptyResponse },
      reasoning = message.reasoning?.trim()?.ifBlank { null }
    )
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    Column(
      modifier = Modifier.widthIn(max = 320.dp),
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
      Text(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        text = if (isUser) {
          stringResource(id = R.string.local_mode_user_label)
        } else {
          stringResource(id = R.string.local_mode_assistant_label)
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Surface(
        shape = RoundedCornerShape(
          topStart = if (isUser) 18.dp else 6.dp,
          topEnd = if (isUser) 6.dp else 18.dp,
          bottomStart = 18.dp,
          bottomEnd = 18.dp
        ),
        color = if (isUser) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceVariant
        }
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = parsedMessage.answer,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
          if (!isUser && !parsedMessage.reasoning.isNullOrBlank()) {
            ReasoningSection(
              index = index,
              reasoning = parsedMessage.reasoning
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ReasoningSection(
  index: Int,
  reasoning: String
) {
  var expanded by rememberSaveable(index, reasoning) { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    TextButton(
      modifier = Modifier.align(Alignment.Start),
      contentPadding = PaddingValues(0.dp),
      onClick = { expanded = !expanded }
    ) {
      Text(
        text = if (expanded) {
          stringResource(id = R.string.local_mode_reasoning_hide)
        } else {
          stringResource(id = R.string.local_mode_reasoning_show)
        },
        style = MaterialTheme.typography.labelMedium
      )
    }

    AnimatedVisibility(visible = expanded) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = stringResource(id = R.string.local_mode_reasoning_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = reasoning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun LocalInputSection(
  input: String,
  sending: Boolean,
  onInputChange: (String) -> Unit,
  onSendClick: () -> Unit
) {
  Surface(tonalElevation = 3.dp) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        modifier = Modifier.weight(1f),
        value = input,
        onValueChange = onInputChange,
        enabled = !sending,
        maxLines = 4,
        placeholder = {
          Text(text = stringResource(id = R.string.local_mode_input_placeholder))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = {
            if (input.isNotBlank() && !sending) {
              onSendClick()
            }
          }
        )
      )
      Button(
        enabled = input.isNotBlank() && !sending,
        onClick = onSendClick
      ) {
        if (sending) {
          CircularProgressIndicator(
            modifier = Modifier
              .size(18.dp)
              .padding(2.dp),
            strokeWidth = 2.dp
          )
        } else {
          Text(text = stringResource(id = R.string.local_mode_send))
        }
      }
    }
  }
}

private data class ParsedLocalMessage(
  val answer: String,
  val reasoning: String?
)

private const val USER_ROLE = "user"
