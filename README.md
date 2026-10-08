# SipTimer

SipTimer is a lightweight, fully offline Android water tracker centred on **the time until the next optional reminder**. You record water, choose a daily goal, set active hours and a reminder interval. Goals and intervals are your own preferences, not medical recommendations.

- Package: `com.egrmeister.siptimer`
- Language: Kotlin + Jetpack Compose (Material 3, customised)
- `compileSdk = 36`, `targetSdk = 36` (Android 16), `minSdk = 26` (Android 8.0)
- No INTERNET permission, no accounts, backend, Firebase, ads, analytics, payments, cloud sync, sensors, Health Connect or location.

## Features

| Area | What it does |
|---|---|
| Today | Large "Next sip in 42 min." countdown with a restrained time-progress arc, scheduled local time, reminder status, **Add Water**, **Skip Next Reminder**, recorded volume vs. goal with a slim progress line, quick portions, today's chronological entries |
| Logging | Quick portions (default 150 / 250 / 350 / 500 mL, editable in Settings) and a custom entry (amount, date, time, Save / Cancel). 1–5,000 whole mL, today or past dates, no future timestamps. Confirmation snackbar with **Undo** |
| Editing | Edit amount/date/time; delete with **Undo**; drafts survive rotation and process death; leaving with unsaved edits asks first |
| Goals | 100–10,000 mL, effective-dated: **Apply today** or **Apply tomorrow**; earlier dates are never rewritten |
| History | Per day: date, recorded volume, saved goal (when available), entry count, skipped-reminder count. Day details show water entries and skip events in separate sections; "No records" for empty days; date picker and earlier/later day navigation |
| Reminders | Optional, off by default; inexact one-shot `AlarmManager` alarms; one notification ID; reboot / update / time / time-zone recovery |
| Data | Room + DataStore in app-private storage; cloud backup and device transfer disabled; **Clear All Data** with confirmation |

## Architecture

Single `:app` module, manual dependency injection (`AppContainer` in `SipTimerApp.kt`).

```
com.egrmeister.siptimer
├── domain/   pure Kotlin: ActiveWindow, ReminderPlanner, ReminderEngine (state machine),
│             CountdownPresenter, Validation, GoalHistory/GoalMath, ActivationGuard, TimeSource
├── data/     Room (entities, DAOs, AppDatabase + Migrations), SettingsStore (DataStore), WaterRepository
├── reminders/ AndroidAlarmScheduler, AndroidNotifications, ReminderReceiver, SystemEventsReceiver
└── ui/       Compose screens (setup, today, entry, history, settings, privacy), theme, navigation
```

- ViewModel + `StateFlow`, collected with `collectAsStateWithLifecycle` (lifecycle-aware).
- Clock, time zone, alarm scheduler and notifications are interfaces (`TimeSource`, `AlarmGateway`, `NotificationGateway`) injected into the engine for tests.
- All Room work runs on `Dispatchers.IO`; schedule transitions are serialised with a `Mutex`.

## Toolchain (pinned)

| Tool | Version |
|---|---|
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.07.00 |
| Room | 2.7.2 (KSP, schemas exported to `app/schemas`) |
| DataStore | 1.1.7 |
| Navigation Compose | 2.9.3 |
| Lifecycle | 2.9.2 |
| JDK | 17 (Temurin in CI) |
| Build tools (CI) | 36.0.0 |

All versions live in `gradle/libs.versions.toml`. Every directly used library is declared directly.

## Scheduling rules

- **Active window** is same-day only: start **included**, end **excluded**. Window length 2–18 h. Interval 30–240 min in 15-minute steps and shorter than the window (an interval equal to the window would land exactly on the excluded end, so no reminder could ever fit).
- **First reminder of a day** = window start + interval.
- **Boundary rule**: a calculated reminder that falls at or after the end moves to the next day's first reminder; one that falls before the start of its day becomes that day's first reminder.
- **Enabling reminders**: inside the window → now + interval; outside → next window start + interval (boundary rule applied to both).
- **Adding water now** (Quick Add, or a custom entry whose date/time you did not change) cancels the pending reminder, dismisses the current notification and starts a fresh interval. **Backdated entries and edits only change totals.**
- **Delivery**: after a reminder is processed the next one is one interval after the processing time; missed intervals are never replayed.
- **Settings change** (interval / active hours / reminders on): recalculated from now with the new settings.
- **Skip Next Reminder**: only when a future reminder exists and reminders are on, allowed and not paused. It skips that occurrence, advances by one interval (next day if needed), records a skip event, never adds water, shows the replacement time at once. The engine only accepts a skip for the exact occurrence and generation the user saw, so one activation can never be processed twice; deliberate repeated skips work.
- **Generation identifier**: every transition stores the next due time **and** a new generation. Alarm callbacks carry their generation and exit when it is obsolete — delayed or duplicate callbacks cannot post twice.
- **Before posting** a notification the engine checks: reminders on, notifications allowed (app, POST_NOTIFICATIONS on 13+, channel not blocked), current generation, due time belongs to today, now inside the window, today's goal not reached. Stale previous-day and outside-window deliveries are rejected.
- **Countdown** is derived from the saved due timestamp (never a persisted counter), updated per minute only while the screen is visible, never negative. States: Active, later today/tomorrow/date, Reminders are off, Notifications are not allowed, goal reached (paused), "Reminder time reached. Android may deliver it later."

