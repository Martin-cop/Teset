AppRecord-35: Expanded supported battery details, yellow/green Home modes, armed yellow Schedule, signed history columns, aligned rightmost-cell charger, matching upper-right units and body spacing; removed prior white percent underlight.

# AppRecord 30 — 2026-10-09

- Battery: numeric estimated mAh with small unit; percent toggles approximate remaining Wh (blue), temperature toggles Fahrenheit (green). Thicker green reading frames, doubled body/number spacing, wider horizontal charging plug, longer pins, animated green cable/current dashes and sharp phosphor-green connection pulse without glyph blur. Capacity is not W and no charger rating is invented.
- Power: stronger four-sided yellow meter underlight, equal 18dp visible outer/inter-meter gaps, lower unit labels, tap V/A/W for local timestamped history. Samples every 10 seconds while the app or capture service runs, up to six hours, with gaps for unavailable data. Battery temperature warning at 45°C beeps once per heating episode, re-arms at 42°C and opens the thermometer when foreground; background opening is deferred until returning to the app. This threshold is an app warning, not a universal manufacturer limit.
- Timing/audio: monotonic active capture counter freezes for user pause, automatic interruption, startup and Stop; same counter in audio and ordinary Screen Home and floating controls. Visual spectrum gain emphasizes quiet sound, without changing PCM, RMS, peak or actual clipping. Photo interval dialog includes minutes duration (empty unlimited).
- Home: white normal, gray disabled, green working/selected icons; Record red during capture, Pause/Stop yellow when available and gray before capture, Play green when playing. Armed clock uses larger faster moving hands and gradual pulse without a special colored frame. Fixed one-line status; increased equal gaps around monitor/quality groups, 16dp monitor-to-quality gap and clipped rounded quality component. Swipe a monitor/background horizontally to switch idle capture modes; upward swipe opens Files. Sliders/editable controls keep their own gestures.
- Archive/playback: latest 100 files by default and on migration from the old default 30. Select All reconciles ALL completed actual files in the background and includes older files outside the display cap. Deselect All clears selection. Two viewer rows share one box and seven equal square cells for photo/audio/video/screen/composite; Files leads the second row. Existing audio glossy rails remain.
- Screen: floating panel has only elapsed time, Pause/Resume toggle and Stop, in one transparent white frame with aligned controls. Stop finalizes output before playback. Settings also offers notification controls (floating controls off): Pause/Resume and Stop without creating an overlay or its black mask. Protected floating overlays can still be black-masked by Android; clean background-preserving overlay exclusion is NOT claimed. Android consent and mandatory privacy/foreground indicators remain.
- Orientation: camera texture decomposition now handles an existing 180° producer transform instead of treating it as zero, with synthetic crop/mirror/quarter-turn regressions. Actual front-camera upright output still needs a phone test.

Physical device camera/encoder/MediaProjection exclusion/background auto-play, long recording, thermal alert sound and real battery sensor accuracy have not been tested. Public Play Store release needs a production key, current Play policy/target-SDK review, privacy declarations and multi-device validation; this is a development-signed preview.

# AppRecord 29 — 2026-10-09

Battery: one horizontal connection plug, no power-bank selector; green 3dp value frames, more vertical separation and a taller reading card. A muted capacity label shows an explicitly estimated full capacity only when valid charge-counter and percentage data are available. Battery title toggles between gauges and electrical details. Three power meters have equal 18dp visible outer/inter-meter spacing and brighter concealed underlight. Audio spectrum/counter have green outlines; Home status is fixed at two lines / 56dp.

Screen now requests overlay permission, minimizes after recording starts and has a protected movable Record/Resume, Pause, Stop toolbar. Mixed toolbar and inset header/resize controls are separate FLAG_SECURE windows; camera preview surfaces remain separate. Main Activity is protected while a projection session runs. Android/OEMs may mask protected areas black rather than reveal underlying content; clean exclusion is NOT hardware-verified. Android sharing consent/foreground notification remain mandatory. Floating Stop waits for finalized Screen/Mixed output, then opens that specific file rather than whichever camera finished last. Real phone background launch, capture exclusion and live recording remain untested.

# AppRecord 28 — 2026-10-09

Home begins with capture modes. Capture status is inside the card title strip; transport contains Record/Pause/Stop/Play. Schedule, Folder and Mark live below the card; health and Guide/Information/Settings form the last row. Tap Schedule for start/duration; hold for the advanced Start/End schedule. Quality value and ruler share a connected black component below each camera monitor. The active mode pulses during recording and stops pulsing while paused.

Viewer metadata has a black frame. Trim moves to the second actions row, with a larger inter-row gap for video/photo/audio. Play at end seeks to zero before resuming, including companion synchronization in the player backend. Original files and timing behavior are preserved.

# AppRecord 27 — 2026-10-09

Camera producer quarter-turn compensation in Native and Mixed EGL paths (screen source excluded); physical-phone verification remains required. Idle Pause is visibly yellow but enabled only during capture. Armed Schedule uses cyan hands, orange frame and a longer 12-second pulse. Audio viewer adds reusable glossy control rails.

