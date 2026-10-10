package com.rassvet.lecturial.model

import org.junit.Assert.*
import org.junit.Test

class LanguageTeacherTest {
    @Test fun filtersOnlyForeignLanguageLessons() {
        assertFalse(matchesLanguageTeacher("Иностранный язык", listOf("Петрова"), "Иванова"))
        assertTrue(matchesLanguageTeacher("Математика", listOf("Петрова"), "Иванова"))
        assertTrue(matchesLanguageTeacher("Английский язык", listOf(" Иванова ", "Петрова"), "иванова"))
    }

    @Test fun keepsUnknownTeachersAndAllTeachersOption() {
        assertTrue(matchesLanguageTeacher("Иностранный язык", emptyList(), "Иванова"))
        assertTrue(matchesLanguageTeacher("Иностранный язык", listOf("Петрова"), ""))
        assertTrue(matchesLanguageTeacher("Иностранный язык", listOf("Петрова"), null))
    }
}
