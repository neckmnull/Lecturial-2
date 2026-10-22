package com.rassvet.lecturial.model

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every room the schedule site lists (collected from its room search, site_rooms.txt) must
 * come out in the one spelling: no doubled text, no campus suffix, no site codes glued to
 * the name, and building and К_ rooms in their own categories.
 */
class SiteRoomsTest {
    private val siteRooms: List<String> = javaClass.classLoader!!.getResource("site_rooms.txt")!!
        .readText(Charsets.UTF_8).lines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `every room on the site gets a clean name`() {
        val dump = StringBuilder()
        for (site in siteRooms) {
            val room = normalizeRoom(site)
            dump.append(site).append("\t→\t").append(room).append('\n')
            if (room.kind == RoomKind.Online) continue
            val name = room.name
            assertTrue(site, name.isNotBlank())
            assertFalse("$site → $name", "(Основной)" in name || "(Университет)" in name)
            assertFalse("$site → $name", Regex("""^\d+-\d+-\d+""").containsMatchIn(name))
            val half = name.length / 2
            assertFalse("$site → $name", name.length >= 8 && name.substring(0, half).equals(name.substring(half), ignoreCase = true))
            if (Regex("""(?iu)^(?:\d[\d\-.\s]*)?\.?(альфа|бета|гамма|дельта)\s*\d""").containsMatchIn(site)) {
                assertTrue("$site → $room", room.category.startsWith("Корпус"))
            }
            if (Regex("""^\d[\d.,\-]*\.?\s*К_|^К_\d""").containsMatchIn(site)) {
                assertTrue("$site → $room", room.category == RoomCategories.K)
            }
        }
        // After merging, a К_ room never appears both with and without its number.
        val counted = mergedRooms(siteRooms.map(::normalizeRoom)).values.map { it.name }.toSet()
        val numbered = counted.filter { ", ауд. " in it }.map { it.substringBefore(", ауд. ") }.toSet()
        counted.filter { it.startsWith("К_") && ", ауд. " !in it }.forEach { assertFalse(it, it in numbered) }
        System.getenv("LECTURIAL_ROOMS_DUMP")?.let { File(it).writeText(dump.toString(), Charsets.UTF_8) }
    }
}
