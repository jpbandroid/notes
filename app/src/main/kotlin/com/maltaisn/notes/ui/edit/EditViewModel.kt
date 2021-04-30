/*
 * Copyright 2021 Nicolas Maltais
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

package com.maltaisn.notes.ui.edit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.model.entity.BlankNoteMetadata
import com.maltaisn.notes.model.entity.Label
import com.maltaisn.notes.model.entity.LabelRef
import com.maltaisn.notes.model.entity.ListNoteMetadata
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteMetadata
import com.maltaisn.notes.model.entity.NoteStatus
import com.maltaisn.notes.model.entity.NoteType
import com.maltaisn.notes.model.entity.PinnedStatus
import com.maltaisn.notes.model.entity.Reminder
import com.maltaisn.notes.ui.AssistedSavedStateViewModelFactory
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.ShareData
import com.maltaisn.notes.ui.StatusChange
import com.maltaisn.notes.ui.edit.adapter.EditAdapter
import com.maltaisn.notes.ui.edit.adapter.EditContentItem
import com.maltaisn.notes.ui.edit.adapter.EditDateItem
import com.maltaisn.notes.ui.edit.adapter.EditItemAddItem
import com.maltaisn.notes.ui.edit.adapter.EditItemItem
import com.maltaisn.notes.ui.edit.adapter.EditItemLabelsItem
import com.maltaisn.notes.ui.edit.adapter.EditListItem
import com.maltaisn.notes.ui.edit.adapter.EditTitleItem
import com.maltaisn.notes.ui.edit.adapter.EditableText
import com.maltaisn.notes.ui.note.ShownDateField
import com.maltaisn.notes.ui.send
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Date

/**
 * View model for the edit note screen.
 * TODO status, pinned, reminder could be livedata, except that livedata has nullable type... flow?
 */
