# 1-3-5 To-Do (Android)

A personal planner built on the 1-3-5 method: each day holds 1 big, 3 medium and 5 small things.
Native Android app (Kotlin, Jetpack Compose). The HTML prototype is the behavior reference.

## Install on the phone
Every push to `main` builds a signed APK and publishes it under **Releases**.
Open the latest release on the phone, download `OneThreeFive-1.0.N.apk`, and install it.
Newer builds install over older ones and keep your data.

## Data
- Everything is stored on the phone in one JSON file (same format as the prototype's backup).
- Settings → Back up saves a JSON file; Settings → Restore opens one (prototype backups work too).
- Android's own device backup also includes the app's data.

## Layout
- `app/src/main/java/.../domain` — all rules in plain Kotlin (rollover, carry-over, recurrence, move/copy, projects, categories, backup format). Unit tested in `app/src/test`.
- `app/src/main/java/.../ui` — Compose screens, menus and sheets.
- `app/src/main/java/.../notify` — reminder alarms and notifications.

## Build locally
`./gradlew testDebugUnitTest assembleDebug` (JDK 17, Android SDK 35).