High-resolution Trim releases the main player before opening, skips thumbnails above 1080p (720p on low-RAM devices), and creates preview only on request after thumbnail loading. Bitmap failures are logged; Save returns to Files. Single-file cuts preserve their source video codec and request Media3 trim optimization, which may fall back to encoding. Export errors enter diagnostic history. Phone exit cause is not established without its crash report.

Battery/temperature frames are equally narrow and green; charging source glyphs match value-frame height. Power meters have equal visible gaps, longer needles and underlight; formula strip is flush with the panel border. Wall adapter versus power bank remains a user-selected category, not reliable automatic Android detection.

# AppRecord 26 — 2026-10-09

Explicit Camera2 rotate/crop NONE with EGL sensor rotation for camera and Mixed streams. Equal square Files header icons; Select, group Share and URI Copy. Shared two-row viewers with separate Play/Pause/Stop, file actions and 1× reset; paused scrubbing and shorter audio spectrum. Centered value frames, °C/°F toggle, rounder thermometer, combined power meters and lower formula strip, colon labels. Clearer armed clock animation. Prior refresh defaults retained. Physical camera orientation/Paste need device verification.

# AppRecord 25 — 2026-10-09

Same-file manual pause/resume; stock player runtime-check fallback with stack logging; fitted icon row and swapped rulers; clear scheduled start and animated clock; Share latest capture; visible pause-aware bookmarks; three real power meters, white frames, automatic generic charging icon, black/white resolution ruler and manual viewing rotation. Hardware confirmation remains pending.

# AppRecord-24 — 2026-10-09

- One icon-only playback row: time/type navigation, play/pause, Stop, Trim next to Delete, and playback settings. No ExoPlayer controls cover the video. Narrow phones can horizontally scroll the row; seeking and speed use separate graduated/position bars.
- Audio playback replaces the empty monitor with a genuine decoded PCM spectrum. PCM16 passthrough processor preserves stereo playback bytes while downmixing only meter input. Timer above the Home audio spectrum; lower visual yellow/red thresholds do not change measured dBFS or true CLIP.
- Reusable ui-components Android library: public AudioSpectrumView, ElapsedCounterView, GaugeView and GaugeDrawing; no AppRecord app/service/sensor dependency.
- Main mode icons align with the capture card; audio transport/counter/spectrum share visible side guides. Standard red Record/Delete, green Play/Resume, yellow Pause and gray unavailable controls.
- Recordings header Folder shortcut and Delete to enter empty selection. User Stop of Screen capture opens the freshly finalized Screen file.
- Power-bank/plug icons on the right, borderless, 36 dp tall, selected connected source pulses green. Source identification remains manual. Equal narrower battery/temperature numeric frames; thinner, slightly rounded thermometer tube outline.
- Battery header opens a separate electrical-details page; rounded green formula strip expands/collapses those details inline. Default battery page shows battery and thermometer first.
- Default Mixed is Screen background + Front inset at top-left (0,0); explicit saved layouts are retained. Preview/video green outline follows the actual fitted content area.
- No reference image was attached for the requested timer typography; a framed 28 sp monospace HH:MM:SS counter is used. Session elapsed time includes manual pauses.

# AppRecord-23 — 2026-10-09

- Shared Record / Pause-Resume / Stop / Play / Schedule / latest-file Folder / Mark row for all five modes; Start both removed, timing controls moved to their own guided page.
- Real pause finalizes files and discards paused/late samples; resume opens new files in the session. Persistent manual pause prevents automatic retry; original end time is unchanged. Resources remain active while paused.
- Real 20-band mono spectrum, dBFS RMS/peak, peak hold and clipping indicator; local important-moment bookmarks with completed-file seek.
- Direct background selection enables a missing source; Screen + Front/Both presets. Validate actual bounded camera choices jointly, avoiding oversized cross-mode quality filtering. Mixed camera outputs limited to supported 720p-or-smaller dimensions.
- Replace intrusive floating mini-layout page with independent live camera inset windows and a small movable bubble / folding toolbar. Auto-minimize on successful screen-backed start; hide overlays on return to AppRecord. Screen inset outside the app is a transparent layout outline. Visible windows remain part of raw MediaProjection output.
- Manual charger / power-bank source icons left of expanded battery; selected icon green only while connected, otherwise gray. No unsupported automatic identification claim.
- Exact integer-percent battery-cell fill with no extra ticks; no shared header tools frame, increased separation and matching visible badge/icon heights.
- Verified test/build/lint/signature details in BUILD_VERIFICATION.txt. Camera/MediaProjection/PiP output and endurance still require phone validation.

# AppRecord-22 — 2026-10-08

Version 1.3.21-AppRecord-22-preview / Build 27 / AppRecord 22. Five modes; independent environment/internal playback audio controls; actual audio-only PCM level meter; Home duration and future start; latest capture playback; configurable archive refresh/count. Equal gauge number frames, flat thermometer end, 3-second charge pulse and connected-charger glyph. Durable stop reason and process-exit reconstruction shown on Home.

