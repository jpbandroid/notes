/*
 * Copyright 2022 Nicolas Maltais
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

package com.errortek.notes.model.entity

import com.errortek.notes.model.ValueEnum
import com.errortek.notes.model.findValueEnum

enum class NoteType(override val value: Int) : ValueEnum<Int> {
    TEXT(0),
    LIST(1);

    companion object {
        fun fromValue(value: Int): NoteType = findValueEnum(value)
    }
}
