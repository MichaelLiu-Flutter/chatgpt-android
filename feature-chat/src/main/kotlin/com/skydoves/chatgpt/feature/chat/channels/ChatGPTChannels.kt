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

@file:OptIn(ExperimentalComposeUiApi::class)

package com.skydoves.chatgpt.feature.chat.channels

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.balloon.compose.Balloon
import com.skydoves.chatgpt.core.designsystem.component.ChatGPTLoadingIndicator
import com.skydoves.chatgpt.core.designsystem.composition.LocalOnFinishDispatcher
import com.skydoves.chatgpt.core.designsystem.theme.STREAM_PRIMARY
import com.skydoves.chatgpt.core.navigation.AppComposeNavigator
import com.skydoves.chatgpt.core.navigation.ChatGPTScreens
import com.skydoves.chatgpt.feature.chat.BuildConfig
import com.skydoves.chatgpt.feature.chat.R
import com.skydoves.chatgpt.feature.chat.theme.ChatGPTStreamTheme
import io.getstream.chat.android.compose.ui.channels.ChannelsScreen

@Composable
fun ChatGPTChannels(
  modifier: Modifier,
  composeNavigator: AppComposeNavigator,
  viewModel: ChatGPTChannelsViewModel? = null,
  onFinishDispatcher: (() -> Unit)? = LocalOnFinishDispatcher.current
) {
  val isStreamEnabled = BuildConfig.STREAM_API_KEY.isNotBlank() &&
    BuildConfig.STREAM_API_KEY != "aaaaaaaaaa"

  if (!isStreamEnabled) {
    BackHandler(enabled = true) { onFinishDispatcher?.invoke() }

    ChatGPTStreamTheme {
      Box(
        modifier = modifier
          .fillMaxSize()
          .semantics { testTagsAsResourceId = true }
      ) {
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = stringResource(id = R.string.local_mode_history_title),
            style = MaterialTheme.typography.titleMedium
          )
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = stringResource(id = R.string.local_mode_title),
                style = MaterialTheme.typography.titleSmall
              )
              Text(
                text = stringResource(id = R.string.local_mode_history_description),
                style = MaterialTheme.typography.bodySmall
              )
              Button(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                  composeNavigator.navigate(
                    ChatGPTScreens.Messages.createRoute(ChatGPTScreens.local_channel_id)
                  )
                }
              ) {
                Text(text = stringResource(id = R.string.local_mode_open_chat))
              }
            }
          }
        }
      }
    }
    return
  }

  val channelsViewModel: ChatGPTChannelsViewModel = viewModel ?: hiltViewModel()
  val uiState by channelsViewModel.channelUiState.collectAsStateWithLifecycle()

  HandleGPTChannelsUiState(uiState = uiState)

  ChatGPTStreamTheme {
    Box(
      modifier = modifier
        .fillMaxSize()
        .semantics { testTagsAsResourceId = true }
    ) {
      ChannelsScreen(
        isShowingHeader = false,
        onChannelClick = { channel ->
          composeNavigator.navigate(ChatGPTScreens.Messages.createRoute(channel.cid))
        },
        onBackPressed = { onFinishDispatcher?.invoke() }
      )

      val isBalloonDisplayed by channelsViewModel.isBalloonDisplayedState.collectAsStateWithLifecycle()

      Balloon(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .size(58.dp),
        builder = rememberFloatingBalloon(),
        balloonContent = {
          Text(
            modifier = Modifier
              .padding(12.dp)
              .fillMaxWidth(),
            text = "You can add your ChatGPT channel!",
            textAlign = TextAlign.Center,
            color = Color.White
          )
        }
      ) { balloonWindow ->

        LaunchedEffect(key1 = Unit) {
          if (!isBalloonDisplayed) {
            balloonWindow.showAlignTop()
          }

          balloonWindow.setOnBalloonDismissListener {
            channelsViewModel.balloonChannelDisplayed()
            balloonWindow.dismiss()
          }
        }

        FloatingActionButton(
          modifier = Modifier.matchParentSize(),
          containerColor = STREAM_PRIMARY,
          shape = CircleShape,
          onClick = { channelsViewModel.handleEvents(GPTChannelEvent.CreateChannel) }
        ) {
          Icon(
            imageVector = Icons.Filled.AddComment,
            contentDescription = null,
            tint = Color.White
          )
        }
      }

      if (uiState == GPTChannelUiState.Loading) {
        ChatGPTLoadingIndicator()
      }
    }
  }
}

@Composable
private fun HandleGPTChannelsUiState(
  uiState: GPTChannelUiState
) {
  val context = LocalContext.current
  LaunchedEffect(key1 = uiState) {
    when (uiState) {
      is GPTChannelUiState.Success -> Toast.makeText(
        context,
        R.string.toast_success_create_channel,
        Toast.LENGTH_SHORT
      ).show()

      is GPTChannelUiState.Error -> Toast.makeText(
        context,
        R.string.toast_error,
        Toast.LENGTH_SHORT
      ).show()

      else -> Unit
    }
  }
}
