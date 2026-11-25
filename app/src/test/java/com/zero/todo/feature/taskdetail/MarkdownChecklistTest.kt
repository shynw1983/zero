package com.zero.todo.feature.taskdetail

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownChecklistTest {
    @Test
    fun `only the selected checklist marker changes`() {
        val notes = "# Packing\r\n- [ ] Passport\r\n  - [X] Charger\r\n- Socks"
        val checked = "# Packing\r\n- [x] Passport\r\n  - [X] Charger\r\n- Socks"
        assertEquals(checked, toggleMarkdownChecklistItem(notes, 1))
        assertEquals(notes, toggleMarkdownChecklistItem(checked, 1))
        assertEquals(notes.replace("[X]", "[ ]"), toggleMarkdownChecklistItem(notes, 2))
        assertEquals(notes, toggleMarkdownChecklistItem(notes, 0))
        assertEquals(notes, toggleMarkdownChecklistItem(notes, 4))
    }
}
