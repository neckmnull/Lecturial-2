package com.rassvet.lecturial.model

/** Only what the schedule site itself says about a room; nothing is guessed from its name. */
enum class RoomKind {
    /** A room of a building or a К_ room. */
    Classroom,
    /** Marked "(комп)" on the site. */
    Computer,
    /** Anything else the site lists: halls, meeting rooms, zones. */
    Other,
    /** Not a room at all: "Дистанционно", conference links and the like. */
    Online,
}

/**
 * A room as shown in the statistics, in one spelling: buildings as «Альфа 4.1», К_ rooms as
 * «К_9, ауд. 1.26»; the site's room codes (1-14-19) and notes like «(комп)» are kept apart.
 */
data class RoomInfo(
    val name: String,
    val category: String,
    val kind: RoomKind,
    /** The site's room codes, e.g. "1-14-19". */
    val code: String? = null,
    /** What the site adds in brackets or after the number: "Финтех", "СПД". */
    val note: String? = null,
) {
    /** Rooms the site lists twice (main campus and "Университет") share this key. */
    val key: String get() = "$category|$name"

    /** Rooms of the buildings and К_ rooms: where regular classes are held. */
    val isTeaching: Boolean get() = category != RoomCategories.OTHER && category != RoomCategories.ONLINE
}

object RoomCategories {
    const val OTHER = "Другие помещения"
    const val K = "Аудитории «К_»"
    const val ONLINE = "Онлайн и записи вместо аудитории"
    val BUILDINGS = listOf("Альфа", "Бета", "Гамма", "Дельта")
    fun building(name: String) = "Корпус «$name»"

    /** Display order: buildings, К_ rooms, the rest, online last. */
    val order: List<String> = BUILDINGS.map(::building) + listOf(K, OTHER, ONLINE)
}

private val CampusSuffix = Regex("""\s*\((Основной|Университет)\)\s*$""")
private val CampusPrefix = Regex("""^(?:К_[УЛ]\.|Уч\.\.?|У\.|Комп\.)\s+""")
private val Spaces = Regex("""\s+""")
private val Brackets = Regex("""\(([^()]*)\)""")
private val Codes = Regex("""^((?:\d+-\d+-\d+[\s.]*)+)""")
private val BuildingRoom = Regex("""^(?:лаб\.\s*)?(Альфа|Бета|Гамма|Дельта)\s*(.*)$""", RegexOption.IGNORE_CASE)
// A letter stuck to the number is part of it: "Альфа 2.2м".
private val RoomNumber = Regex("""^([\d.]+[а-яё]?(?:\s*\+\s*[\d.]+[а-яё]?)*)\s*(.*)$""")
private val KRoom = Regex("""^(\d[\d.,\-]*?)?\.?\s*К_(.+)$""")
private val LeadingNumber = Regex("""^(\d{3,4})(?=\D)""")
private val NotRoom = Regex("""https?://|meeting id|passcode|дистан|онлайн|zoom|webinar|вебинар""", RegexOption.IGNORE_CASE)

