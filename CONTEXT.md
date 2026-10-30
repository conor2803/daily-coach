# Coach

A personal Android app that acts as a daily trainer: a few times a day it reads the owner's
health, places, calendar and weather, and turns them into a short plan for the rest of the day.

## Language

### Check-ins

**Check-in**:
One run of the coach, either at a scheduled time or on demand, ending in a notification.
_Avoid_: run, job, sync

**Briefing**:
The plain-text summary of the owner's day that an Engine reads; exactly what *Preview data* shows.
_Avoid_: context, prompt, payload

**Advice**:
What a Check-in produces: a Headline and a Plan.
_Avoid_: response, result, recommendation

**Headline**:
The one-line main call for the day, shown as the notification title.

**Plan**:
A few short timed lines saying what to do and when, then one line on why.

### Engines

**Engine**:
The thing that turns a Briefing into Advice. Either the Claude Engine or the On-device Engine.
_Avoid_: model, provider, backend, AI

**Claude Engine**:
The Engine that sends the Briefing to Anthropic's Claude API, paid per use.

**On-device Engine**:
The Engine that runs an open model on the phone itself, free and offline.
_Avoid_: Gemma (that names a Model, not the Engine), local, Nano

**Model**:
The specific model an Engine uses, such as `claude-sonnet-5-5` or Gemma 4 E2B.

### Places

**Place**:
A location the owner saved (Home, Office, Gym…) that the phone watches for arrivals and departures.
_Avoid_: geofence, location