Screen recording now uses surface MediaCodec, AAC PCM mixing and RollingMuxer. MixedCompositor draws original source textures into selected raw encoders and a separate composite encoder. Any selected source is the background; normalized frame placements are draggable/resizable/hideable. Mixed _M files participate in archives/navigation/statistics. A floating mini-layout controller supports arrangement/source/audio/Stop changes over other apps; it represents layout rather than a second live camera overlay. Source graph changes finalize current files and can introduce a short gap, preserving the deadline. Whole-display consent is requested on API34+, never reused between sessions. A single active projection/display is retained during rolling/reconfiguration. Hardware output/endurance and compatibility are unverified.

Previous release notes follow for history; their earlier implementation descriptions are superseded by the current README.

# AppRecord-21 — 2026-10-08

VersionCode 26; Preview signing identity and earlier deliveries preserved.

## AppRecord-21: responsive archive, media navigation and capture modes

Archive performance: the old synchronized repository reconciliation held the same monitor used by Home's synchronous configuration reads, and opened every media file every two seconds. Configuration/mount/free-space/database reads no longer take that repository monitor. Provider discovery runs outside it and each mutable file reconciliation takes its own short critical section. Automatic checks inspect the newest 30 files; explicit Refresh performs a full physical reconciliation. Database get uses an indexed ID query. The initial list shows the newest 30 files, then Refresh reveals all; reopening returns to 30. Grouping and diff calculation run off the UI thread. Provider metadata enumeration still covers the folders; large full scans can take time. Cached older file changes may require Refresh. Statistics describes indexed files, so Refresh is needed to discover previously unindexed older files. Tests use 2,444 provider files, a blocked scan with concurrent config/database readers, and a 2,444-row UI fixture. This does not establish latency on a physical phone.

Viewers share five bottom controls: earlier file (all media types), earlier matching type/camera, Delete, later matching type/camera, later file (all types). Photos, audio, camera video and screen video can cross media boundaries; typed browsing preserves camera/source. Overlay previous/next controls were removed. Speed defaults to 1 on activity creation and remains selected between files; photos do not change it. Audio temporarily caps a video preference above 8 at 8, restoring the preference on video. Back returns to the prior application page; stale detached Close controls cannot close a newer page.

Home uses four immediate mode icons outside the capture panel: Video, Photo, Audio, Screen. Selected mode is green; the others stay yellow. Start is at the top of the changing panel. Photo interval lives in its own Photo · seconds control; successful photos flash it and the photo-mode icon as well as the monitor. Mode and screen-audio changes are locked while starting/recording. Screen's microphone toggle chooses ambient sound (not internal app audio). Enabling it requests RECORD_AUDIO and adds microphone foreground type, and MediaRecorder writes AAC into the screen MP4. Disabling it uses no microphone permission/type/track. Each screen session still requires Android MediaProjection consent; no permanent installation grant or silent automatic regrant is possible. Consent is shared only by rolling files in one still-active session. Actual microphone/screen output remains hardware-unverified.

Battery empty red bars pulse more strongly with a 1-second period instead of 3 while actively charging. Thermometer ticks are thinner/fainter and occupy the upper half of the tube; readable white numbers occupy the clear lower half. Battery terminal projects 10% farther with 10% less contact height. Power formula title is green. The tools group has the same orange border, radius and 44dp visible height as the health badges. About retains factual separate Kotlin and Android SDK values. Folder sits immediately after Recordings. Audio/screen archive glyphs are white, and their save switch moves to the left within the file frame. Statistics boxes gain matching bottom clearance. The export banner is retained for export/trim progress, results and errors and stays hidden when empty.

# AppRecord-20 — 2026-10-08

## AppRecord-20: clean rebuild and visible release identity

Clean-built from the current sources containing AppRecord-19's screen recording, power/thermometer/storage updates, archive statistics/icons and scheduling shortcuts. The launcher label is now **AppRecord 20** so it can be distinguished from a separately installed original DuoRecord. About displays the installed version and numeric Build 25. Installable package remains `com.duorecord.app.preview`, with the same development certificate, so this updates earlier Preview builds rather than creating another Preview installation. The independently signed original `com.duorecord.app` is a separate app and is not replaced.

The archived AppRecord-19 APK was inspected: it contains ScreenCaptureEngine, Screen mode and All files statistics, and its delivered MainActivity source matches the current pre-rebuild source. The user's installed package was not accessible, so the reported discrepancy is not assigned a confirmed cause. AppRecord-20 is built after Gradle clean; final APK metadata, signature, alignment and packaged feature markers are verified before publication. Previous numbered releases are preserved.

To verify installation: open **AppRecord 20**, then Info & tools → About: Version `1.3.19-AppRecord-20-preview`, Build `25`. Capture mode contains **Screen**; Recordings has an always-visible statistics icon. Screen capture remains silent and requires fresh Android consent at each start. Physical phone testing remains pending.

# AppRecord-19 — 2026-10-08

VersionCode 24; 1.3.18-AppRecord-19. Prior deliveries and Preview signing identity preserved.

## AppRecord-19: screen capture, live power and archive controls

