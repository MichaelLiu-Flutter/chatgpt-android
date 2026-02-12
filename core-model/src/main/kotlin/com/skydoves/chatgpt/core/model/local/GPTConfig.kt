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

package com.skydoves.chatgpt.core.model.local

data class GPTConfig(
  val id: String,
  val name: String,
  val baseUrl: String,
  val apiKey: String,
  val isDefault: Boolean = false
)

object GPTConfigPreferencesKeys {
  const val KEY_GPT_CONFIGS = "key_gpt_configs"
  const val KEY_ACTIVE_GPT_CONFIG_ID = "key_active_gpt_config_id"
  const val KEY_ACTIVE_GPT_BASE_URL = "key_active_gpt_base_url"
  const val KEY_ACTIVE_GPT_API_KEY = "key_active_gpt_api_key"

  const val DEFAULT_CONFIG_ID = "default_build_config"
  const val DEFAULT_CONFIG_NAME = "Default"
}
