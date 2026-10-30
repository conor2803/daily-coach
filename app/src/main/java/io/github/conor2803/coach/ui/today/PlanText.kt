package io.github.conor2803.coach.ui.today

/** Where the clock times sit in one line of a Plan, so the UI can set them in bold. */
internal fun timeSpans(line: String): List<IntRange> =
    TIME.findAll(line).map { it.range }.toList()

internal fun planLines(plan: String): List<String> =
    plan.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }

private val TIME = Regex("""\b([01]?\d|2[0-3]):[0-5]\d\b""")
