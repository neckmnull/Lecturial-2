package com.rassvet.lecturial.model

import java.time.LocalDate

/** How a discipline ends the semester, the strictest form first. */
enum class AssessmentKind(val title: String, val section: String, val explanation: String) {
    Exam(
        "Экзамен",
        "Экзамены",
        "Оценка 2–5 в зачётку: идёт в средний балл и влияет на стипендию. Накопительный экзамен " +
            "складывается из баллов за семестр, обычный сдают в сессию по билетам.",
    ),
    GradedCredit(
        "Дифф. зачёт",
        "Дифференцированные зачёты",
        "Зачёт с оценкой 2–5. В зачётку ставят оценку, и она, как экзамен, идёт в средний балл и " +
            "влияет на стипендию. Чаще всего ставится по работам семестра и итоговой работе.",
    ),
    Credit(
        "Зачёт",
        "Зачёты",
        "Без оценки: «зачтено» или «не зачтено». Обычно ставят по работе в семестре — посещаемость, " +
            "сданные задания, иногда итоговый тест. На стипендию не влияет, но незачёт — это долг.",
    ),
    Control(
        "Контроль",
        "Другие формы контроля",
        "Промежуточные работы: коллоквиумы, тесты, рефераты, проекты. Их результаты обычно учитываются в итоговой оценке.",
    ),
}

/** "Зачет дифференцированный", "Экзамен накопительный", "Коллоквиум"… as an [AssessmentKind]. */
fun assessmentKind(form: String): AssessmentKind {
    val lower = form.lowercase()
    return when {
        "дифф" in lower || "дифференц" in lower || "с оценкой" in lower -> AssessmentKind.GradedCredit
        "экзам" in lower -> AssessmentKind.Exam
        "зач" in lower -> AssessmentKind.Credit
        else -> AssessmentKind.Control
    }
}

/** The form the discipline ends with: an exam outranks a graded credit, which outranks a credit. */
val LksDiscipline.assessment: AssessmentKind? get() = controlForms.map(::assessmentKind).minByOrNull { it.ordinal }

/** Whether a schedule entry is a credit, an exam or a consultation before one. */
fun isAssessmentEvent(event: ScheduleEvent): Boolean {
    val type = event.type.lowercase()
    return "зач" in type || "экзам" in type || "консульт" in type
}

/** The credits, exams and consultations of [discipline] in [events] from [from] on, soonest first. */
fun assessmentEvents(events: List<ScheduleEvent>, discipline: String, from: LocalDate): List<ScheduleEvent> =
    events.filter { !it.date.isBefore(from) && isAssessmentEvent(it) && sameDisciplineName(it.discipline, discipline) }
        .sortedWith(compareBy({ it.date }, { parseEventMinutes(it.startTime) }))

/**
 * What the journal says ahead of the assessment, as short hints: attendance and grades that may
 * stand in the way, or that everything looks fine. Null when the journal knows nothing.
 */
fun assessmentHint(kind: AssessmentKind?, journal: LksDisciplineStats?): String? {
    journal ?: return null
    val attendance = journal.attendance
    val average = journal.average?.let(::fivePointGrade)
    val worries = buildList {
        if (attendance != null && attendance < 0.7) add("посещаемость ниже 70 % — уточните у преподавателя условия допуска")
        if (average != null && average < 3.5) add("средний по журналу ниже 3,5 — стоит подтянуть")
        if (journal.marks.count { it.grade != null && fivePointGrade(it.grade.toDouble()) < 3 } > 0) add("есть двойки — их лучше закрыть до сессии")
    }
    return when {
        worries.isNotEmpty() -> worries.joinToString("; ").replaceFirstChar { it.uppercase() }
        kind == AssessmentKind.Credit && attendance != null && attendance >= 0.8 -> "По журналу всё в порядке: ходите и сдаёте работы"
        (kind == AssessmentKind.GradedCredit || kind == AssessmentKind.Exam) && average != null ->
            "Средний по журналу ${"%.1f".format(average).replace('.', ',')} — ориентир для итоговой оценки"
        else -> null
    }
}

/** Ten- and hundred-point grades read on the five-point scale. */
fun fivePointGrade(value: Double): Double = when {
    value > 10 -> value / 20
    value > 5 -> value / 2
    else -> value
}
