# AppRecord-35 — 2026-10-10

Current preview: `1.3.34-AppRecord-35-preview`, versionCode 40, launcher AppRecord 35. Battery detail readings distinguish reported, estimated and unsupported values. Home modes: yellow available, green selected. Armed Schedule: yellow. Battery history: signed columns, null samples remain gaps. Number top insets: 24dp; body/tube gaps matched; charger to rightmost cell. Tiny matching units sit upper-right; no white percent underlight. Unified Record/Pause/Resume and Play/Pause toggles remain; Stop separate.

## Historical baseline and retained behavior

# AppRecord-30 — 2026-10-09

Installable preview: `com.duorecord.app.preview`, version `1.3.30-AppRecord-31-preview`, Build 35, launcher **AppRecord 30**. Uses the preceding Preview development signature; the separately signed original DuoRecord package is not replaced. Earlier numbered deliveries remain immutable.

## Recording

Five Home icons choose Video, Photo, Audio, Screen or Mixed. Green means selected/active, white available and gray unavailable during another mode's capture. Every mode shares a single control row: Record, Pause/Resume, Stop and Play. Record starts the current mode; Play opens the latest completed file of that mode; Folder opens its ordinary or Selected location. Individual camera controls remain in Video/Photo. Schedule below the capture card opens Capture timing, holding the two controls formerly under Start: minutes (empty = no deadline) and future start/Now. Close/Back saves a valid duration. Record starts immediately or saves a future schedule. Safety limits still apply. The status rectangle retains stop reasons.

Manual Pause keeps the muxer and current file open. Resume (including Record while paused) continues that same file with paused time removed from sample timestamps. Stop finalizes it. Explicit rolling segment limits and process/resource loss can still create separate files. Camera/microphone processing stays active while paused; the original scheduled deadline is unchanged.

Mark saves an important moment while recording; hold it (or tap while stopped) to browse the latest 100 of up to 1000 local bookmarks. A completed matching video/audio seeks to that moment; a photo mark opens the latest preceding photo in the session. Bookmarks do not copy or protect files; deleting media makes its mark unplayable. Clearing app data removes bookmarks.

Screen and Mixed have independent microphone (ambient) and speaker (internal playback) controls, changeable during capture. They select either input, both mixed into one AAC track, or silence. RECORD_AUDIO is requested and microphone foreground access reserved at startup to allow later source changes. Internal capture supports Android-permitted MEDIA/GAME/UNKNOWN playback; applications can refuse capture, protected content and calls are not captured. No privacy bypass is attempted.

Audio-only saves AAC and displays a real 20-band logarithmic spectrum (Hann-windowed 1024-point FFT), RMS/peak dBFS, 1.2-second peak hold and PCM clipping warning. This is a mono input display, not separate physical microphones, an audio effect or calibrated sound-pressure dB. Video saves MP4; interval photos save JPEG. Photo intervals and rear/display lights retain their existing controls.

## Mixed recording

Select Front, Rear and/or Screen; any selected source can be the background. Original textures are encoded independently, and a separate composite encoder draws the chosen background plus inset frames. Selected Front, Rear and Screen files use `_L`, `_R`, `_S`; composite files use `_M`. Selecting all three creates four streams/files per rolling segment. One source creates that source plus its composite copy. Files share a graph/session identity and monotonic clock. Changing the background, moving/resizing/hiding a frame affects the composite only. Hiding an inset does not stop its raw file; a source button adds/removes that source.

Screen + Front / Screen + Both quickly prepare a screen-backed presentation. Front BG / Rear BG / Screen BG choose a background directly and enable that source if needed; no hidden selection popup is required. Mixed camera qualities are capped to supported sizes with long edge ≤1280 and short edge ≤720, using exact selected Camera2 IDs and validating the final concurrent configuration together, instead of filtering against another mode's oversized quality.

