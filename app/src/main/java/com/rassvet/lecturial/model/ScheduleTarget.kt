package com.rassvet.lecturial.model

/** A teacher from the Sirius schedule site, identified by the site's UUID. */
data class TeacherInfo(
    val id: String,
    val name: String,
    val department: String?,
)

/**
 * Whose schedule is shown. A group is stored by its plain name (as before teacher mode
 * existed); a teacher is stored as `teacher:<uuid>`, so caches, alarms and the widget
 * keep working with a single schedule key.
 */
object ScheduleTarget {
    private const val TEACHER_PREFIX = "teacher:"
    private const val CLASSROOM_PREFIX = "classroom:"

    fun teacherKey(id: String): String = TEACHER_PREFIX + id

    fun teacherId(key: String?): String? =
        key?.takeIf { it.startsWith(TEACHER_PREFIX) }?.removePrefix(TEACHER_PREFIX)?.takeIf { it.isNotBlank() }

    fun isTeacher(key: String?): Boolean = teacherId(key) != null

    /** [id] is the classroom value exactly as the site lists it, e.g. "Альфа 5.8Альфа 5.8 (Основной)". */
    fun classroomKey(id: String): String = CLASSROOM_PREFIX + id

    fun classroomId(key: String?): String? =
        key?.takeIf { it.startsWith(CLASSROOM_PREFIX) }?.removePrefix(CLASSROOM_PREFIX)?.takeIf { it.isNotBlank() }

    /** "Альфа 5.8Альфа 5.8 (Основной)" → "Альфа 5.8": drops the campus suffix and the site's doubled text. */
    fun classroomLabel(id: String): String {
        val withoutCampus = id.trim().removeSuffix("(Основной)").trim()
        if (withoutCampus.length % 2 == 0) {
            val half = withoutCampus.length / 2
            if (withoutCampus.substring(0, half) == withoutCampus.substring(half)) return withoutCampus.substring(0, half)
        }
        return withoutCampus
    }

    /** Text to show for the schedule key: the teacher's name or the group itself. */
    fun label(key: String?, teacherName: String?): String = when {
        key == null -> ""
        isTeacher(key) -> teacherName?.takeIf { it.isNotBlank() } ?: "Преподаватель"
        classroomId(key) != null -> classroomLabel(classroomId(key)!!)
        else -> key
    }
}