/** Turns the site's spelling of a room into [RoomInfo]. */
fun normalizeRoom(site: String): RoomInfo {
    var text = site.trim().replace(CampusSuffix, "").replace(CampusPrefix, "").trim()
    text = undouble(text).replace(Spaces, " ").trim()
    if (NotRoom.containsMatchIn(text)) return RoomInfo(text, RoomCategories.ONLINE, RoomKind.Online)

    val notes = Brackets.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    val computer = notes.any { it.equals("комп", ignoreCase = true) }
    val otherNotes = notes.filterNot { it.equals("комп", ignoreCase = true) }
    val bare = text.replace(Brackets, "").replace(Spaces, " ").trim()
    fun kindOf(default: RoomKind) = if (computer) RoomKind.Computer else default

    // "1-14-19.Альфа 4.1", "Альфа 5.12.1Альфа 5.12.1 Финтех (комп)", "1-07-17 1-07-18Бета 3.3+3.4 (комп)"
    val code = Codes.find(bare)?.groupValues?.get(1)
    val afterCode = code?.let { bare.removePrefix(it).trim() } ?: bare
    BuildingRoom.matchEntire(afterCode)?.let { match ->
        val building = RoomCategories.BUILDINGS.first { it.equals(match.groupValues[1], ignoreCase = true) }
        val number = RoomNumber.matchEntire(match.groupValues[2].trim())
        val digits = number?.groupValues?.get(1)?.replace(Regex("""\s*\+\s*"""), " + ")?.trimEnd('.')
        val extra = number?.groupValues?.get(2)?.trim().orEmpty()
        return RoomInfo(
            name = listOfNotNull(building, digits?.takeIf { it.isNotEmpty() }).joinToString(" "),
            category = RoomCategories.building(building),
            kind = kindOf(RoomKind.Classroom),
            code = code?.let(::codesOf),
            note = (listOf(extra) + otherNotes).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null },
        )
    }

    // "1,26К_9", "1.10.1.10. К_Гринатом", "К_12К_12", "19-3К_19"
    KRoom.matchEntire(bare)?.let { match ->
        val number = match.groupValues[1].replace(',', '.').trimEnd('.').ifEmpty { null }
        val label = match.groupValues[2].trim()
        return RoomInfo(
            name = "К_$label" + (number?.let { "$KNumberSeparator$it" } ?: ""),
            category = RoomCategories.K,
            kind = kindOf(RoomKind.Classroom),
            note = otherNotes.joinToString(", ").ifEmpty { null },
        )
    }

    // Everything else keeps its words, tidied: "ПолигоныEGO" → "Полигоны EGO", quotes as «».
    var name = text
    var otherCode: String? = null
    Codes.find(name)?.let { match ->
        otherCode = codesOf(match.groupValues[1])
        name = name.removePrefix(match.groupValues[1]).trim()
    }
    var number: String? = null
    LeadingNumber.find(name)?.let { match ->
        number = match.groupValues[1]
        name = name.removePrefix(match.groupValues[1]).trim()
    }
    name = name
        .replace(Regex("""([а-яё])([A-Z])"""), "$1 $2")
        .replace(Regex(""""([^"]*)""""), "«$1»")
        .replace(Regex("""(?iu)^каб\.\s*(\d)"""), "Каб. $1")
        .replace(Regex("""»(\d)"""), "» $1")
        .trimEnd('.')
        .replaceFirstChar { it.uppercaseChar() }
    if (number != null) name += ", ауд. $number"
    return RoomInfo(name, RoomCategories.OTHER, kindOf(RoomKind.Other), code = otherCode)
}

/**
 * For every room key, the room it is counted as. The site lists some rooms under several
 * spellings: a К_ room of the "Университет" campus comes without its number ("К_10") and
 * joins the numbered one ("К_10, ауд. 1.25") when there is exactly one with the same label
 * and note; other places that differ only in quotes or dots ("ЛД Айсберг", "ЛД «Айсберг»")
 * join as well.
 */
fun mergedRooms(rooms: Collection<RoomInfo>): Map<String, RoomInfo> {
    val distinct = rooms.distinctBy { it.key }
    val numbered = distinct.filter { it.category == RoomCategories.K && KNumberSeparator in it.name }
    fun loose(name: String) = name.lowercase().replace(Regex("""[«»"'.,]"""), "").replace(Spaces, " ").trim()
    return distinct.associate { room ->
        room.key to when {
            room.category == RoomCategories.K && KNumberSeparator !in room.name ->
                numbered.filter { it.name.substringBefore(KNumberSeparator) == room.name && (room.note == null || it.note == room.note) }
                    .singleOrNull() ?: room
            room.category == RoomCategories.OTHER ->
                distinct.filter { it.category == RoomCategories.OTHER && loose(it.name) == loose(room.name) }
                    .maxWith(compareBy<RoomInfo> { it.name.count { c -> c == '«' } }.thenBy { it.name })
            else -> room
        }
    }
}

private const val KNumberSeparator = ", ауд. "

/** "1-07-17 1-07-18" or "1-14-19." → "1-07-17, 1-07-18" / "1-14-19". */
private fun codesOf(raw: String): String = raw.split(Regex("""[\s.]+""")).filter { it.isNotEmpty() }.joinToString(", ")

/** The site repeats a room's number in front of its name: "К_12К_12", "1.081.08 К_Росатом". */
private fun undouble(text: String): String {
    val lower = text.lowercase()
    // Four letters at least, so codes like "1-01-01" aren't taken for a doubled "1-0".
    for (k in text.length / 2 downTo 4) {
        if (lower.substring(0, k) == lower.substring(k, 2 * k)) return text.substring(k)
    }
    return text
}

/** Sort key that orders room names the way people read them: "Альфа 5.2" before "Альфа 5.10". */
fun naturalRoomKey(name: String): String =
    Regex("""\d+""").replace(name.lowercase()) { it.value.padStart(9, '0') }
