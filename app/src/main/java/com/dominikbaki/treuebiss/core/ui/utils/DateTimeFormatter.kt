package com.dominikbaki.treuebiss.core.ui.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Ein einfaches Hilfsobjekt, um Datums- und Zeitwerte für die UI zu formatieren.
object DateTimeFormatter {
    fun formatInstant(
        instant: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): String {
        val localDateTime = instant.toLocalDateTime(timeZone)
        val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
        val month = localDateTime.monthNumber.toString().padStart(2, '0')
        val year = localDateTime.year.toString()
        return "$day.$month.$year"
    }
}