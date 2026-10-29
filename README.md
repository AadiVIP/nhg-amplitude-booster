# NHG Amplitude Booster

Android audio amplifier that applies uniform gain to the **global output mix
(audio session 0)** using only public `android.media.audiofx` APIs, plus a
bit-exact in-app test-tone engine for measuring the DSP chain.

| | |
|---|---|
| Brand | No Headphone Gamerz (NHG) |
| Application ID | `com.noheadphonegamerz.amplitudebooster` (debug builds add `.debug`) |
| Version | `versionName 1.0.0`, `versionCode 1` |
| Languages | Kotlin, Jetpack Compose + Material 3 |
| SDK range | `minSdk 28` (Android 9) → `targetSdk/compileSdk 36` (Android 16) |
| Network | None. No `INTERNET` permission, no telemetry, no analytics. |
| Microphone | None. No `RECORD_AUDIO`; the app never captures from a mic. |
| Licence | Apache-2.0 — see [`LICENSE`](LICENSE) and section 11. |

> **Verification status, stated up front.** On 2026-10-30 the owner manually
> confirmed on a vivo V25 5G (Android 15, API 35) that turning the boost up
> really does raise the playback level of audio coming from *other* apps, and
> that turning it off returns them to normal. The session-0 path therefore works
> end to end on that handset. Everything else is still a caution, not a claim: it
> is one device, confirmed by hand rather than with acoustic instrumentation
> recorded here, and which output actually hosts session 0 is decided by each
> device's audio policy manager. Behaviour can differ by handset, Android build,
> vendor audio HAL and current routing. The 59 automated tests in this repo are
> JVM unit tests — they prove the DSP maths and the effect state machine, never
> the acoustics. Section 7 is the protocol for checking your own device; do not
> assume it matches the reference result until you have run it.

---

## 1. What it does

* **Master boost 0 → +15 dB** (0.5 dB steps, presets at 0 / +6 / +12 dB) applied
  to the global output mix, i.e. the audio Android mixes on its way to the
  speaker — the one session every app's media shares, so no per-app session ID
  has to be discovered.
* **Uniform scalar gain only:** `y[n] = g · x[n]`, `g = 10^(gainDb/20)`.
  No EQ, no bass/treble shelf, no pitch shift, no harmonics, no stereo widening,
  no loudness contour, no sample-rate conversion.
* **In-app test tone generator** that synthesises calibrated PCM straight into
  `AudioTrack` through a documented DSP engine with a stereo-linked peak
  limiter, so the gain maths can be measured rather than asserted.

## 2. How the global boost works

`AmplitudeBoosterService` (foreground, `specialUse`) owns an
`AudioEffectAdapter` built on **audio session 0** — `AUDIO_SESSION_OUTPUT_MIX`,
the global output mix. Session 0 is a fixed, well-known identifier, so **no
discovery of other apps' sessions is needed** and no privileged access is used.

The adapter tries three public effect APIs in order and keeps the first that
constructs (`audio/AudioEffectAdapter.kt`, `DefaultAudioEffectBackend.attach`):

1. `LoudnessEnhancer(0)` → `setTargetGain(millibels)`
2. `DynamicsProcessing(0)` → input gain, all channels
3. `Equalizer(0, 0)` → all bands raised to the requested level

At 0 dB the effect is left **disabled** (bypass): the output mix is untouched.

**What actually gates this on Android 15/16.** In AOSP, attaching an effect to
session 0 is checked by `settingsAllowed()`, which accepts a caller that holds
`android.permission.MODIFY_AUDIO_SETTINGS` — an ordinary install-time permission
that this app declares. `MODIFY_AUDIO_ROUTING` (signature-level) is *not*
required for this path. `LoudnessEnhancer` logs a "attaching … to global output
mix is deprecated" warning on session 0; it remains functional. Reference:
AOSP `service/audio/ServiceUtilities.cpp` (`settingsAllowed`) and
`service/audio/AudioFlinger.cpp` (`if (sessionId == AUDIO_SESSION_OUTPUT_MIX)`
in the effect-creation path), plus `media/java/android/media/audiofx/LoudnessEnhancer.java`.

