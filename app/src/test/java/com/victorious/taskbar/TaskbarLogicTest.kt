package com.victorious.taskbar

import com.victorious.taskbar.data.TaskbarLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskbarLogicTest {
    @Test fun moveWithinBounds() {
        assertEquals(listOf("b", "a", "c"), TaskbarLogic.move(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("c", "a", "b"), TaskbarLogic.move(listOf("a", "b", "c"), "c", -5))
        assertEquals(listOf("a", "b"), TaskbarLogic.move(listOf("a", "b"), "x", 1))
        assertEquals(listOf("a", "b"), TaskbarLogic.move(listOf("a", "b"), "b", 1))
    }

    @Test fun fuzzyRanking() {
        data class A(val l: String, val p: String)
        val apps = listOf(A("Google Maps", "com.google.maps"), A("Gmail", "com.google.gm"), A("Camera", "com.cam"), A("Settings", "com.android.settings"))
        assertEquals("Gmail", TaskbarLogic.rank(apps, "gm", { it.l }, { it.p }).first().l)
        assertEquals("Google Maps", TaskbarLogic.rank(apps, "maps", { it.l }, { it.p }).first().l)
        assertTrue(TaskbarLogic.rank(apps, "zzz", { it.l }, { it.p }).isEmpty())
        assertEquals(apps, TaskbarLogic.rank(apps, " ", { it.l }, { it.p }))
        assertTrue(TaskbarLogic.score("Settings", "x", "stg") > 0) // subsequence
    }

    @Test fun buckets() {
        assertEquals('C', TaskbarLogic.bucket("camera"))
        assertEquals('#', TaskbarLogic.bucket("1Password"))
        assertEquals('#', TaskbarLogic.bucket(""))
    }
}
