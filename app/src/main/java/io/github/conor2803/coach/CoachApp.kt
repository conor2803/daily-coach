package io.github.conor2803.coach

import android.app.Application
import io.github.conor2803.coach.coach.Notifier
import io.github.conor2803.coach.coach.Scheduler

class CoachApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        // Make sure the next check-in is always queued, even after an update.
        Scheduler.ensureScheduled(this)
    }
}
