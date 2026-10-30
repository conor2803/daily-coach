package io.github.conor2803.coach.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class CoachPromptTest {

    @Test
    fun `a json reply becomes a headline and plan`() {
        val advice = CoachPrompt.parse("""{"headline": "Rest day", "plan": "Walk 12:30\nBed 22:30"}""")
        assertEquals("Rest day", advice.headline)
        assertEquals("Walk 12:30\nBed 22:30", advice.plan)
    }

    @Test
    fun `json wrapped in chatter and a code fence is still found`() {
        val reply = """
            Here's your plan:
            ```json
            {"headline": "Easy 5k at 18:00", "plan": "Run 18:00-18:40"}
            ```
        """.trimIndent()
        val advice = CoachPrompt.parse(reply)
        assertEquals("Easy 5k at 18:00", advice.headline)
        assertEquals("Run 18:00-18:40", advice.plan)
    }

    @Test
    fun `a plain text reply uses its first line as the headline`() {
        val advice = CoachPrompt.parse("Take it easy today\n\nWalk at lunch\nBed by 22:30")
        assertEquals("Take it easy today", advice.headline)
        assertEquals("Walk at lunch\nBed by 22:30", advice.plan)
    }

    @Test
    fun `a long plain first line is cut to fit a notification title`() {
        val advice = CoachPrompt.parse("x".repeat(200))
        assertEquals(110, advice.headline.length)
    }

    @Test
    fun `json with an empty headline falls back to the text`() {
        val advice = CoachPrompt.parse("""{"headline": "", "plan": "Run"}""")
        assertEquals("""{"headline": "", "plan": "Run"}""", advice.headline)
    }

    @Test
    fun `an empty reply still gives a headline`() {
        assertEquals("Check-in ready", CoachPrompt.parse("   ").headline)
    }
}