class EditViewModel @AssistedInject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefs: PrefsManager,
    private val alarmManager: ReminderAlarmManager,
    @Assisted private val savedStateHandle: SavedStateHandle,
) : ViewModel(), EditAdapter.Callback {

    /**
     * Whether the current note is a new note.
     * This is important to remember as to not recreate as new blank note
     * when [start] is called a second time.
     */
    private var isNewNote = false

    /**
     * Note being edited by user. This note data is not up-to-date with the UI.
     * - Call [updateNote] to update it to reflect UI state.
     * - Call [saveNote] to update it from UI and update database.
     */
    private var note = BLANK_NOTE

    /**
     * List of labels on note. Always reflects the UI.
     */
    private var labels = emptyList<Label>()

    /**
     * Status of the note being edited. This is separate from [note] so that
     * note status can be updated from this in [updateNote].
     */
    private var status = note.status

    /**
     * Whether the note being edited is pinned or not.
     */
    private var pinned = note.pinned

    /**
     * The reminder set on the note, or `null` if none is set.
     */
    private var reminder: Reminder? = null

    /**
     * The currently displayed list items created in [updateListItems].
     * While this list is mutable, any in place changes should be reported to the adapter!
     */
    private var listItems: MutableList<EditListItem> = mutableListOf()
        set(value) {
            field = value
            _editItems.value = value
        }

    private val _noteType = MutableLiveData<NoteType>()
    val noteType: LiveData<NoteType>
        get() = _noteType

    private val _noteStatus = MutableLiveData<NoteStatus>()
    val noteStatus: LiveData<NoteStatus>
        get() = _noteStatus

    private val _notePinned = MutableLiveData<PinnedStatus>()
    val notePinned: LiveData<PinnedStatus>
        get() = _notePinned

    private val _noteReminder = MutableLiveData<Reminder?>()
    val noteReminder: LiveData<Reminder?>
        get() = _noteReminder

    private val _editItems = MutableLiveData<List<EditListItem>>()
    val editItems: LiveData<List<EditListItem>>
        get() = _editItems

    private val _focusEvent = MutableLiveData<Event<FocusChange>>()
    val focusEvent: LiveData<Event<FocusChange>>
        get() = _focusEvent

    private val _messageEvent = MutableLiveData<Event<EditMessage>>()
    val messageEvent: LiveData<Event<EditMessage>>
        get() = _messageEvent

    private val _statusChangeEvent = MutableLiveData<Event<StatusChange>>()
    val statusChangeEvent: LiveData<Event<StatusChange>>
        get() = _statusChangeEvent

    private val _shareEvent = MutableLiveData<Event<ShareData>>()
    val shareEvent: LiveData<Event<ShareData>>
        get() = _shareEvent

    private val _showDeleteConfirmEvent = MutableLiveData<Event<Unit>>()
    val showDeleteConfirmEvent: LiveData<Event<Unit>>
        get() = _showDeleteConfirmEvent

    private val _showRemoveCheckedConfirmEvent = MutableLiveData<Event<Unit>>()
    val showRemoveCheckedConfirmEvent: LiveData<Event<Unit>>
        get() = _showRemoveCheckedConfirmEvent

    private val _showReminderDialogEvent = MutableLiveData<Event<Long>>()
    val showReminderDialogEvent: LiveData<Event<Long>>
        get() = _showReminderDialogEvent

    private val _showLabelsFragmentEvent = MutableLiveData<Event<Long>>()
    val showLabelsFragmentEvent: LiveData<Event<Long>>
        get() = _showLabelsFragmentEvent

    private val _exitEvent = MutableLiveData<Event<Unit>>()
    val exitEvent: LiveData<Event<Unit>>
        get() = _exitEvent

    /**
     * Whether to show date item.
     */
    private val shouldShowDate: Boolean
        get() = if (isNewNote) false else prefs.shownDateField != ShownDateField.NONE

    /**
     * Whether note is currently in trash (deleted) or not.
     */
    private val isNoteInTrash: Boolean
        get() = status == NoteStatus.DELETED

    init {
        if (KEY_NOTE_ID in savedStateHandle) {
            viewModelScope.launch {
                isNewNote = savedStateHandle[KEY_IS_NEW_NOTE] ?: false

                val note = notesRepository.getNoteById(savedStateHandle[KEY_NOTE_ID] ?: Note.NO_ID)
                if (note != null) {
                    this@EditViewModel.note = note
                }
            }
        }
    }

    /**
     * Initialize the view model to edit a note with the ID [noteId].
     * The view model can only be started once to edit a note.
     * Subsequent calls with different arguments will do nothing and previous note will be edited.
     *
     * @param noteId Can be [Note.NO_ID] to create a new blank note.
     * @param labelId Can be different from [Label.NO_ID] to initially set a label on a new note.
     */
    fun start(noteId: Long = Note.NO_ID, labelId: Long = Label.NO_ID) {
        viewModelScope.launch {
            val isFirstStart = (note == BLANK_NOTE)

            // Try to get note by ID with its labels.
            val noteWithLabels = notesRepository.getNoteByIdWithLabels(if (isFirstStart) {
                // first start, use provided note ID
                noteId
            } else {
                // start() was already called, fragment view was probably recreated
                // use the note ID of the note being edited previously
                note.id
            })

            var note = noteWithLabels?.note
            var labels = noteWithLabels?.labels

            if (note == null || labels == null) {
                // Note doesn't exist, create new blank text note.
                // This is the expected path for creating a new note (by passing Note.NO_ID)
                val date = Date()
                note = BLANK_NOTE.copy(addedDate = date, lastModifiedDate = date)
                val id = notesRepository.insertNote(note)
                note = note.copy(id = id)

                // If a label was passed to be initially set, use it.
                // Otherwise no labels will be set.
                val label = labelsRepository.getLabelById(labelId)
                labels = listOfNotNull(label)
                if (label != null) {
                    labelsRepository.insertLabelRefs(listOf(LabelRef(id, labelId)))
                }

                isNewNote = true
                savedStateHandle[KEY_IS_NEW_NOTE] = isNewNote
            }

            this@EditViewModel.note = note
            this@EditViewModel.labels = labels
            status = note.status
            pinned = note.pinned
            reminder = note.reminder

            _noteType.value = note.type
            _noteStatus.value = status
            _notePinned.value = pinned
            _noteReminder.value = reminder

            updateListItems()

            if (isFirstStart && isNewNote) {
                // Focus on text content
                focusItemAt(findItemPos<EditContentItem>(), 0, false)
            }
        }
    }

    /**
     * Update note and save it in database if it was changed.
     * This updates last modified date.
     */
    fun saveNote() {
        // Update note
        updateNote()

        // NonCancellable to avoid save being cancelled if called right before view model destruction
        viewModelScope.launch(NonCancellable) {
            // Compare previously saved note from database with new one.
            val oldNote = notesRepository.getNoteById(note.id)
            if (oldNote != note) {
                // Note was changed.
                note = note.copy(lastModifiedDate = Date())
                notesRepository.updateNote(note)
            }
        }
    }

    /**
     * Send exit event. If note is blank, it's discarded.
     */
    fun exit() {
        if (note.isBlank) {
            // Delete blank note
            viewModelScope.launch {
                notesRepository.deleteNote(note)
                _messageEvent.send(EditMessage.BLANK_NOTE_DISCARDED)
                _exitEvent.send()
            }
        } else {
            _exitEvent.send()
        }
    }

    fun toggleNoteType() {
        updateNote()

        // Convert note type
        note = when (note.type) {
            NoteType.TEXT -> note.asListNote()
            NoteType.LIST -> {
                if ((note.metadata as ListNoteMetadata).checked.any { it }) {
                    _showRemoveCheckedConfirmEvent.send()
                    return
                } else {
                    note.asTextNote(true)
                }
            }
        }
        _noteType.value = note.type

        // Update list items
        updateListItems()
    }

    fun togglePin() {
        pinned = when (pinned) {
            PinnedStatus.PINNED -> PinnedStatus.UNPINNED
            PinnedStatus.UNPINNED -> PinnedStatus.PINNED
            PinnedStatus.CANT_PIN -> error("Can't pin")
        }
        _notePinned.value = pinned
    }

    fun changeReminder() {
        _showReminderDialogEvent.send(note.id)
    }

    fun changeLabels() {
        _showLabelsFragmentEvent.send(note.id)
    }

    fun onReminderChange(reminder: Reminder?) {
        this.reminder = reminder
        _noteReminder.value = reminder
    }

    fun convertToText(keepCheckedItems: Boolean) {
        note = note.asTextNote(keepCheckedItems)
        _noteType.value = NoteType.TEXT

        // Update list items
        updateListItems()
    }

    fun moveNoteAndExit() {
        changeNoteStatusAndExit(if (status == NoteStatus.ACTIVE) {
            NoteStatus.ARCHIVED
        } else {
            NoteStatus.ACTIVE
        })
    }

    fun restoreNoteAndEdit() {
        note = note.copy(status = NoteStatus.ACTIVE, pinned = PinnedStatus.UNPINNED)

        status = note.status
        pinned = note.pinned
        _noteStatus.value = status
        _notePinned.value = pinned

        // Recreate list items so that they are editable.
        updateListItems()

        _messageEvent.send(EditMessage.RESTORED_NOTE)
    }

    fun copyNote(untitledName: String, copySuffix: String) {
        saveNote()

        viewModelScope.launch {
            val newTitle = Note.getCopiedNoteTitle(note.title, untitledName, copySuffix)

            if (!note.isBlank) {
                // If note is blank, don't make a copy, just change the title.
                // Copied blank note should be discarded anyway.
                val date = Date()
                val copy = note.copy(
                    id = Note.NO_ID,
                    title = newTitle,
                    addedDate = date,
                    lastModifiedDate = date,
                    reminder = reminder)
                val id = notesRepository.insertNote(copy)
                note = copy.copy(id = id)

                // Set reminder alarm for copy
                if (reminder != null) {
                    alarmManager.setNoteReminderAlarm(note)
                }

                // Set labels for copy
                labelsRepository.insertLabelRefs(createLabelRefs(id))
            }

            // Update title item
            findItem<EditTitleItem>().title.replaceAll(newTitle)
            focusItemAt(findItemPos<EditTitleItem>(), newTitle.length, true)
        }
    }

    fun shareNote() {
        updateNote()
        _shareEvent.send(ShareData(note.title, note.asText()))
    }

    fun deleteNote() {
        if (isNoteInTrash) {
            // Delete forever, ask for confirmation.
            _showDeleteConfirmEvent.send()
        } else {
            // Send to trash
            changeNoteStatusAndExit(NoteStatus.DELETED)
        }
    }

    fun deleteNoteForeverAndExit() {
        viewModelScope.launch {
            notesRepository.deleteNote(note)
        }
        exit()
    }

    fun uncheckAllItems() {
        changeListItems { list ->
            for ((i, item) in list.withIndex()) {
                if (item is EditItemItem && item.checked) {
                    list[i] = item.copy(checked = false)
                }
            }
        }
    }

    fun deleteCheckedItems() {
        changeListItems { list ->
            list.removeAll { it is EditItemItem && it.checked }
        }
    }

    private fun changeNoteStatusAndExit(newStatus: NoteStatus) {
        updateNote()

        if (!note.isBlank) {
            // If note is blank, it will be discarded on exit anyway, so don't change it.
            val oldNote = note
            status = newStatus

            pinned = if (status == NoteStatus.ACTIVE) {
                PinnedStatus.UNPINNED
            } else {
                PinnedStatus.CANT_PIN
            }

            if (newStatus == NoteStatus.DELETED) {
                // Remove reminder for deleted note
                if (reminder != null) {
                    reminder = null
                    alarmManager.removeAlarm(note.id)
                }
            }

            saveNote()

            // Show status change message.
            val statusChange = StatusChange(listOf(oldNote), oldNote.status, newStatus)
            _statusChangeEvent.send(statusChange)
        }

        exit()
    }

    /**
     * Update [note] to reflect UI changes, like text changes.
     * Note is not updated in database and last modified date isn't changed.
     */
    private fun updateNote() {
        // Create note
        val title = findItem<EditTitleItem>().title.text.toString()
        val content: String
        val metadata: NoteMetadata
        when (note.type) {
            NoteType.TEXT -> {
                content = findItem<EditContentItem>().content.text.toString()
                metadata = BlankNoteMetadata
            }
            NoteType.LIST -> {
                val items = listItems.filterIsInstance<EditItemItem>()
                content = items.joinToString("\n") { it.content.text }
                metadata = ListNoteMetadata(items.map { it.checked })
            }
        }
        note = note.copy(title = title, content = content,
            metadata = metadata, status = status, pinned = pinned, reminder = reminder)
    }

    /**
     * Create label refs for a note ID from [labels].
     */
    private fun createLabelRefs(noteId: Long) =
        labels.map { LabelRef(noteId, it.id) }

    private fun createListItems() = mutableListOf<EditListItem>().apply {
        val canEdit = !isNoteInTrash

        // Date item
        if (shouldShowDate) {
            this += EditDateItem(when (prefs.shownDateField) {
                ShownDateField.ADDED -> note.addedDate.time
                ShownDateField.MODIFIED -> note.lastModifiedDate.time
                else -> 0L  // never happens
            })
        }

        // Title item
        val titleItem = EditTitleItem(DefaultEditableText(note.title), canEdit)
        this += titleItem

        when (note.type) {
            NoteType.TEXT -> {
                // Content item
                val contentItem = EditContentItem(DefaultEditableText(note.content), canEdit)
                this += contentItem
            }
            NoteType.LIST -> {
                // List items
                val items = note.listItems
                for (item in items) {
                    this += EditItemItem(DefaultEditableText(item.content),
                        item.checked,
                        canEdit)
                }

                // Item add item
                if (canEdit) {
                    this += EditItemAddItem
                }
            }
        }

        if (labels.isNotEmpty()) {
            this += EditItemLabelsItem(labels)
        }
    }

    /**
     * Update list items to match the [note] data.
     * [updateNote] might need to be called first for that data to be up-to-date.
     */
    private fun updateListItems() {
        // TODO avoid full list refresh somehow?
        //  - on note type change
        //  - on fragment view recreation
        listItems = createListItems()
    }

    override fun onNoteItemChanged(item: EditItemItem, pos: Int, isPaste: Boolean) {
        if ('\n' in item.content.text) {
            // User inserted line breaks in list items, split it into multiple items.
            val lines = item.content.text.split('\n')
            item.content.replaceAll(lines.first())
            changeListItems { list ->
                for (i in 1 until lines.size) {
                    list.add(pos + i, EditItemItem(DefaultEditableText(lines[i]),
                        checked = false, editable = true))
                }
            }

            // If text was pasted, set focus at the end of last items pasted.
            // If a single linebreak was inserted, focus on the new item.
            focusItemAt(pos + lines.size - 1, if (isPaste) lines.last().length else 0, false)
        }
    }

    override fun onNoteItemBackspacePressed(item: EditItemItem, pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Previous item is also a note list item. Merge the two items content,
            // and delete the current item.
            val prevText = prevItem.content
            val prevLength = prevText.text.length
            prevText.append(item.content.text)
            changeListItems { it.removeAt(pos) }

            // Set focus on merge boundary.
            focusItemAt(pos - 1, prevLength, true)
        }
    }

    override fun onNoteItemDeleteClicked(pos: Int) {
        val prevItem = listItems[pos - 1]
        if (prevItem is EditItemItem) {
            // Set focus at the end of previous item.
            focusItemAt(pos - 1, prevItem.content.text.length, true)
        } else {
            val nextItem = listItems[pos + 1]
            if (nextItem is EditItemItem) {
                // Set focus at the end of next item.
                focusItemAt(pos + 1, nextItem.content.text.length, true)
            }
        }

        // Delete item in list.
        changeListItems { it.removeAt(pos) }
    }

    override fun onNoteItemAddClicked(pos: Int) {
        // pos is the position of EditItemAdd item, which is also the position to insert the new item.
        changeListItems { list ->
            list.add(pos, EditItemItem(DefaultEditableText(), checked = false, editable = true))
        }
        focusItemAt(pos, 0, false)
    }

    override fun onNoteLabelClicked() {
        changeLabels()
    }

    override fun onNoteClickedToEdit() {
        if (isNoteInTrash) {
            // Cannot edit note in trash! Show message suggesting user to restore the note.
            // This is not just for show. Editing note would change its last modified date
            // which would mess up the auto-delete interval in trash.
            _messageEvent.send(EditMessage.CANT_EDIT_IN_TRASH)
        }
    }

    override val isNoteDragEnabled: Boolean
        get() = !isNoteInTrash && listItems.count { it is EditItemItem } > 1

    override fun onNoteItemSwapped(from: Int, to: Int) {
        // Avoid updating live data, adapter was notified of the change already.
        Collections.swap(listItems, from, to)
    }

    override val strikethroughCheckedItems: Boolean
        get() = prefs.strikethroughChecked

    private fun focusItemAt(pos: Int, textPos: Int, itemExists: Boolean) {
        _focusEvent.send(FocusChange(pos, textPos, itemExists))
    }

    private inline fun changeListItems(change: (MutableList<EditListItem>) -> Unit) {
        val newList = listItems.toMutableList()
        change(newList)
        listItems = newList
    }

    private inline fun <reified T : EditListItem> findItem(): T {
        return (listItems.find { it is T } ?: error("List item not found")) as T
    }

    private inline fun <reified T : EditListItem> findItemPos(): Int {
        return listItems.indexOfFirst { it is T }
    }

    data class FocusChange(val itemPos: Int, val pos: Int, val itemExists: Boolean)

    /**
     * The default class used for editable item text, backed by StringBuilder.
     * When items are bound by the adapter, this is changed to AndroidEditableText instead.
     * The default implementation is only used temporarily (before item is bound) and for testing.
     */
    class DefaultEditableText(text: CharSequence = "") : EditableText {
        override val text = StringBuilder(text)

        override fun append(text: CharSequence) {
            this.text.append(text)
        }

        override fun replaceAll(text: CharSequence) {
            this.text.replace(0, this.text.length, text.toString())
        }

        override fun equals(other: Any?) = (other is DefaultEditableText &&
                other.text.toString() == text.toString())

        override fun hashCode() = text.hashCode()

        override fun toString() = text.toString()
    }

    @AssistedInject.Factory
    interface Factory : AssistedSavedStateViewModelFactory<EditViewModel> {
        override fun create(savedStateHandle: SavedStateHandle): EditViewModel
    }

    companion object {
        private val BLANK_NOTE = Note(Note.NO_ID, NoteType.TEXT, "", "",
            BlankNoteMetadata, Date(0), Date(0), NoteStatus.ACTIVE, PinnedStatus.UNPINNED, null)

        private const val KEY_NOTE_ID = "noteId"
        private const val KEY_IS_NEW_NOTE = "isNewNote"
    }
}
