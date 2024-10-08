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

/**
 * Describes how a note or a group of notes are pinned.
 */
enum class PinnedStatus(override val value: Int) : ValueEnum<Int> {
    CANT_PIN(0),
    UNPINNED(1),
    PINNED(2);

    companion object {
        fun fromValue(value: Int): PinnedStatus = findValueEnum(value)
    }
}