Screen mode records silent H.264 MP4 video through Android MediaProjection, up to 1080 × 1920 (or landscape equivalent) and 30 FPS, subject to the device encoder. Each start requires Android's screen-sharing consent. Files use Movies/Record and the _S suffix; they have an S/screen icon in Recordings. The service uses the mediaProjection foreground type and one VirtualDisplay per consent session, swapping recording surfaces at file boundaries. Screen mode does not request camera/microphone permissions, cannot run alongside the app's camera/audio modes, cannot be scheduled unattended, and does not retry an expired consent token. System Stop, session deadline, storage, battery and thermal limits still stop capture. Protected content may be hidden by Android. This new hardware path still requires phone validation, including rotation and rolling-file boundaries; no recorded screen output is claimed from unit tests.

Thermometer white ticks span the tube, with rotated thin numbers every 10 degrees. Battery terminal projects less and has a wider contact. Storage cylinders use finer white minor and thicker major curves. Charging connection, status, source, speed availability, current, voltage and charger rating each have a separate row. The final box's title strip shows signed battery-side power, e.g. 3.80 V × 1.20 A = 4.56 W. Displayed rounded factors produce the shown result. Values refresh every second, including unplugging; missing telemetry stays unknown. Battery net watts are not adapter rating or USB input power.

Archive header statistics reports all indexed files; selected statistics sits beside trash and cancel icons. Selection does not include unfinished files; global statistics can include their current known sizes. Viewer Previous/Next use standard bar-and-triangle icons, staying within the same source. About retains installed version and removes the name-meaning field. The brighter saved-JPEG blink is full white; photo torch requests the highest advertised strength where public Android/device APIs support it, otherwise the standard torch level. Screen brightness remains capped by Android/device settings.

Schedule Start caption sets now + 3 minutes and End sets now + 10 minutes, retaining the existing field appearance. The date picker displays selected month number/name/year. Start date is above the photo interval; no reply was received to the ordering clarification, so this is the documented interpretation. Screen mode is deliberately excluded from automatic schedules because every capture needs fresh consent.

# AppRecord-18 — 2026-10-08

- VersionCode 23; 1.3.17-AppRecord-18. Preserve Preview signing identity and earlier numbered deliveries.
- Rounded smaller battery terminal, active-charging empty-bar pulse, white graduated thermometer with in-tube numerals; spaced Memory badge, curved storage fill and finer ticks, smaller capacity line and titled strips.
- Signed battery current/voltage/net-watt estimate with unsupported speed/rating explicitly unknown; do not infer adapter or USB power from battery telemetry.
- Capture mode auto-saves ordered changes and valid seconds, retains prior valid input on errors, Photo/seconds share a row; smaller dialog controls inside title strip; home mode follows capture-state colors.
- Info & tools separates informational actions from Settings; labeled About details in titled boxes; all titled boxes require title strips.
- Wider camera-pair gap, larger microphone in left slot, selection statistics by file type/camera with physical-file counts and no selection/deletion side effect.
- 61 domain + 82 app tests = 143 passed; physical phone validation pending.

# AppRecord-17 — 2026-10-07

- VersionCode 22; 1.3.16-AppRecord-17. Preserve prior numbered releases and Preview signing identity.
- Stable screen-light overlay/Close through one-second photo pulses, quarter-width monitor, saved-photo blink; white/yellow/green idle/starting/recording camera states.
- Graduated proportional thermometers, flat liquid end/smaller bulb, revised battery terminal, equal reading cards/header badges, shaded inset storage barrels and quota moved into Storage.
- Real ADTS AAC audio files with V/microphone archive identity; retain legacy M4A reading. Guard photo mode against MP4 muxing. Viewer trash with safe playback/pin release and confirmation; keep only archive save switches.
- Native date/time pickers and automatic separators after clear; Video/Photos/Audio schedule modes, 10-hour ruler with hours/minutes, three-line saved summary, Save schedule label and explicit scheduled-end precedence over manual limit.
- 61 domain + 78 app tests pass, 88 lint warnings/0 errors. Physical camera/audio/illumination/long-run validation pending.

# AppRecord-16 — 2026-10-07

- VersionCode 21; 1.3.15-AppRecord-16. Preserve all prior releases and the Preview certificate.
- Connected bulb-and-tube thermometer, shorter/taller solid battery terminal, and shared cards for each reading and its gauge.
- Remove standalone thermal badge while retaining thermal safety and diagnostics. Optional real CPU thermometer remains.
- Horizontal cylindrical storage/quota barrels filled left-to-right by usage, used/total phone capacity, unknown capacity handling, and concise page-specific health Guides.
- Screen illumination supports Front/Rear/Both, shows the active monitor, and keeps a rear LED control available in the white overlay. Light choices remain independent, with photo exposure gating and brightness/parent restoration.
- Native layout/gauge/guide regressions plus all-camera-mode illumination/control routing coverage. Physical LED/phone validation remains pending.

# AppRecord-15 — 2026-10-07

