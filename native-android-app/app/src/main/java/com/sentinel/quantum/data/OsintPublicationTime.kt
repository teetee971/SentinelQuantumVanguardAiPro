package com.sentinel.quantum.data

import java.text.DateFormat
import java.util.Date

/** Shared presentation rule for publication timestamps.
 *
 * Epoch/non-positive values are the explicit sentinel for an unknown publication time.
 * They must never be rendered as 01/01/1970 or promoted to the current time.
 */
object OsintPublicationTime {
    fun format(date: Date, formatter: DateFormat, unknownLabel: String): String =
        if (date.time <= 0L) unknownLabel else formatter.format(date)
}
