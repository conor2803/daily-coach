package io.github.conor2803.coach.coach

/** What turns a briefing into advice. */
enum class Engine {
    CLAUDE,
    ON_DEVICE;

    /** Why a check-in can't run yet with this engine, or null when it's ready. */
    fun missingSetup(apiKey: String, modelDownloaded: Boolean): String? = when (this) {
        CLAUDE -> if (apiKey.isBlank()) "Add your Anthropic API key in the Coach app." else null
        ON_DEVICE -> if (!modelDownloaded) "Download the on-device model in Coach settings." else null
    }

    companion object {
        val DEFAULT = ON_DEVICE

        fun fromStored(value: String?): Engine = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