- VersionCode 20; 1.3.14-AppRecord-15. Preserve numbered prior releases and the Preview certificate.
- Continue video with silent PCM/AAC while Android silences the microphone; restore sound without restarting cameras. Persist the default-on option through settings, schedules and recovery. Audio-only capture retains strict interruption handling.
- Separate attempted recovery from confirmed recovery; record current microphone/audio state and deduplicate silencing/restoration events. Preserve the original deadline and reject obsolete callbacks.
- Video ruler 0–30× beneath playback; zero pauses, Play restores the previous positive rate, audio is explicitly disabled above 8×. Audio-only remains 0–8×.
- Named hardware button editor with repeated presses or configurable simultaneous holds, last saved capture settings, one-shot/full-release behavior and focus/background cancellation. No global locked/background key interception or reserved Power/Home capture.
- Larger header bounds/symbols, greater internal spacing and closer visible faces. Physical Samsung microphone contention, high-speed playback and endurance validation remain pending.

# AppRecord-14 — 2026-10-07

- VersionCode 19; 1.3.13-AppRecord-14. AppRecord-14.apk and AppRecord-14-source.zip preserve the existing Preview certificate and all prior releases.
- Audio-only AAC/M4A capture using microphone-only permissions/foreground type, rolling files, Music/Record/Selected protection, A archive playback/save, audio-only Previous/Next and 8× speed, scheduling and persistent original-deadline recovery. Photos/audio excluded from video export/trim.
- Two-second archive refresh, explicit lamp Off/Guide, stronger concealed footer/header underlight, closer header controls, shorter compact battery gauge, corrected clockwise refresh arrow, and equal Start/mode button geometry.
- Capture-mode Cancel merged into Close, invalid interval keeps dialog open, and grouped purpose-specific Guide on every subsidiary page/dialog. Explains camera/microphone arbitration and Android thermal severity/source.
- 47 domain + 64 Robolectric app tests = 111 passed; no failures/errors/skips. Physical AAC media, Samsung camera/mic contention, flashlight/exposure and background endurance still require a device.

# AppRecord-13 — 2026-10-07

- VersionCode 18; 1.3.12-AppRecord-13. AppRecord-13.apk and AppRecord-13-source.zip; preserves the Preview signing certificate and all prior releases.
- Green available-file button/pair borders, gray inactive Home toggles, swapped Export/Folder, dedicated thicker short scrollbar gutter and a connected clockwise refresh glyph.
- Green charge bars, solid narrow terminal, battery-detail thermometer, integer compact values, drive icon and separate ordinary quota bytes/percent including live writing sizes.
- Permanent Guide-left-of-Close rule; grouped Recordings and Schedule help. Minute-precision filtered dates, live duration/end ruler, independent persistent camera choices, matching quality/light controls and saved schedule choices. Android 11 exact-alarm compatibility guard.
- Capture-only screen illumination; vertical rear flashlight on the correct side. Photo illumination windows wait for settled light/fresh frames, share overlapping camera demand, turn off between photos and cancel on reconfiguration/stop. Physical hardware validation remains pending.
- Regression coverage: invalid typing/paste/calendar bounds, duration through midnight and two-way editing, persisted camera/quality/light choices, grouped help placement, Android 11 navigation, active-file quota accounting, charge/terminal pixels, photo screen-light windows and cancellable fresh-frame exposure gating. Existing storage, playback, recovery and UI checks retained.

# AppRecord-12 — 2026-10-07

- VersionCode 17; 1.3.11-AppRecord-12. Delivery: AppRecord-12.apk and AppRecord-12-source.zip.
- Fixed-height selection toolbar; strictly clipped archive viewport; fixed 32 dp standard scrollbar handle; tri-state Select all checkbox with deselection; fitted metadata; clockwise refresh arrowhead.
- Schedule becomes a fourth matching home square shortcut. Battery charge/temperature merged; duplicate battery-temperature badge/rows removed. Optional CPU badge only for a real permitted platform reading.
- Double tap live monitors toggles full-screen view and restores their parent/layout on return/background. Saved playback remains in Recordings.
- Moves settings explanations to Guide; adds Guide icon in Settings header.
- Persists capture configuration and original deadline, retains foreground service through recoverable pauses, uses availability callbacks/eight-second retries, preserves end and source/quality/light state, cancels on manual Stop/expiry. Sticky recreation/visible-app reopening restore state subject to Android restrictions. Stale engine callbacks are ignored.
- Tests cover monitor touch/lifecycle, selection count and actual drawn header stability during scrolling, toggle-all, saved configurations/deadlines, interrupted camera retry, old callbacks, cancel/expiry, recreated service and battery recovery. No physical hardware claim.

# AppRecord-11 — 2026-10-07

