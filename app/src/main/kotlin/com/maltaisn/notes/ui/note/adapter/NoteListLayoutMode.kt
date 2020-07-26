/*
 * Copyright 2020 Nicolas Maltais
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

package com.maltaisn.notes.ui.note.adapter

/**
 * A list layout mode, with different appearance parameters.
 *
 * @property maxTextLines Maximum lines displayed for a text note.
 * @property maxListItems Maximum items displayed for a list note.
 */
enum class NoteListLayoutMode(
    val value: Int,
    val maxTextLines: Int,
    val maxListItems: Int
) {
    LIST(0, 5, 5),
    GRID(1, 10, 10)
}
