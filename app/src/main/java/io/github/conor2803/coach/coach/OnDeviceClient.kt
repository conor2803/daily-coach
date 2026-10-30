package io.github.conor2803.coach.coach

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.EngineConfig
import io.github.conor2803.coach.Advice
import java.io.File
import com.google.ai.edge.litertlm.Engine as LiteRtEngine

/** Runs the coach on the phone with Gemma through LiteRT-LM. The briefing never leaves the phone. */
object OnDeviceClient {
    private const val CONTEXT_TOKENS = 8192
    private const val MAX_REPLY_TOKENS = 1000

    fun coach(model: File, cacheDir: File, briefing: String): Advice {
        startOnGpuElseCpu(model, cacheDir).use { engine ->
            val config = ConversationConfig(
                systemInstruction = Contents.of(CoachPrompt.SYSTEM),
                maxOutputToken = MAX_REPLY_TOKENS,
            )
            engine.createConversation(config).use { conversation ->
                val reply = conversation.sendMessage(briefing)
                val text = reply.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                    .trim()
                return CoachPrompt.parse(text)
            }
        }
    }

    private fun startOnGpuElseCpu(model: File, cacheDir: File): LiteRtEngine =
        runCatching { started(model, cacheDir, Backend.GPU()) }
            .getOrElse { started(model, cacheDir, Backend.CPU()) }

    private fun started(model: File, cacheDir: File, backend: Backend): LiteRtEngine {
        val engine = LiteRtEngine(
            EngineConfig(
                modelPath = model.path,
                backend = backend,
                maxNumTokens = CONTEXT_TOKENS,
                cacheDir = cacheDir.path,
            )
        )
        try {
            engine.initialize()
        } catch (e: Exception) {
            engine.close()
            throw e
        }
        return engine
    }
}
