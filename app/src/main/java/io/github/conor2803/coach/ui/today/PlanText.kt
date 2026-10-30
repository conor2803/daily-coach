package io.github.conor2803.coach.ui.today

/** Where the clock times sit in one line of a Plan, so the UI can set them in bold. */
internal fun timeSpans(line: String): List<IntRange> =
    TIME.findAll(line).map { it.range }.toList()

/** One line per step; a plan that came back as a single paragraph is split into its sentences. */
internal fun planLines(plan: String): List<String> {
    val lines = plan.lines().map { it.trim().trimStart('-', '•', '*').trim() }.filter { it.isNotEmpty() }
    return if (lines.size == 1) SENTENCE_END.split(lines.single()).filter { it.isNotBlank() } else lines
}

private val TIME = Regex("""\b([01]?\d|2[0-3]):[0-5]\d\b""")
private val SENTENCE_END = Regex("""(?<=[.!?])\s+(?=[A-Z])""")
