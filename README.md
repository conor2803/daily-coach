# Coach

A personal Android app that acts like a trainer in your pocket. A couple of times a day it
looks at your Garmin and RingConn data, where you've been, your calendar and the weather,
asks Claude what you should do next, and sends you a notification like:

> **Good sleep, HRV up — easy 5k at 18:00 before the rain**
> Run 18:00–18:40, easy pace
> Dinner by 19:15
> Screens off 21:45, bed 22:30
> Office day and 7:40 start, so keep it easy rather than skipping it.

Everything runs on your phone. No server, no account. Two things leave the phone: the text
summary sent to the Claude API (tap **Preview data** in the app to see exactly what that is),
and your current location, sent to Open-Meteo to get the weather forecast.

## How it works

```
Garmin Connect ─┐
                ├─► Health Connect ─┐
RingConn ───────┘                   │
Geofences (Home, Office, Gym…) ─────┤
Calendar ───────────────────────────┼─► ContextBuilder ─► Claude API ─► notification
Weather (Open-Meteo) ───────────────┘          ▲
                                     WorkManager check-ins (07:30, 16:30 by default)
```

| Piece | File |
|---|---|
| Garmin + ring data (sleep, HR, HRV, resting HR, steps, SpO₂, workouts) | `health/HealthReader.kt` |
| Arrive/leave log for your saved places | `places/` |
| Calendar and weather | `context/` |
| The briefing sent to Claude | `context/ContextBuilder.kt` |
| Prompt and API call | `coach/ClaudeClient.kt` |
| Scheduled check-ins | `coach/CoachWorker.kt`, `coach/Scheduler.kt` |
| Setup screen | `MainActivity.kt` |

## Setup

1. **Open in Android Studio** (any recent version) and let Gradle sync. Plug in your phone with
   USB debugging on and press Run.
2. **Get an API key** at <https://console.anthropic.com> and paste it into *Coach settings*.
   Two check-ins a day costs pennies.
3. **Send your data to Health Connect**
   - Garmin Connect → Settings → Health Connect → turn it on and allow everything.
   - RingConn → Settings → Sync to Health Connect → turn it on.
4. **Grant permissions** in the app's *Permissions* card. For Health Connect, also allow
   *Access data in the background*, otherwise scheduled check-ins can't read it.
   For places, choose **Allow all the time** for location.
5. **Save your places.** Stand at home, type "Home", tap *Save here*. Same at the office, gym, etc.
6. Tap **Check in now** to test, and **Preview data** to see what the coach sees.

## Notes

- **Ring sync:** the ring's data only lands in Health Connect after RingConn has synced. Garmin
  syncs on its own in the background, so the coach always has fresh watch data and picks up
  the ring's extra detail whenever RingConn next syncs.
- **Samsung / Xiaomi / OnePlus:** set Coach's battery usage to *Unrestricted* so check-ins and
  geofences aren't killed in the background.
- **Model:** defaults to `claude-sonnet-5-5`; change it in settings.
- **Dependencies:** if Gradle complains about a version, accept Android Studio's suggested
  upgrade. Health Connect background reads need `connect-client` 1.1.0 or newer and
  Android 14+ (or the Health Connect app on older Android).
- The API key is stored in the app's private storage on the phone and is never committed here.

## Ideas for next

- Add Strava for richer run history, or route suggestions near home.
- Mid-afternoon nudge only on office days.
- A weekly summary on Sunday evening.
- Store daily snapshots so the coach can see longer trends.
