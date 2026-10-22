package com.rassvet.lecturial.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomNamesTest {
    private fun check(site: String, name: String, category: String, kind: RoomKind, code: String? = null, note: String? = null) {
        assertEquals(site, RoomInfo(name, category, kind, code, note), normalizeRoom(site))
    }

    @Test
    fun `building rooms read as building and number`() {
        check("1-14-19.Альфа 4.1 (Основной)", "Альфа 4.1", "Корпус «Альфа»", RoomKind.Classroom, code = "1-14-19")
        check("Альфа 5.7Альфа 5.7 (Основной)", "Альфа 5.7", "Корпус «Альфа»", RoomKind.Classroom)
        check("Альфа 5.12.1Альфа 5.12.1 Финтех (комп) (Основной)", "Альфа 5.12.1", "Корпус «Альфа»", RoomKind.Computer, note = "Финтех")
        check("1-07-17 1-07-18Бета 3.3+3.4 (комп) (Основной)", "Бета 3.3 + 3.4", "Корпус «Бета»", RoomKind.Computer, code = "1-07-17, 1-07-18")
        check("1-07-02Дельта 2.3 (комп) (Основной)", "Дельта 2.3", "Корпус «Дельта»", RoomKind.Computer, code = "1-07-02")
        check("лаб. Альфа 2.3", "Альфа 2.3", "Корпус «Альфа»", RoomKind.Classroom)
    }

    @Test
    fun `К_ rooms read as label and room number`() {
        check("1,26К_9 (Основной)", "К_9, ауд. 1.26", "Аудитории «К_»", RoomKind.Classroom)
        check("1,28К_7 (Комп) (Основной)", "К_7, ауд. 1.28", "Аудитории «К_»", RoomKind.Computer)
        check("К_12К_12 (Основной)", "К_12", "Аудитории «К_»", RoomKind.Classroom)
        check("19-3К_19 (Основной)", "К_19, ауд. 19-3", "Аудитории «К_»", RoomKind.Classroom)
        check("1.10.1.10. К_Гринатом (Основной)", "К_Гринатом, ауд. 1.10", "Аудитории «К_»", RoomKind.Classroom)
        check("1.081.08 К_Росатом (СПД) (Основной)", "К_Росатом, ауд. 1.08", "Аудитории «К_»", RoomKind.Classroom, note = "СПД")
        check("1.35К_0 (стартап-лаборатория ИНТЦ) (Основной)", "К_0, ауд. 1.35", "Аудитории «К_»", RoomKind.Classroom, note = "стартап-лаборатория ИНТЦ")
        check("1.36К_0 (Лингафонный кабинет) (Основной)", "К_0, ауд. 1.36", "Аудитории «К_»", RoomKind.Classroom, note = "Лингафонный кабинет")
    }

    @Test
    fun `the University campus copies match the main rooms`() {
        assertEquals(normalizeRoom("Альфа 5.8Альфа 5.8 (Основной)").key, normalizeRoom("У. Альфа 5.8 (Университет)").key)
        assertEquals(normalizeRoom("1.24К_11 (Основной)").name.substringBefore(','), normalizeRoom("К_У. К_11 (Университет)").name)
        assertEquals(normalizeRoom("1.161.16 К_Газпром нефть (Основной)").key, normalizeRoom("К_Л. 1.16 К_Газпром нефть (Университет)").key)
    }

    @Test
    fun `other places keep their words and get a kind`() {
        check("2218Зал Атом (Основной)", "Зал Атом, ауд. 2218", "Другие помещения", RoomKind.Other)
        check("зона 10.2 (переговорная)", "Зона 10.2 (переговорная)", "Другие помещения", RoomKind.Other)
        check("1-01-01Оранжевый форум (Основной)", "Оранжевый форум", "Другие помещения", RoomKind.Other, code = "1-01-01")
        check("Корпус \"Спорт\"Корпус \"Спорт\" (Основной)", "Корпус «Спорт»", "Другие помещения", RoomKind.Other)
        check("ПолигоныEGO (круглый красный) (Основной)", "Полигоны EGO (круглый красный)", "Другие помещения", RoomKind.Other)
        check("каб.44 Зона 5.14Каб.44 Зона 5.14 (Основной)", "Каб. 44 Зона 5.14", "Другие помещения", RoomKind.Other)
    }

    @Test
    fun `links and remote lessons are not rooms`() {
        listOf("Дистанционно", "https://conf.siriusuniversity.ru/rooms/x/join", "Meeting ID: 959 8953 4758 Passcode: 1").forEach {
            assertEquals(RoomKind.Online, normalizeRoom(it).kind)
        }
        assertFalse(normalizeRoom("зона 10.2 (переговорная)").isTeaching)
        assertTrue(normalizeRoom("1,27К_8 (комп) (Основной)").isTeaching)
    }

    @Test
    fun `a letter stuck to the number stays in it`() {
        check("Альфа 2.2м", "Альфа 2.2м", "Корпус «Альфа»", RoomKind.Classroom)
        check("Комп. К_8 (комп) (Университет)", "К_8", "Аудитории «К_»", RoomKind.Computer)
    }

    @Test
    fun `spellings of one room are counted as that room`() {
        val sites = listOf(
            "1.25К_10 (Основной)", "У. К_10 (Университет)",
            "1.35К_0 (стартап-лаборатория ИНТЦ) (Основной)", "1.36К_0 (Лингафонный кабинет) (Основной)",
            "К_Л. К_0 (стартап-лаборатория ИНТЦ) (Университет)",
            "1,27К_8 (комп) (Основной)", "Комп. К_8 (комп) (Университет)",
            "К_14К_14 (Основной)", "ЛД Айсберг", "ЛД \"Айсберг\"",
        )
        val rooms = sites.map(::normalizeRoom)
        val merged = mergedRooms(rooms)
        fun countedAs(site: String) = merged.getValue(normalizeRoom(site).key).name
        assertEquals("К_10, ауд. 1.25", countedAs("У. К_10 (Университет)"))
        assertEquals("К_0, ауд. 1.35", countedAs("К_Л. К_0 (стартап-лаборатория ИНТЦ) (Университет)"))
        assertEquals("К_8, ауд. 1.27", countedAs("Комп. К_8 (комп) (Университет)"))
        assertEquals("К_14", countedAs("К_14К_14 (Основной)"))
        assertEquals("ЛД «Айсберг»", countedAs("ЛД Айсберг"))
        assertEquals("К_0, ауд. 1.36", countedAs("1.36К_0 (Лингафонный кабинет) (Основной)"))
    }

    @Test
    fun `natural order puts 5_2 before 5_10`() {
        assertEquals(listOf("Альфа 5.2", "Альфа 5.10"), listOf("Альфа 5.10", "Альфа 5.2").sortedBy(::naturalRoomKey))
    }
}
