/*
 * Copyright 2023 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.errortek.notes.ui

import com.errortek.notes.R
import com.errortek.notes.model.ValueEnum
import com.errortek.notes.model.findValueEnum

/**
 * Enum for different app themes.
 * [value] is from [R.array.pref_theme_values].
 */
enum class AppTheme(override val value: String) : ValueEnum<String> {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromValue(value: String): AppTheme = findValueEnum(value)
    }
}