The encoded composite preview inside AppRecord includes a drag/resize/hide editor. After a successful screen-backed mixed start, Home minimizes so the user can use another app. Outside AppRecord, selected non-background cameras have live decoded preview windows with drag/resize/hide controls. A small 48 dp movable Mixed bubble expands a compact audio/source/background/Pause/Stop toolbar, folding after five seconds. Windows are independent and non-focusable/non-modal; only their rectangles receive touches. Returning to AppRecord removes these external overlays and reconnects the in-app preview. No large floating configuration page remains.

A Screen source inset over a camera background has a transparent movable outline outside the app; its actual content appears in the encoded composite, not in a recursively repeated screen preview. Android screen capture includes visible camera windows and controls in the raw Screen file; ordinary app APIs cannot promise an overlay-free raw display recording. The composite draws its own selected inset textures. Closing an inset hides its composite frame, but its separate raw file continues. Home's own composite preview can create feedback when Home itself is the shared screen; switch to the demonstrated app.

Adding/removing a source rebuilds the camera/encoder graph, finalizing existing files and opening new ones. This can introduce a short gap; it never resets the session deadline. Background and layout changes keep encoders running. Multiple concurrent cameras and up to four hardware video encoders depend on the phone. The program reports unsupported setups/errors rather than claiming guaranteed compatibility.

## Android consent and schedules

Every new Screen/Mixed session obtains a fresh MediaProjection grant. On Android 14+ the request specifies the whole display rather than app-only capture; OEM permission UI can still vary. Mixed reserves this grant even if Screen is initially unselected so Screen/internal playback can be added later. One MediaProjection instance and at most one VirtualDisplay are used per session; rolling/source rebuilds swap the display surface. An old grant is not reused after Stop or service interruption. Permanent installation consent is not available through the ordinary Android API. Floating controls use Android's separately granted display-over-other-apps permission.

Future starts persist source/mode/quality settings. In the foreground the schedule is checked automatically; in the background a notification may require opening the app. Screen/Mixed require the user to confirm Android sharing at the scheduled start. Time limits retain the original scheduled end; a blank duration has no deadline. A notification permission is requested for background reminders. The dedicated Schedule page retains its 10-hour ruler.

## Files, archive and export

Ordinary video/screen/mixed: `Movies/Record`; JPEG: `Pictures/Record`; AAC: `Music/Record`. Kept and trimmed/exported files are in the relevant `Selected` subfolder. Archive save switches move originals into/out of protected storage; trim/export creates separate copies. Unprotected finalized files are subject to rolling quota cleanup. Missing records are reconciled against actual files.

Settings → Refresh seconds and Initial files configure archive automatic checks and initial display/inspection count. Defaults remain 2 seconds / 100 files; 5 seconds / 30–100 is usually lighter for a large archive. Refresh explicitly loads all. Grouping/diff calculation and scans remain off the UI thread, and provider scanning does not block configuration reads. Playback retains previous/next by time and by type/camera, plus separate transport and file-action rows; selected playback speed is retained across files. Video supports 0–30× (0 pauses, audio muted above 8×); audio-only caps at 8×.

Export by time concatenates chronological video slices spanning the chosen range into a new MP4 through Media3 Transformer (H.264/AAC). Gaps prevent export; protected duplicate coverage is not repeated. Front/Rear are separate sources; Both makes separate camera outputs. It is not a photo/audio/video mixer or the Mixed recording mode.

## Device gauges and verification

The battery's ten visible cells fill to exact integer-percent precision without extra subdivisions: 73% = seven full cells plus 30% of the eighth. Empty portions retain their dark red charging pulse; outlines/other battery geometry are preserved. Header tool icons have no shared frame, increased separation and the same visible height as battery/storage cards. Expanded battery/temperature numeric frames remain equal; the thermometer tube end is rounded.

Two source icons above the expanded battery sit on its left. Android generally reports AC/USB/Wireless, not whether USB power comes from a wall charger or power bank. Tap the plug or bank to select its type manually; the chosen icon is green only while Android reports connection, both are gray while disconnected or before a choice. The type is remembered and must be updated when switching power sources. Battery volts × signed net battery amps estimates battery power, not USB input watts or charger rating.