**No Shizuku, no ADB, no root, no accessibility service, no
MediaProjection/AudioPlaybackCapture.** RootlessJamesDSP-style capture was
rejected for this product because replaying another app's audio adds roughly
100–300 ms of latency, risks echo/feedback, and fails entirely for apps that set
`allowAudioPlaybackCapture="false"`. A one-line Shizuku *presence* probe exists
in the diagnostics screen; see the caveat in section 6.

## 3. State model (the app reports what the framework told it)

`BoosterStatus` in `audio/BoosterState.kt`:

| State | Meaning |
|---|---|
| `OFF` | No effect held; "System output mix is unmodified." |
| `STARTING` | Attachment in progress. |
| `ACTIVE` | **Only** after API readback confirms `getEnabled() == true` **and** `hasControl() == true`. |
| `ERROR` | Construction or control failed. The exact exception strings from every attempted backend are kept in `lastError` and shown in the UI. |

If an effect is created but readback fails, the adapter **releases it** and
reports `ERROR` rather than claiming success. `setControlStatusListener` /
`setEnableStatusListener` downgrade the state if another app or the system takes
the effect away. Requested gain is clamped to `[0.0, 15.0]` dB before anything
is sent to the framework.

## 4. Permissions

Declared in `app/src/main/AndroidManifest.xml`; all are normal/install-time, none
are runtime prompts:

| Permission | Why |
|---|---|
| `MODIFY_AUDIO_SETTINGS` | Required for the session-0 effect path (section 2). |
| `FOREGROUND_SERVICE` | Hosts the effect in a foreground service. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ FGS type for the effect service; subtype property explains the use. |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ FGS type for the in-app test tone. |
| `POST_NOTIFICATIONS` | Ongoing notification with a **Stop Boost** action. |

No `INTERNET`, no `RECORD_AUDIO`, no `WRITE_EXTERNAL_STORAGE`, no `DUMP`.
The launcher `Activity` is exported (it must be); the service is
`android:exported="false"`.

## 5. Known limitations

* **Device and OS variability — no compatibility claim.** Which output actually
  hosts session 0 is chosen by the audio policy manager, not by the app. Effects
  on the output mix can also migrate between threads when the policy changes.
  Consequence: which streams are boosted, and whether boost is audible at all,
  differs by handset, Android version, vendor audio HAL, and even by the current
  routing state. **One handset is confirmed working (vivo V25 5G, Android 15,
  owner-performed manual test). No other device, and no compatibility list, is
  claimed.**
* **Not all audio is necessarily covered.** Whatever the policy routes through
  the affected mix is boosted. Streams on other mixes — and, on some devices,
  calls, alarms or system sounds — may be unaffected. The device's own volume
  and mute state still bound the result; digital gain cannot exceed hardware
  limits.
* **Contention is real.** Only one app can hold a given effect on a session. If
  a system or vendor equalizer already owns the output mix, the constructor can
  fail or `hasControl()` can be false — the app then reports `ERROR` /
  `UNAVAILABLE` instead of silently doing nothing.
* **LoudnessEnhancer has no gain ceiling check.** Anything you send is applied
  by the framework; AOSP then runs it through an adaptive limiter and clamps to
  16-bit range. That is why the UI is capped at +15 dB rather than exposing the
  unbounded parameter.
* **No reboot autostart, no quick-settings tile, no per-app gain.** The boost is
  a single master value for the whole output mix.
* **The `ACTIVE` state means "the framework reports the effect enabled and
  under our control"** — it is API readback, not an acoustic measurement.
* **Debug builds are signed with the standard Android debug key** and get the
  `.debug` applicationId suffix. Release builds need the private keystore
  described in section 9; without it `assembleRelease` is unsigned, and no
  release artefact in this repository is debug-key signed.

