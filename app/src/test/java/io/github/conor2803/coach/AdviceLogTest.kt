package io.github.conor2803.coach

import io.github.conor2803.coach.coach.AdviceLog
import io.github.conor2803.coach.coach.Engine
import io.github.conor2803.coach.coach.LoggedAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdviceLogTest {
    private val entry = LoggedAdvice(
        Advice("Easy run today", "07:30 Run 5 km easy\n17:00 Stretch\nWhy: \"recovery\" is low", 1_700_000_000_000L),
        Engine.CLAUDE,
    )

    @Test
    fun encoded_entry_parses_back_to_the_same_entry() {
        assertEquals(entry, AdviceLog.parse(AdviceLog.encode(entry)))
    }

    @Test
    fun encoded_entry_is_a_single_line() {
        assertEquals(1, AdviceLog.encode(entry).lines().size)
    }

    @Test
    fun corrupt_line_is_skipped() {
        assertNull(AdviceLog.parse("{\"headline\": \"half a li"))
        assertNull(AdviceLog.parse("not json"))
        assertNull(AdviceLog.parse(""))
    }

    @Test
    fun line_missing_a_field_is_skipped() {
        assertNull(AdviceLog.parse("""{"headline":"h","at":1}"""))
    }

    @Test
    fun unknown_engine_falls_back_to_the_default_engine() {
        val line = """{"headline":"h","plan":"p","at":1,"engine":"GONE"}"""
        assertEquals(Engine.DEFAULT, AdviceLog.parse(line)?.engine)
    }
}
