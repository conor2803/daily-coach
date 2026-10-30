package io.github.conor2803.coach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineTest {

    @Test
    fun `a fresh install uses the on-device engine`() {
        assertEquals(Engine.ON_DEVICE, Engine.fromStored(null))
    }

    @Test
    fun `a stored choice is kept`() {
        assertEquals(Engine.CLAUDE, Engine.fromStored("CLAUDE"))
        assertEquals(Engine.ON_DEVICE, Engine.fromStored("ON_DEVICE"))
    }

    @Test
    fun `an unrecognised stored value falls back to on-device rather than spending money`() {
        assertEquals(Engine.ON_DEVICE, Engine.fromStored("GEMINI_NANO"))
    }

    @Test
    fun `claude cannot check in without an api key`() {
        assertEquals(
            "Add your Anthropic API key in the Coach app.",
            Engine.CLAUDE.missingSetup(apiKey = " ", modelDownloaded = true),
        )
    }

    @Test
    fun `on-device cannot check in until the model is downloaded`() {
        assertEquals(
            "Download the on-device model in Coach settings.",
            Engine.ON_DEVICE.missingSetup(apiKey = "", modelDownloaded = false),
        )
    }

    @Test
    fun `each engine needs only its own setup`() {
        assertNull(Engine.CLAUDE.missingSetup(apiKey = "sk-ant-test", modelDownloaded = false))
        assertNull(Engine.ON_DEVICE.missingSetup(apiKey = "", modelDownloaded = true))
    }
}