## 6. Safety considerations

* **Speaker and hearing risk.** +6 dB is roughly a doubling of acoustic power
  demand; small phone and laptop speakers, and passive earbuds, can distort or
  be damaged. The UI shows a persistent caution at high gain. Start low, and
  stop if the output sounds clipped or strained.
* **Clipping downstream of us.** Our gain is applied to Android's mix. The
  device's own DSP may add further processing; a hard-clipped signal can sound
  "louder" while destroying detail. If the waveform audibly crackles, reduce the
  gain.
* **Diagnostic-only reflection caveat.** `AudioBackendManager.probeShizuku()`
  uses `Class.forName("android.os.ServiceManager")`, a non-SDK API. On recent
  Android this lookup is expected to be blocked, and the probe then reports
  "Service not detected" — which is not evidence about Shizuku. That row is
  informational only and is not used for any capability decision. It is a known
  wart, not a supported feature.
* **Behaviour on battery/DCS:** the foreground service keeps a notification so
  the boost is always visibly active and always one tap from off.

## 7. Manual test protocol (this is what proves it)

1. Install an APK — the release-signed one attached to the GitHub Release, or
   your own `./gradlew assembleRelease` / `assembleDebug` output
   (`adb install -r app/build/outputs/apk/release/app-release.apk`). Debug
   builds carry the `.debug` application ID, so they install *alongside* a
   release build rather than replacing it.
2. Start continuous audio in **another** app — music, a video, a game.
3. Open NHG Amplitude Booster, set gain to **0 dB**, turn **Master Audio Boost**
   on. Status should read `ACTIVE (SESSION 0)` with a bypass note.
4. Raise gain in steps (3 → 6 → 12 dB). Listen for a monotonic level increase.
5. Turn it off; audio must return to its original level.
6. Lock the screen for ~30 s, unlock, and confirm the status and the audible
   level are unchanged.
7. Repeat with another equalizer/loudness app running to test control contention.
8. Open **Settings → Device capability audit** if anything fails: it enumerates
   `AudioEffect.queryEffects()` and the session-0 constructor result, including
   the exception text.

**Reference result (2026-10-30).** vivo V25 5G, Android 15 (API 35), run by the
owner: enabling the master boost increased the playback level of audio coming
from other apps. Which backend held the session and how completely each stream
type was covered were not recorded for that run, so those remain open questions
even on the reference handset.

Record, for any device you report on: handset model, Android/API level, which
backend became active, and whether the change was audible. Claims of
compatibility beyond what you measured are not supported by this project.

## 8. Repository layout

```
LICENSE                                      Apache-2.0 licence text
app/build.gradle.kts                         SDK levels, versions, release signing wiring
app/keystore.properties.example              signing parameter template (real file stays outside Git)
app/src/main/AndroidManifest.xml           permissions, service, FGS types
app/src/main/kotlin/.../MainActivity.kt    Compose entry point, state wiring
  audio/AmplitudeBoosterService.kt         foreground service, notification, persistence
  audio/AudioEffectAdapter.kt              session-0 effect chain, state machine, readback
  audio/AudioBackendManager.kt             capability audit (effect list, session-0 probe)
  audio/AudioPlayerEngine.kt               AudioTrack test player
  audio/BoosterState.kt, BoosterStateHub.kt  observable state model
  dsp/DspProcessor.kt, DspConfig.kt        scalar gain + stereo-linked limiter
  dsp/TruePeakInvestigation.kt             ITU-R BS.1770-4 4x oversampled ISP meter
  dsp/DspStatistics.kt                       telemetry (gain reduction, limited frames)
  ui/…                                      MainScreen, SetupScreen, SettingsSheet,
                                            DiagnosticsScreen, AboutScreen, ToneGeneratorScreen
app/src/test/kotlin/…                     59 JVM unit tests (see section 9)
```

