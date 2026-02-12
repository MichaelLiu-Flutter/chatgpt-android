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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
  var input by remember { mutableStateOf("") }

  ChatGPTStreamTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(text = stringResource(id = R.string.local_mode_title), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onBackPressed) {
          Text(text = stringResource(id = R.string.local_mode_back))
        }
      }

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(messages) { message ->
          val prefix = if (message.role == "user") "You" else "GPT"
          val content = message.content.ifBlank {
            stringResource(id = R.string.local_mode_empty_response)
          }
          Text(
            text = "$prefix: $content",
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          modifier = Modifier.weight(1f),
          value = input,
          onValueChange = { input = it },
          enabled = !sending,
          placeholder = { Text(stringResource(id = R.string.local_mode_input_placeholder)) }
        )
        Button(
          enabled = input.isNotBlank() && !sending,
          onClick = {
            viewModel.sendMessage(input)
            input = ""
          }
        ) {
          if (sending) {
            CircularProgressIndicator(
              modifier = Modifier.padding(2.dp),
              strokeWidth = 2.dp
            )
          } else {
            Text(text = stringResource(id = R.string.local_mode_send))
          }
        }
      }
    }
  }
}