- VersionCode 16; 1.3.10-AppRecord-11. Delivery: AppRecord-11.apk and AppRecord-11-source.zip.
- Four orange-framed health badges share yellow horizontal outlines/red ten-step gauges and value placement. Expanded matching gauges and boxed values on detail pages.
- Replaces bulb with flashlight; adds independent max-brightness white screen light, preserving only front monitor and restoring brightness on exit/background.
- Adds configurable service-owned interval JPEG capture (default 10 seconds), selected output dimensions, bounded GL snapshot/compression workers, no microphone/MP4 in photo mode.
- Photos use Pictures/Record and Selected; archive, same-camera photo navigation, pinning, retention, keep and delete/reconcile. Excludes photos from video export; collision naming supports JPEG.
- Adds storage/photo, mode, gauge, screen-lamp lifecycle and photo/video grouping tests; phone hardware verification remains pending.

# AppRecord-10 — 2026-10-07

- VersionCode 15; 1.3.9-AppRecord-10. Delivery: AppRecord-10.apk and AppRecord-10-source.zip.
- Reconciliation checks physical descriptor availability, removes confirmed missing files even when MediaStore retains rows, preserves unknown access errors/current writers/pins, and recovers abandoned WRITING entries.
- Bulb icon has distinct off/on geometry and green light rays. Pre-recording selection persists and is applied at capture startup; actual capture callbacks confirm running state. Reset clears the preference.
- File duration and units share one compact settings row. Pages/dialogs replace Back with top-right square Close. Battery is horizontal with percentage above.
- All Open folder routes resolve an explicit compatible browser, preferring the existing default/system handlers; direct-directory opening precedes normal root browsing. No chooser, directory picker or global-default mutation.

# AppRecord-9 — 2026-10-07

- VersionCode 14; 1.3.8-AppRecord-9. Delivery: AppRecord-9.apk and AppRecord-9-source.zip.
- Home status becomes a straight-cornered rectangular neon strip with equal insets and dynamic text height.
- Recordings, folder and time-export actions become three equally spaced 56 dp square icons in one row, with existing action behavior, neon lighting, accessibility names and long-press tooltips.
- Battery/temperature/thermal/storage details use concise label/value rows in a single panel. Removes the large battery gauge, duplicate panel title and lengthy explanatory text; retains necessary thresholds, unknown readings and short temperature/retention notes.

# AppRecord-8 — 2026-10-07

- VersionCode 13; 1.3.7-AppRecord-8. Delivery: AppRecord-8.apk and AppRecord-8-source.zip.
- Removes the enclosing Recordings panel and reduces per-record height from 112 to 80 dp with equal visible padding/gaps. Two-line metadata aligns with the playback square face. Each R/F square and vertical native save switch share a frame; switch sits right of R and left of F, with the neon lighting preserved.
- Replaces full-width Back and Refresh buttons with adjacent square Close (×) and Refresh icons in the page header. Close returns Home without issuing a recording stop.
- Reconciles every three seconds while the Recordings page is visible, stops on page exit/activity stop, and retains manual refresh. Guards against overlapping scans, avoids unchanged row rebinds, and uses incremental changes to preserve scroll and surviving selections. Provider size/duration changes update READY metadata; active recording/export files remain pending.
- Adds permanent equal-four-sided-spacing instructions to AGENTS.md, except edges occupied by other UI objects.

# AppRecord-7 — 2026-10-07

- VersionCode 12; 1.3.6-AppRecord-7. Delivery: AppRecord-7.apk and AppRecord-7-source.zip.
- Open recording folder uses normal Files ACTION_VIEW root browsing with an initial Record directory hint, never a directory-selection picker. Device file managers can ignore the hint; Movies > Record remains browsable from internal storage.
- Removes the home title, compacts the three header controls to 40x48dp, and gives the four equally wide status badges 48dp height. Battery artwork is vertical and filled according to its reported charge.
- Each status badge opens its own full detail page. Battery details show a charge-filled battery graphic, percent, battery temperature, charging and reported condition; thermal/temperature/storage have their own readings and explanations. Unknown values remain unknown.
- Recording details move from the home screen to Settings > Recording details. Schedule recording becomes a standalone page reached from Home, with two actual-resolution rulers and future whole-hour defaults; fractional seconds are removed.
- Segment supports seconds/minutes/hours, stored as rounded whole seconds, with a 30-minute default/reset. A one-time migration replaces the historical 120-second default; other custom durations are preserved. Unit preference persists and reset returns it to Minutes. FPS is explained in Settings.
- Scheduled end alarms identify their stop as automatic, so interruption history keeps the scheduled cause instead of labeling it a user Stop.

# AppRecord-6 — 2026-10-07

- VersionCode 11; 1.3.5-AppRecord-6. Delivery: AppRecord-6.apk and AppRecord-6-source.zip.
- Adds a neon sun/light button between Front and Rear without reducing the width of the side-by-side monitors. Yellow is off, green is on; idle/unavailable/pending controls are disabled.
- The recording service routes light commands to the capture engine for Front, Rear and Both. An owned rear camera uses its repeating Camera2 request with FLASH_MODE_TORCH and AE_MODE_ON. Front-only capture uses the rear LED through CameraManager.setTorchMode and confirms state from TorchCallback; unsupported configurations report an unavailable state without stopping capture.
- Light intent survives camera-session reconfiguration, external state confirmation has a timeout, optional torch callback registration cannot prevent recording startup, and stopping/finalization switches the light off. Minimizing only removes preview surfaces and does not send a light-off command.
- Filename suffixes remain R (Rear) and L (Front/Left), as previously requested; archive playback buttons remain R/F. The number over each quality ruler is width x height in pixels; the ruler beneath playback shows a speed multiplier.