Build/test/lint results and native Android fixture renders are in `verification/`. Native View renders are not phone screenshots. No physical phone/emulator was connected: live MediaProjection/AudioPlaybackCapture, PCM mix quality/synchronization, Camera2 concurrency, encoder throughput, floating windows, source reconfiguration gaps and long-duration/background performance still require device testing. This is an experimental build, not evidence of reliability equal to mature recorders.

## Earlier component changes (AppRecord 24)

Playback uses two fitted rows. The first contains navigation, Play, independent Pause, Stop and Trim; the second contains Delete, Folder, Share, Copy and 1×. Stop pauses and seeks to zero. The decoded audio view shows a true PCM spectrum, not an animation. The speed ruler remains below the monitor with a normal-speed reset in the file-action row. The fitted video surface, excluding black letterboxing, has a thin green border.

Audio Home shows a framed HH:MM:SS session-elapsed counter above the wider spectrum; this includes paused intervals. No timer reference photo was attached, so typography uses 28 sp monospace rather than claiming an exact match. Yellow/red segment colors begin earlier for visual feedback without changing actual PCM/dBFS/CLIP measurements.

See [reusable components](ui-components/README.md) to embed battery, thermometer, storage, audio spectrum or timer in another Android app. `ui-components` depends only on Android and pure Kotlin `domain`, not AppRecord capture/services/repository.

Recordings header Folder opens ordinary recording storage; header Select enters selection with zero files selected. After a manual Home Stop in Screen mode, playback waits for IDLE/finalization and opens the newest completed Screen file from that session. Other modes/stops do not unexpectedly open playback.

Expanded Battery has one borderless horizontal plug that pulses green for any connected charging source. The plug aligns with the inner terminal/body edge. Android does not reliably distinguish a wall charger from a power bank. Battery title opens electrical details; the larger formula strip is a noninteractive label. Battery/thermometer number frames have equal reduced widths. Mixed initially uses Screen as background with Front at (0,0), retaining user-saved placements.


## AppRecord 25 changes (retained)
Same-file manual pause/resume; compact timestamp mapping; stock playback fallback for runtime-check errors with diagnostic stacks; fitting playback icon row, position above controls and speed below; manual viewing rotation by holding the monitor; clear scheduled start; armed animated Schedule; Share latest capture; pause-aware moment offsets and visible mark count; black white-tick resolution rulers; larger noninteractive power formula and three real V/A/W analog meters. Automatic charging indication does not imply automatic wall-charger/power-bank classification. No physical-device playback verification was performed.


## AppRecord 30
Camera2 rotate-and-crop is explicitly NONE where supported so the existing EGL sensor transform owns upright portrait frames exactly once. Applies to camera video/JPEG and Mixed camera streams; old rotated files are not rewritten. This targets portrait-app compatibility rotation; physical Samsung camera confirmation is still required.
Archive header is six equal 40 dp squares with inner glyph margins and a compact Files title. Automatic refresh remains configurable, default 2 seconds/recent 100 files; manual Refresh reconciles the full archive. Select replaces the misleading header trash glyph. Group sharing uses ACTION_SEND_MULTIPLE; Copy stores URI ClipData, with Paste dependent on the receiving file manager.
Playback shares two compact rows for photo/audio/video: browse/Play/Pause/Stop/Trim, then Delete/Folder/Share/Copy/1×. Inapplicable controls are disabled. Dragging the position bar pauses and selects the frame/time; Play continues there. Position updates cannot overwrite a user drag. Audio spectrum height is 220 dp; no viewer control row requires scrolling.
Expanded battery and temperature number frames centered. Tap the thermometer number to switch Celsius/Fahrenheit, retaining actual Celsius scale. More rounded thermometer end. V/A/W gauges and the large green formula share one panel, gauges above the lower formula strip; repeated unit suffixes removed. Electrical details use colon labels. The armed clock has a cool cyan hands, orange frame, stepped motion and a smooth 12-second pulse. Only the horizontal plug remains; it signals a charging connection without source classification.


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