### Goal reached

When today's recorded total reaches the saved goal, pending reminders for today are cancelled, the current notification is dismissed, the paused state is shown and the next eligible day's first reminder is scheduled. If an edit/delete/Undo drops the total below the goal, a fresh interval starts from now (an obsolete due time is never restored). Logging stays available; totals and percentages above 100 % are shown as recorded, with no encouragement to drink more.

### Time, zones and daylight saving

- Entries store the absolute timestamp **and** the logged local date, local time and zone ID. History groups by the recorded local date, so travelling never moves an entry to another day.
- Active windows and future reminders use the **current** device time zone. `TIME_SET` / `TIMEZONE_CHANGED` recalculate a fresh schedule with a new generation (old alarms become obsolete).
- **DST**: window boundaries are resolved with `ZonedDateTime.of` — a boundary inside a spring-forward gap moves forward by the gap length (e.g. 02:30 → 03:30); inside a fall-back overlap the earlier offset is used. Intervals are elapsed real time (`Instant + Duration`), so 01:30 + 120 min on a fall-back night is the *second* 02:30.
- At midnight the visible screen refreshes today's total and goal. No online time verification.

### Recovery

State is persisted (DataStore) **before** the alarm is registered. On process start, reboot (`BOOT_COMPLETED`), and app update (`MY_PACKAGE_REPLACED`) the saved schedule is re-registered without restarting it; a saved due time from an earlier day is replaced by a fresh one.

## Notifications

- Reminders are **disabled by default**. On Android 13+ `POST_NOTIFICATIONS` is requested only when you turn reminders on, and only once. If denied, tracking keeps working, the Today screen shows "Notifications are not allowed." with an optional link to system settings, and the app never prompts again.
- Title "SipTimer", body "Would you like to record some water?". Tapping opens Today. Delivery never logs water.
- Inexact one-shot `AlarmManager.setAndAllowWhileIdle` — **no** `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`. Android may deliver later than scheduled; the countdown shows the scheduled time, not a delivery guarantee.
- Explicit, immutable `PendingIntent`s; the alarm receiver is `exported="false"`; the system-events receiver is exported only for protected system broadcasts.
- No foreground service, push messaging, wake-lock permission or battery-exemption request.
- **Force-stop limitation**: if the app is force-stopped (Settings → Apps → Force stop), Android cancels its alarms and blocks its receivers until the app is opened again. Opening SipTimer restores the saved schedule.

## Privacy, storage and backup

- Permissions: `POST_NOTIFICATIONS` (optional reminders) and `RECEIVE_BOOT_COMPLETED` (reminder recovery) — nothing else. INTERNET, ACCESS_NETWORK_STATE, WAKE_LOCK, exact alarms and foreground service are explicitly removed in the manifest; CI checks both the merged and the packaged manifests against this allowlist.
- Room database `siptimer.db` and DataStore `siptimer_settings` are in app-private storage.
- `android:allowBackup="false"`, `fullBackupContent` and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer.
- Bundled privacy information: Settings → Privacy and data.
- Clear All Data (Settings) asks for confirmation, cancels alarms and notifications, deletes all tables and preferences, and returns to setup.

## Storage schema

Room v1 (`exportSchema = true`, Room Gradle plugin → `app/schemas/com.egrmeister.siptimer.data.AppDatabase/1.json`):

- `water_entries(id, amountMl, timestamp, loggedLocalDate, loggedLocalTime, zoneId, createdAt, updatedAt)`
- `goal_changes(effectiveLocalDate PK, goalMl, createdAt)`
- `reminder_skips(id, skippedDueAt, actionTimestamp, loggedLocalDate, replacementDueAt)`

Totals are summed as `Long`. Future versions add explicit migrations in `data/AppDatabase.kt` (`Migrations.ALL`); destructive migration is never enabled. The schema JSON is generated on the first build — commit it (CI uploads it as the `siptimer-schemas-and-reports-*` artifact and warns while it is uncommitted).

## Signing (PKCS12) and GitHub Secrets

The release build type uses `signingConfigs.getByName("release")` explicitly. Credentials come from environment variables (CI) or a git-ignored `keystore.properties`. **Missing credentials fail every release task — there is no debug-key fallback.** On `main` pushes, a missing secret fails CI with an explicit error.

Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of the `.p12` keystore (single line) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password (equals the store password for PKCS12) |