# AppRecord-5 — 2026-10-07

- VersionCode 10; 1.3.4-AppRecord-5. Delivery: AppRecord-5.apk and AppRecord-5-source.zip.
- Catches foreground recording-monitor failures and finalizes with a persisted reason instead of allowing a coroutine exception to crash the process. Engine ownership is assigned before starting capture so cleanup can reach it.
- Settings > Interruption history keeps the latest 200 automatic stops, unexpected service destruction, crashes and interrupted-session recoveries, including available error/stack and last device checkpoint. A Copy report action allows manual follow-up. Explicit Stop is excluded. Android exit reasons are read on next launch when available; unknown reasons are labeled unknown. Background recording links to app battery settings without claiming to bypass Android/OEM restrictions.
- Header readings use compact icon/value boxes. Each archive row is a neon panel with native protection toggles aligned close below the R/F buttons.
- Protection moves the existing MediaStore file to Movies/Record/Selected; removing protection moves it back. Previously protected main-folder files migrate during reconciliation. No duplicate copy or filename change is requested.
- Long press activates row selection; All/Delete/Cancel allow group deletion with file count, size and camera breakdown. Writing/pinned files cannot be deleted. Refresh reconciles externally deleted files and preserves active pending recordings. Paired-row deletion explicitly includes both camera files.
- Playback speed is a graduated touch ruler below the video, with a single value label and options through 8x; the old speed dialog/button is removed.

# AppRecord-4 — 2026-10-07

- VersionCode 9; 1.3.3-AppRecord-4. Delivery: AppRecord-4.apk and AppRecord-4-source.zip.
- Archive protection toggles use platform Android Switch thumb/track geometry with a discreet neon underlight.
- Playback arrows select previous/next READY files of the same camera in chronological order; ends are disabled. Speed options: 1, 1.25, 1.5, 2, 3, 4, 6 and 8×.
- Open recording folder opens Movies/Record through a system file browser, falling back to a directory picker if the browser cannot handle the folder intent.
- New recordings, trims and time exports use yyyy-MM-dd_HH-mm-ss_R.mp4 (rear) or _L.mp4 (front/left). A collision adds _1, _2, etc. Existing videos are not batch renamed. Protection toggles preserve filenames.
- Camera/time are recoverable from the new names; session pairing and protected state in the main folder remain database metadata. Clearing app data can lose that metadata. Selected subfolder outputs remain separate.

# AppRecord-3 — 2026-10-06

- VersionCode 8; 1.3.2-AppRecord-3; English sequential delivery filenames.
- Compact contiguous Guide/About/Settings group with real battery percent/temperature, charging, Android thermal state and free-volume capacity in the remaining header.
- Five-second foreground-only refresh, unknown readings preserved, diagnostic dialog and risk coloring using reported/configured states.
- Recording details moved into their own titled neon panel.
- One-time restore of neon appearance on upgrade at explicit user request; template shadows/disabled behavior preserved.
- About/Guide clarify original DuoRecord name, Export by time, Trim Save, Settings Save and file Keep.
- Health-reading conversion/unknown/condition tests and native compact-header/details-panel UI test.

# AppRecord-2 — 2026-10-06

- VersionCode 7; 1.3.1-AppRecord-2; AppRecord-2.apk and AppRecord-2-source.zip.
- One archive neon panel; compact date/time and size/duration rows.
- Same-session overlapping front/rear files paired without reusing files or relying on resettable segment indices.
- Separate rounded R/F playback buttons; missing/unfinished/damaged camera playback disabled gray without underlight.
- Individual protected-file switches below R/F with binding-safe, serialized persistence and existing quota handling.
- Removed archive tabs, individual row boxes and extra controls; added draggable native RecyclerView fast scroll.
- Six domain grouping tests and three UI archive tests, including real fast-thumb drag, independent playback/protection and disabled states.

# AppRecord-1 — 2026-10-06

- VersionCode 6; 1.3.0-AppRecord-1; sequential English delivery filenames.
- Compact graduated quality ruler, single yellow resolution label, discrete-step haptics; equal shared-button margins.
- Double-tap camera monitor playback; removed two standalone play buttons.
- Thumbnail start/end trim handles and selected-range preview; source preserved.
- Configurable 120-minute default session limit; persisted stop/crash/interruption diagnostics.
- Reset defaults; New York year/month in About.
- One local-date schedule with per-camera quality, foreground automatic start or background notification prompt, scheduled stop and exact-alarm access.
- Configurable visible-activity volume shortcut and Quick Settings tile with unlock flow.
- Lock-screen global volume interception and automatic camera start from ordinary background alarms are explicitly unsupported. Hardware endurance/crash reproduction remains untested.

# 1.2.0-beta2