## 9. Building and testing

**Prerequisites:** JDK 17 or 21; Android SDK with platform `android-36`
(build-tools 34.0.0 or newer); the Gradle wrapper is checked in (Gradle 9.8.1),
so Gradle itself does not need to be installed. Set your SDK path either in
`local.properties` (`sdk.dir=…`, git-ignored) or via `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest        # 59 unit tests
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
                                   # signed only if release signing is configured (see below)
```

On unusual toolchains (Termux/Android-on-ARM dev boxes, some containers) the
prebuilt `aapt2` shipped with the SDK may not run. In that case point Gradle at
a working `aapt2` for that invocation, without baking a machine path into the
repo:

```bash
./gradlew assembleDebug -Pandroid.aapt2FromMavenOverride=/path/to/working/aapt2
```

### Release signing

Release builds use a real private key. The signing parameters are read at
configuration time from a properties file, first match wins:
`NHG_RELEASE_SIGNING_PROPERTIES`, then `-Pnhg.release.signingProperties=…`, then
a git-ignored `app/keystore.properties`. [`app/keystore.properties.example`](app/keystore.properties.example)
lists the four keys (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`).
If none resolves, the build prints "release signing: NOT configured" and
`assembleRelease` yields an **unsigned** APK — it never quietly falls back to the
debug key.

The keystore and the properties file belong outside the working tree, with `0600`
permissions. `*.jks`, `*.keystore`, `*.p12`, `*.pem` and `keystore.properties`
are all git-ignored. Create the key once, keeping the passwords off the command
line and out of shell history — `keytool` reads them from files:

```bash
mkdir -p ~/.nhg-release && chmod 700 ~/.nhg-release
# write both passwords into separate 0600 files with an editor, then:
keytool -genkeypair -v \
  -keystore ~/.nhg-release/nhg-release.p12 -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 9125 \
  -alias nhg-release \
  -dname "CN=NHG Amplitude Booster, OU=Release, O=No Headphone Gamerz" \
  -storepass:file ~/.nhg-release/store.pass -keypass:file ~/.nhg-release/key.pass
shred -u ~/.nhg-release/store.pass ~/.nhg-release/key.pass   # or delete them
```

Then check who signed a built APK before publishing it:

```bash
apksigner verify --print-certs --verbose app/build/outputs/apk/release/app-release.apk
aapt2 dump badging app/build/outputs/apk/release/app-release.apk | head -2
```

**Back the keystore and both passwords up somewhere that survives a lost phone
or a rebuilt machine.** The signing certificate *is* the app's identity to
Android: if it is lost, no update can be installed over an existing install —
every user would have to uninstall and reinstall, because Android rejects an APK
whose signer differs. Recovery is impossible after the fact, so treat the key as
more important than the source. Use a strong random password; the key file alone
is worthless without it, and the pair together must never be committed.

APKs, `build/` output, `local.properties`, IDE files and Gradle caches are
git-ignored; this repository publishes **source**, not binaries.

### What the tests cover, and what they don't

`app/build/test-results/testDebugUnitTest/` after a run — 59 tests, currently
0 failures:

| Suite | Tests | Covers |
|---|---|---|
| `dsp/DspProcessorTest` | 28 | uniform linear scaling, bit-exact 0 dB, limiter continuity across buffers, asymmetric stereo peaks, near-ceiling PCM, bypass identity, NaN/Inf sanitising, overflow at ±32767/−32768, statistics telemetry |
| `audio/AudioEffectAdapterTest` | 11 | gain clamping, mB conversion, and every state transition of the effect state machine against a fake backend — including "created but no control ⇒ ERROR + release" |
| `audio/AudioPlayerEngineTest` | 5 | engine configuration and lifecycle |
| `audio/BoosterStateHubTest` | 5 | observable state hub publication/deduplication |
| `dsp/AudioQualityTest` | 5 | THD in the linear zone, frequency flatness, latency |
| `audio/PhaseWrapTest` | 2 | phase-wrap equivalence in tone synthesis |
| `dsp/DspProcessorConfigTest` | 3 | config snapshot semantics |

These are **pure JVM** tests (no Android framework, no device). A green suite
means the maths and the state machine are correct; it says nothing about whether
a given firmware lets a third-party app hold the output mix, and nothing about
acoustic loudness. That second question was answered by hand on one handset
(section 7) — run it on yours before assuming the same result.

## 10. Documentation

The design rationale lives in this README: section 2 (why session 0, why the
three-backend chain, why capture/replay and privileged injection were rejected),
section 3 (state model and why `ACTIVE` requires readback), section 5 (known
limitations) and section 7 (the manual protocol that has to be run on real
hardware before any loudness claim can be made). Device-specific probe logs and
third-party app audits are kept out of this repository on purpose.

## 11. Licence and attribution

Copyright © 2026 No Headphone Gamerz (NHG).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use
these files except in compliance with the License. You may obtain a copy of the
License at <http://www.apache.org/licenses/LICENSE-2.0>. Unless required by
applicable law or agreed to in writing, software distributed under the License
is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the specific language
governing permissions and limitations under the License.

The complete licence text is in [`LICENSE`](LICENSE) and covers this repository's
own Kotlin, XML, Gradle and documentation files.

### Third-party components

Nothing is vendored. Every dependency is resolved from `google()` and
`mavenCentral()` at build time and keeps its own licence, which this project's
licence does not override.

| Component | Licence | Where |
|---|---|---|
| AndroidX — core, activity, lifecycle, annotation, arch.core, collection, concurrent, emoji2, graphics, interpolator, profileinstaller, savedstate, startup, tracing, versionedparcelable, autofill, customview | Apache-2.0 | runtime dependencies |
| Jetpack Compose — ui, foundation, material, material3, runtime, animation | Apache-2.0 | runtime dependencies |
| Kotlin stdlib, kotlinx.coroutines, `org.jetbrains:annotations` | Apache-2.0 | transitive |
| `com.google.guava:listenablefuture` (stub) | Apache-2.0 | transitive |
| JUnit 4.13.2 | EPL-1.0 | `testImplementation` only — not shipped in any APK |
| Gradle wrapper jar | Apache-2.0 | Gradle, Inc. artifact, unmodified |

`android.media.audiofx.*` (`LoudnessEnhancer`, `DynamicsProcessing`,
`Equalizer`) and the rest of the Android framework are public platform APIs used
as documented; no Android framework source is included here.

### Provenance

This code was written against the public Android API surface and the AOSP
behaviour described in section 2. Two external projects served as reference
points; nothing from either is incorporated:

* **GOODEV Volume Booster** — a third-party commercial app looked at during
  pre-architecture research purely as a black box: a publicly distributed APK
  was inspected statically (manifest and effect-chain enumeration) to see what a
  shipping booster declares. No decompiled code, resource, asset, string or
  translation from it exists in this repository, and it makes no contribution
  that could be credited or relicensed.
* **RootlessJamesDSP** (upstream GPL-3.0; native `libjamesdsp` GPL-2.0-only) —
  audited to decide whether its capture/replay approach should be adopted. It
  was not, for the latency and coverage reasons in section 2. Comparing this
  repository's 2,235 meaningful source lines against that upstream's 207 Kotlin
  files produced 19 identical lines, all unavoidable Android boilerplate
  (`override fun onCreate(savedInstanceState: Bundle?) {`,
  `inner class LocalBinder : Binder() {`, `AudioAttributes.USAGE_MEDIA` builder
  calls). No upstream file name, class or DSP chain corresponds to anything in
  `dsp/` or `audio/`. No GPL-licensed code is present, so nothing in this
  repository carries a copyleft obligation.

The launcher icon is an original hand-authored vector — a speaker-cone path plus
two stroked arcs — not a template or imported asset.
