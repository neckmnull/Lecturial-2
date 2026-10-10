package com.rassvet.lecturial.model

fun isForeignLanguage(discipline: String): Boolean {
    val name = discipline.lowercase().replace("ё", "е")
    return listOf("иностранн", "английск", "немецк", "французск", "иняз").any { name.contains(it) }
}

fun matchesLanguageTeacher(discipline: String, teachers: List<String>, teacher: String?): Boolean =
    teacher.isNullOrBlank() || !isForeignLanguage(discipline) || teachers.all { it.isBlank() } ||
        teachers.any { it.trim().equals(teacher.trim(), ignoreCase = true) }

fun ScheduleWeek.forLanguageTeacher(teacher: String?): ScheduleWeek = copy(
    days = days.map { day -> day.copy(events = day.events.filter {
        matchesLanguageTeacher(it.discipline, it.teachers, teacher)
    }) },
)
