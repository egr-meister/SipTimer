package com.egrmeister.siptimer.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Injected clock + time zone so every time-dependent rule can be tested deterministically. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId
}

object SystemTimeSource : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun TimeSource.today(): LocalDate = now().atZone(zone()).toLocalDate()

fun Instant.localDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
