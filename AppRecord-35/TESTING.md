# AppRecord-30 verification

Run with JDK17 and configured Android SDK:

```
./gradlew --max-workers=2 :domain:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintRelease
```

Tests include all seven nonempty source combinations/background choices, bounded/saved frame coordinates, raw/composite distinct names/archive inclusion, actual PCM level/silence selection and saturating mix, config/recovery persistence, reserved resource permissions, unlimited deadline/reboot overflow, Home fifth mode and locked colors, frame move/resize/hide, unlimited future arming, configurable archive initial limit with 2444 records, blocked provider scans with concurrent config reads, prior camera/photo/audio/archive/player/schedule/retention behavior.

Native View PNGs use Robolectric native graphics fixtures and are not phone screenshots. Test counts/build/lint/signature/metadata are recorded by packaging in BUILD_VERIFICATION.txt and verification/*.xml/txt/json. Tests do not execute real Camera2/AudioPlaybackCapture/MediaProjection/MediaCodec output.

Required phone checks: Screen audio none/ambient/internal/both; app opt-out playback; real mix synchronization and PCM quality; all source combinations and alternative backgrounds; original versus composite files; moving/resizing/hiding/adding/removing sources and stopping during graph rebuild; system sharing Stop; floating control grant/dismiss/background; 120-minute and unlimited endurance; scheduled start consent; thermal/storage/battery interruptions; Samsung Android16 competition/permission behavior; rotation and codec throughput. Check outputs externally, not only in-app previews.

Additional tests: exact 73% native battery fill; equal visible header heights and no shared tools frame; six controls plus bookmark across all five modes; dedicated timing validation/save; manual source-icon connection behavior and placement; local bookmark persistence; real sine/silence/full-scale spectrum/dBFS; same-file AAC pause with dropped paused/delayed packets; persistent user pause, manual recovery and unchanged/expired deadlines; direct Screen background selection/presets; non-modal floating-window size/flags, live camera TextureView wiring, Screen outline, source/background changes and inset hiding without stopping raw capture.

Before relying on AppRecord-29 on a phone, test Mixed → Screen + Front → Record → overlay grant/screen consent → launch another app: live front inset, drag/resize/hide, expand/fold toolbar, background/source changes, audio toggles, Pause/Resume/Stop; verify saved original and composite playback. Test Screen + Both only if concurrent cameras/encoders are supported. Check raw Screen overlay inclusion, MediaCodec decoder allocation, Android 16 permission/start restrictions, screen orientation and microphone contention. Test manual pause at an explicit deadline, and all five mode outputs. None of these live hardware checks were performed here.

## AppRecord 29 coverage

Recorder24UiTest verifies decoded stereo passthrough/true spectrum/silence, standalone component fractions, matching audio transport/counter/spectrum visible edges, dedicated Battery details and noninteractive formula and analog gauges, Archive Folder/empty Select selection, and new-session Screen-stop playback. MixedSetupTest covers default (0,0) Front inset and preservation of explicit user layout. Existing playback navigation/speed and multi-type pin/deletion tests remain.

Real phone checks still required: decoded audio spectrum aligned with audible content at all rates and seeks, surface border under rotation/letterboxing, Screen finalization -> auto playback, charging pulse/source choice and hotplug refresh, reusable AAR integration in another project, and inherited mixed multi-camera/screen/overlay/thermal/background endurance. PNGs are native Android View fixtures, not phone screenshots.

## Version 25 checks
Recorder25UiTest covers scheduled clearing/animated clock state, observer-safe PCM passthrough, pause-aware marks, three analog gauges and black/white resolution ruler. CaptureContainerTest verifies retained video muxer identity and compact timestamps; AAC pause appends to one file. All UI fixtures are Robolectric renders, not phone screenshots. ERROR_CODE_FAILED_RUNTIME_CHECK on the reported Samsung is not reproduced on physical hardware; compatibility retry and diagnostic logging require device validation.

## AppRecord 29 verification
Recorder26UiTest covers recorded Camera2 request-key writes with a test request shadow (the platform metadata native stub does not preserve builder values), supported/unsupported rotate-and-crop policies, portrait input size mapping for a 270° front sensor, centered gauges and persisted °C/°F conversion, equal archive header squares, multi-URI clipboard and ACTION_SEND_MULTIPLE read grants, single power panel order, colon labels, independent Play/Pause and 15-second paused seek, normal-speed reset. Camera requests are simulated, not actual frames. PNGs are native Android View fixtures, not device screenshots. Hardware portrait orientation and external file-manager Paste remain unverified.

# AppRecord 29 — 2026-10-09

Camera producer quarter-turn compensation in Native and Mixed EGL paths (screen source excluded); physical-phone verification remains required. Idle Pause is visibly yellow but enabled only during capture. Armed Schedule uses cyan hands, orange frame and a longer 12-second pulse. Audio viewer adds reusable glossy control rails.

High-resolution Trim releases the main player before opening, skips thumbnails above 1080p (720p on low-RAM devices), and creates preview only on request after thumbnail loading. Bitmap failures are logged; Save returns to Files. Single-file cuts preserve their source video codec and request Media3 trim optimization, which may fall back to encoding. Export errors enter diagnostic history. Phone exit cause is not established without its crash report.

Battery/temperature frames are equally narrow and green; charging source glyphs match value-frame height. Power meters have equal visible gaps, longer needles and underlight; formula strip is flush with the panel border. Only a connection plug is shown; wall adapter versus power bank is not classified.


# AppRecord 29 — 2026-10-09

Home begins with capture modes. Capture status is inside the card title strip; transport contains Record/Pause/Stop/Play. Schedule, Folder and Mark live below the card; health and Guide/Information/Settings form the last row. Tap Schedule for start/duration; hold for the advanced Start/End schedule. Quality value and ruler share a connected black component below each camera monitor. The active mode pulses during recording and stops pulsing while paused.

Viewer metadata has a black frame. Trim moves to the second actions row, with a larger inter-row gap for video/photo/audio. Play at end seeks to zero before resuming, including companion synchronization in the player backend. Original files and timing behavior are preserved.


# AppRecord 29 — 2026-10-09

Battery: one horizontal connection plug, no power-bank selector; green 3dp value frames, more vertical separation and a taller reading card. A muted capacity label shows an explicitly estimated full capacity only when valid charge-counter and percentage data are available. Battery title toggles between gauges and electrical details. Three power meters have equal 18dp visible outer/inter-meter spacing and brighter concealed underlight. Audio spectrum/counter have green outlines; Home status is fixed at two lines / 56dp.

Screen now requests overlay permission, minimizes after recording starts and has a protected movable Record/Resume, Pause, Stop toolbar. Mixed toolbar and inset header/resize controls are separate FLAG_SECURE windows; camera preview surfaces remain separate. Main Activity is protected while a projection session runs. Android/OEMs may mask protected areas black rather than reveal underlying content; clean exclusion is NOT hardware-verified. Android sharing consent/foreground notification remain mandatory. Floating Stop waits for finalized Screen/Mixed output, then opens that specific file rather than whichever camera finished last. Real phone background launch, capture exclusion and live recording remain untested.



# AppRecord 30 — 2026-10-09

- Battery: numeric estimated mAh with small unit; percent toggles approximate remaining Wh (blue), temperature toggles Fahrenheit (green). Thicker green reading frames, doubled body/number spacing, wider horizontal charging plug, longer pins, animated green cable/current dashes and sharp phosphor-green connection pulse without glyph blur. Capacity is not W and no charger rating is invented.
- Power: stronger four-sided yellow meter underlight, equal 18dp visible outer/inter-meter gaps, lower unit labels, tap V/A/W for local timestamped history. Samples every 10 seconds while the app or capture service runs, up to six hours, with gaps for unavailable data. Battery temperature warning at 45°C beeps once per heating episode, re-arms at 42°C and opens the thermometer when foreground; background opening is deferred until returning to the app. This threshold is an app warning, not a universal manufacturer limit.
- Timing/audio: monotonic active capture counter freezes for user pause, automatic interruption, startup and Stop; same counter in audio and ordinary Screen Home and floating controls. Visual spectrum gain emphasizes quiet sound, without changing PCM, RMS, peak or actual clipping. Photo interval dialog includes minutes duration (empty unlimited).
- Home: white normal, gray disabled, green working/selected icons; Record red during capture, Pause/Stop yellow when available and gray before capture, Play green when playing. Armed clock uses larger faster moving hands and gradual pulse without a special colored frame. Fixed one-line status; increased equal gaps around monitor/quality groups, 16dp monitor-to-quality gap and clipped rounded quality component. Swipe a monitor/background horizontally to switch idle capture modes; upward swipe opens Files. Sliders/editable controls keep their own gestures.
- Archive/playback: latest 100 files by default and on migration from the old default 30. Select All reconciles ALL completed actual files in the background and includes older files outside the display cap. Deselect All clears selection. Two viewer rows share one box and seven equal square cells for photo/audio/video/screen/composite; Files leads the second row. Existing audio glossy rails remain.
- Screen: floating panel has only elapsed time, Pause/Resume toggle and Stop, in one transparent white frame with aligned controls. Stop finalizes output before playback. Settings also offers notification controls (floating controls off): Pause/Resume and Stop without creating an overlay or its black mask. Protected floating overlays can still be black-masked by Android; clean background-preserving overlay exclusion is NOT claimed. Android consent and mandatory privacy/foreground indicators remain.
- Orientation: camera texture decomposition now handles an existing 180° producer transform instead of treating it as zero, with synthetic crop/mirror/quarter-turn regressions. Actual front-camera upright output still needs a phone test.

Physical device camera/encoder/MediaProjection exclusion/background auto-play, long recording, thermal alert sound and real battery sensor accuracy have not been tested. Public Play Store release needs a production key, current Play policy/target-SDK review, privacy declarations and multi-device validation; this is a development-signed preview.


## AppRecord 31 — 2026-10-09
Notification controls are now the default for Screen and Mixed recording. Startup migrates the previous floating default once; later manual choices survive restart. No floating control or camera windows are created in notification mode; composite layout remains configured in Home. Pause/Resume and Stop are available in the recording notification, with an active chronometer and frozen paused time. Stop opens the finalized recording. Notification permission and the recording channel are checked before capture; Android capture consent remains required. Opening the notification shade can appear in the recording. Physical-device behavior still requires validation.


## AppRecord 32 — 2026-10-09
Battery cable now turns directly down into a battery cell, with a triple-width green cable and visible red moving dashes. Charger pins are 10% shorter; matched number and charger frames are 44dp high inside taller battery/thermometer cards. Percent toggles estimated remaining energy in Wh, not adapter power. Power-history dialogs have no header and one Close action, with a yellow plot frame and red text. Other dialogs with a bottom Close do not duplicate the top Close icon. Five Home mode choices use yellow/green; active camera panel frames are green. Home swipes also cover surrounding non-control space; horizontal photo-viewer swipes navigate photos chronologically. Interactive controls retain their own gestures. Device capture behavior was not physically tested.


## AppRecord 33 — 2026-10-09
Charging cable has one bend aligned to a battery-cell centre and ends at that cell's upper edge. Estimated remaining energy now shows a single number without the Wh suffix; accessibility/Guide still identify watt-hours. Battery and thermometer reading frames start at a matched 104dp and grow to fit with padding. Yellow power-chart border removed. Home order is Video, Audio, Photo, Screen, Mixed. Camera buttons choose sources while idle without starting capture; Record starts the selected sources. Camera border belongs only to its monitor. Available frames are white, initial choices yellow, recording green and unavailable gray. Record glyph stays red, Play glyph stays green. Front-camera input receives a 180-degree orientation correction in the shared encoding/preview path; physical validation remains required. Explicit paused seeking takes precedence over end-of-file replay, both in Home playback and the ExoPlayer component. Tests cover non-recording camera selection and monitor-only frames, numeric one-line energy sizing and selected audio playback position.


## AppRecord 34 — 2026-10-10
Battery/temperature cards are compact (172dp); number frames sit 8dp from the inner top. The charger is upright, aligned to the centre of battery cell index 8 (second from right), with a straight cable and moving black segments. Percent/energy and C/F keep phosphor-green digits and matched green frames. Percent has white concealed underlight. Capacity mAh and remaining energy Wh use matching 40% units above their 26sp numbers. Estimated remaining energy remains an estimate, not charger wattage. Launcher and round launcher use an adaptive camera icon. All five capture modes use one Record/Pause/Resume toggle plus separate Stop and Play. Requested state glyphs: Record red during recording; Pause yellow while paused. Playback merges Play/Pause into one control; file navigation and Stop remain. Notification already has one combined Pause/Resume action; mandatory Android notification and capture consent remain. Tests exercise pause/resume service actions across all five modes, combined playback behavior and compact cable/number/unit geometry. Physical camera/capture/background behavior is not tested on a phone.