- Apply the newly supplied UI Neon.md: precise panel drawing, role-based button underlights, Canvas header icons, framed inputs and switches, tabs and dropdowns.
- Add separate Guide and About views with project-specific help, supplied developer identity and runtime package version.
- Keep settings openable during recording; lock capture parameters and allow appearance changes while preserving latest capture preferences.
- Clip scrolling dialog content above pinned actions; show errors in the active dialog and use explicit export error state.
- Keep TextureView preview children hardware-rendered by caching only the decorative panel background in software.
- Include reference Markdown and native View renders for review. No physical-device validation claimed.

# 1.2.0-beta1

- Front-left/rear-right live monitors, shared top toggle, independent start/stop and last-video playback in one Neon panel.
- Device-derived per-camera resolution sliders and compressed-output preview with actual active-quality overlay.
- Independent pipeline teardown, shared audio and EGL lifetime protection; quality changes finalize a camera file and restart at the new resolution (brief gaps).
- Typed numeric settings, Persian/Arabic digits, validation, and a minimal visible-recording theme.
- Removed optional export-completion notification; preserved required foreground/privacy indicators.
- Debug Preview package installs separately because the original release signing key was not supplied.

# Changes

## 1.1.0-beta3

- Independent rear/front video encoders and MP4 files, with microphone audio in each.
- Rear, Front and Both buttons start capture directly; remembered mode defaults to Both.
- Rear-above-front internal playback with shared controls and approximate time alignment.
- Camera-specific relative and absolute exports; Both creates independent outputs.
- Non-destructive SQLite camera/session metadata migration; legacy combined files remain playable.
- Keep becomes a reversible protected tag instead of another copy.
- Separate saved quota, increase-limit/oldest-saved/cancel overflow choices, and ordinary oldest-first rolling cleanup.
- UI Neon panels, buttons, selected-state lighting and dropdown styling adapted from the supplied template.

## 1.0.1-beta2

English UI and automatic MediaStore Movies/Record storage without a folder picker or storage permissions.

## 1.0.0-beta1

Initial modular camera, storage, internal playback and trimming build.


## AppRecord 31 — 2026-10-09
Notification controls are now the default for Screen and Mixed recording. Startup migrates the previous floating default once; later manual choices survive restart. No floating control or camera windows are created in notification mode; composite layout remains configured in Home. Pause/Resume and Stop are available in the recording notification, with an active chronometer and frozen paused time. Stop opens the finalized recording. Notification permission and the recording channel are checked before capture; Android capture consent remains required. Opening the notification shade can appear in the recording. Physical-device behavior still requires validation.


## AppRecord 32 — 2026-10-09
Battery cable now turns directly down into a battery cell, with a triple-width green cable and visible red moving dashes. Charger pins are 10% shorter; matched number and charger frames are 44dp high inside taller battery/thermometer cards. Percent toggles estimated remaining energy in Wh, not adapter power. Power-history dialogs have no header and one Close action, with a yellow plot frame and red text. Other dialogs with a bottom Close do not duplicate the top Close icon. Five Home mode choices use yellow/green; active camera panel frames are green. Home swipes also cover surrounding non-control space; horizontal photo-viewer swipes navigate photos chronologically. Interactive controls retain their own gestures. Device capture behavior was not physically tested.


## AppRecord 33 — 2026-10-09
Charging cable has one bend aligned to a battery-cell centre and ends at that cell's upper edge. Estimated remaining energy now shows a single number without the Wh suffix; accessibility/Guide still identify watt-hours. Battery and thermometer reading frames start at a matched 104dp and grow to fit with padding. Yellow power-chart border removed. Home order is Video, Audio, Photo, Screen, Mixed. Camera buttons choose sources while idle without starting capture; Record starts the selected sources. Camera border belongs only to its monitor. Available frames are white, initial choices yellow, recording green and unavailable gray. Record glyph stays red, Play glyph stays green. Front-camera input receives a 180-degree orientation correction in the shared encoding/preview path; physical validation remains required. Explicit paused seeking takes precedence over end-of-file replay, both in Home playback and the ExoPlayer component. Tests cover non-recording camera selection and monitor-only frames, numeric one-line energy sizing and selected audio playback position.


## AppRecord 34 — 2026-10-10
Battery/temperature cards are compact (172dp); number frames sit 8dp from the inner top. The charger is upright, aligned to the centre of battery cell index 8 (second from right), with a straight cable and moving black segments. Percent/energy and C/F keep phosphor-green digits and matched green frames. Percent has white concealed underlight. Capacity mAh and remaining energy Wh use matching 40% units above their 26sp numbers. Estimated remaining energy remains an estimate, not charger wattage. Launcher and round launcher use an adaptive camera icon. All five capture modes use one Record/Pause/Resume toggle plus separate Stop and Play. Requested state glyphs: Record red during recording; Pause yellow while paused. Playback merges Play/Pause into one control; file navigation and Stop remain. Notification already has one combined Pause/Resume action; mandatory Android notification and capture consent remain. Tests exercise pause/resume service actions across all five modes, combined playback behavior and compact cable/number/unit geometry. Physical camera/capture/background behavior is not tested on a phone.
