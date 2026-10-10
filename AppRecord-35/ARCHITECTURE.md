# AppRecord-23 architecture

- domain: recording modes, source IDs including MIXED, output naming, interval/duration/quality/storage policies, mixed normalized frame layout, saturating PCM mix/actual level measurement, archive grouping, chronological navigation and timeline export plans.
- contracts: repository/factory/engine/player/export interfaces; CaptureListener carries camera/microphone/light state, actual PCM levels/spectrum, pause acknowledgments, completed files and stop/error reasons.
- data: MediaStore/optional SAF storage and indexed SQLite metadata. Config persists independent sources, composite background/layout, two screen-audio selections, archive refresh/count and duration 0 (unlimited). Provider discovery is outside repository locks; recent reconciliation limits expensive file inspection, while full Refresh scans all.
- capture: NativeDualEngine preserves camera/photo behavior. AudioOnlyEngine reports actual input PCM levels. ScreenCaptureEngine obtains one MediaProjection, creates at most one VirtualDisplay, opens selected concurrent Camera2 inputs, creates one VideoEncoder/RollingMuxer per original source and, in Mixed, one for the composed result. MixedCompositor owns external textures and EGL windows, rendering raw and composed views with common monotonic timestamps. ScreenAudioEncoder continuously encodes 48 kHz mono AAC from individually switchable ambient and allowed playback capture; inactive inputs produce zero PCM. Input switches preserve the AAC timeline. Source changes close/finalize the graph and rebuild it around the same projection/display and origin; short gaps are possible. EncodedPreview decodes actual encoded samples for the Home composite preview.
- app: foreground resource permissions/types are reserved before background work. CaptureRecovery stores original elapsed/wall end times; Long.MAX_VALUE represents unlimited, with explicit overflow handling. Screen/Mixed cannot reuse a consent token after session loss. MixedFloatingControls is a layout diagram/controller over other apps, not an additional live source overlay. MainActivity stores debounced layout edits; schedules persist modes/sources, prompt in restricted background cases and require screen consent. Stop reasons persist in runtime preferences; diagnostics reconstruct accessible historical process exits.
- player: Media3 playback supports chronological/all-type and same-type/source navigation. Speed is retained across file changes; video audio muted above 8×.
- editor: Media3 Transformer concatenates planned chronological clipped videos into H.264/AAC output; photos and standalone audio are excluded from time-range exports. Trim/export creates protected copies.

Android whole-display capture can include visible application/controller UI. No API bypasses protected content, app playback-capture opt-outs, microphone prioritization, foreground startup restrictions, force-stop or fresh screen-consent requirements. Hardware concurrency, mixer quality and endurance require device validation.

Permanent UI rules are in AGENTS.md and design/UI Neon.md. All numbered deliveries are immutable; Preview has a development signature distinct from the original app.

AppRecord-23 changes: CaptureEngine.setPaused / CaptureListener.onPauseChanged and distinct USER_PAUSED/PAUSING phases; durable recovery.userPaused blocks automatic retry until Resume. RollingMuxer pause finalizes output, clears pending queues, drops all pause packets and rejects delayed pre-resume timestamps. Resume begins at a fresh IDR; camera/audio encoding remains active. JPEG exposure triggers are gated independently. The original deadline is unchanged.

PcmMeter computes a Hann-windowed 1024-point mono FFT, 20 logarithmic bands, RMS/peak dBFS and full-scale detection. AudioOnly forwards throttled real PCM statistics; AudioLevelView renders segments, peak hold and clipping. CaptureMarkers persists bounded local wall-clock/session/source bookmarks and resolves completed file seeks.

MixedSetup atomically includes a chosen background and validates source removal. Mixed video caps supported camera sizes to 1280×720 bounds using the exact selected IDs before validating their joint Camera2 configuration. MixedFloatingControls uses separate non-modal overlay windows and raw camera EncodedPreview surfaces, a collapsed bubble and a timed compact toolbar; Screen insets are transparent positioning outlines to avoid screen feedback. DuoApp.uiVisible removes overlays while MainActivity is shown; successful screen-backed starts move the activity to the background. Visible overlay content is part of MediaProjection's raw Screen source. This is not Android Activity PiP and does not bypass SYSTEM_ALERT_WINDOW consent.

ChargingSourceChoice records explicit wall/power-bank labels rather than guessing from AC/USB. Only actual Android connection activates the chosen icon. Battery cells clip their green portion to the exact integer percent; no extra subdivisions are drawn. Header tool faces match badge visible height without an enclosing frame.

## AppRecord 24 reusable presentation and decoded audio

`ui-components` is an Android library exposing AudioSpectrumView, GaugeDrawing, GaugeView and ElapsedCounterView. It imports no app-layer classes; `domain` supplies AudioMeter/PcmMeter. App HealthGauge remains an adapter from DeviceHealth to shared geometry. App AudioLevelView is a typealias for the public spectrum view. Sensor acquisition, session timing, repository and lifecycle stay with their owning app.

NativePlayer's DefaultAudioSink uses PlaybackMeterProcessor: passthrough PCM16, meter-only channel averaging, FFT after complete 1024-sample blocks, reset on flush. PlayerComponent exposes isPlaying/audioMeter with compatible defaults for optional modules. Master play/pause/Stop control paired playback; PlayerViews have useController=false. Green outlines derive fitted surface bounds, not whole player bounds. Audio playback substitutes the decoded meter for a black monitor and polls only while the activity is started.

Battery status separates gauge cards and collapsible electrical metrics; Battery header opens a dedicated metrics page. Charging source remains explicit manual metadata with connected-only pulsing. Auto-play Screen is an activity-owned, one-shot manual Home Stop action gated on IDLE and a READY Screen file from that session.


## Version 25
RollingMuxer retains its open container during manual pause and subtracts cumulative pause intervals from audio/video timestamps. Playback metering delegates buffer handling to Media3 TeeAudioProcessor; runtime-check failure retries once using the standard renderer with diagnostics. AnalogPowerView is reusable in ui-components.


## AppRecord 26
CameraCapabilities.configureOrientation requests SCALER_ROTATE_AND_CROP_NONE only on supported API31+ devices, keeping sensor rotation inside EGL. Both native camera and Mixed engine call it. Viewer scrubbing pauses the component and preserves pending seek position until its asynchronous clock catches up. Shared navigation/file-action rows use the same callbacks for every media kind. Clipboard uses multi-item URI ClipData; shares attach read grants. Expanded display units are preferences; underlying temperatures remain Celsius.

# AppRecord 27 — 2026-10-09

Camera producer quarter-turn compensation in Native and Mixed EGL paths (screen source excluded); physical-phone verification remains required. Idle Pause is visibly yellow but enabled only during capture. Armed Schedule uses cyan hands, orange frame and a longer 12-second pulse. Audio viewer adds reusable glossy control rails.

High-resolution Trim releases the main player before opening, skips thumbnails above 1080p (720p on low-RAM devices), and creates preview only on request after thumbnail loading. Bitmap failures are logged; Save returns to Files. Single-file cuts preserve their source video codec and request Media3 trim optimization, which may fall back to encoding. Export errors enter diagnostic history. Phone exit cause is not established without its crash report.

Battery/temperature frames are equally narrow and green; charging source glyphs match value-frame height. Power meters have equal visible gaps, longer needles and underlight; formula strip is flush with the panel border. Wall adapter versus power bank remains a user-selected category, not reliable automatic Android detection.


# AppRecord 28 — 2026-10-09

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