The keystore and the signing-data file are stored **outside the project** (`~/Desktop/APPS Console/_keys/SipTimer`). `.gitignore` excludes `*.p12`, `*.jks`, `*.keystore`, `*.base64`, `keystore.properties`, `secrets.txt`. Never commit them. The upload certificate is self-signed, which is normal for Play App Signing.

## CI (`.github/workflows/android.yml`)

1. Committed Gradle wrapper (validated), JDK 17, SDK platform 36 + build-tools 36.0.0.
2. Focused unit tests (`testDebugUnitTest`) and release lint (`lintRelease`).
3. Decodes the keystore into `$RUNNER_TEMP`, builds **signed** `assembleRelease bundleRelease`.
4. `apksigner verify --verbose --print-certs` on the APK; fails on errors or `CN=Android Debug`.
5. `jarsigner -verify` on the AAB (integrity + signer); fails on unsigned/tampered entries or debug cert; APK and AAB signer SHA-256 must match.
6. Permission allowlist on the packaged APK (`aapt2 dump permissions`) and the merged release manifest.
7. Native-library inventory of APK and AAB + `zipalign -c -P 16` check (+ ELF `LOAD` alignment if `.so` files ever appear).
8. Uploads the verified APK, AAB and reports; uploads Room schemas, lint reports and (if R8 is enabled) `mapping.txt`.
9. Always removes the temporary keystore.

No emulator smoke test is required in CI.

## Build locally

```bash
export ANDROID_KEYSTORE_FILE="$HOME/Desktop/APPS Console/_keys/SipTimer/siptimer-release.p12"
export ANDROID_KEYSTORE_PASSWORD='…'   # from _keys/SipTimer/secrets.txt
export ANDROID_KEY_ALIAS='siptimer-upload'
export ANDROID_KEY_PASSWORD='…'
./gradlew testDebugUnitTest lintRelease assembleRelease bundleRelease
```

Artifacts:

- APK: `app/build/outputs/apk/release/app-release.apk` (local testing / sideload only)
- AAB: `app/build/outputs/bundle/release/app-release.aab` — **the only artifact uploaded to Google Play**

Signature check:

```bash
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab | tail -5
```

## R8 / ProGuard

Release is built **non-minified first** (current default). After the signed non-minified release is verified on a device, enable shrinking with `-PsiptimerMinify=true` (R8 + resource shrinking), repeat the signature/permission/16 KB checks and keep `app/build/outputs/mapping/release/mapping.txt` (CI uploads it).

## 16 KB page size

SipTimer has no native code of its own. Dependencies used (Compose, Room, DataStore, AndroidX, coroutines) are pure Java/Kotlin on Android — the bundled SQLite is the platform one. CI lists every `.so` in the APK and AAB and runs `zipalign -c -P 16`; if any native library appears, the ELF `LOAD` segment alignment must be ≥ 2**14 or CI fails. Runtime compatibility on a real 16 KB device/emulator is **pending** (API 36 alone does not prove it).

## Install on a device and check logcat

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c && adb shell am start -n com.egrmeister.siptimer/.MainActivity
adb logcat -s SipTimer:* AndroidRuntime:E ActivityManager:I
adb shell dumpsys alarm | grep -A3 com.egrmeister.siptimer     # pending inexact alarm
adb shell cmd package query-receivers --components -a android.intent.action.BOOT_COMPLETED | grep siptimer
```

The app logs schedule transitions and alarm callbacks under the `SipTimer` tag (no personal data).

## Verification status

Done in development:

- Kotlin compilation of all sources and unit tests (Kotlin 2.0 compiler against Compose 1.7 / Material3 1.3.1 libraries, Room replaced by compile stubs).
- 41 focused JVM unit tests pass: active-window boundaries, first reminder timing, logging-driven restart, historical entries not restarting, Skip advancement and duplicate processing, goal pause/resume, stale/obsolete callback rejection, permission denial, countdown recovery, midnight, clock change, time-zone change, DST gap and overlap, data changes (edit/delete/Undo), input validation, effective-dated goals.

Pending (to be filled in after CI and device runs):

| Check | Status |
|---|---|
| CI green (tests, release lint, signed APK + AAB, apksigner, jarsigner, permissions, 16 KB) | pending |
| Release artifact sizes (APK / AAB) | pending — reported in the CI job summary |
| Room schema `1.json` committed | pending (generated by first build) |
| `adb install` of signed release APK + logcat review | pending — no device/emulator attached to the build environment |
| Offline launch, countdown, logging, Skip, goals, notifications, reboot recovery, process recreation, accessibility (TalkBack, font scale 200 %), rotation, reset | pending (device) |
| 16 KB runtime test on a 16 KB device/emulator | pending |
| R8 / resource shrinking build + repeat checks | pending (after non-minified release is verified) |

Record for each device run: device model, Android version, artifact size, results.
