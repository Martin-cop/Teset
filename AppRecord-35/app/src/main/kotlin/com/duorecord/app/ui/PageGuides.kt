package com.duorecord.app.ui

internal object PageGuides {
    val recordings="""
        FILES & COLORS
        Each card groups overlapping Front and Rear captures. R opens the Rear file; F opens the Front file; V with a microphone plays audio-only files; S with a screen icon opens screen recordings; M opens a composite recording. Green button and frame mean a playable file exists. Gray means no completed file for that camera. The first line shows local date/time; the second shows total file size and longest duration, or PHOTO.

        KEEP / SELECTED
        The vertical switch beside R, F or V keeps that file. On moves it to Selected and protects it from automatic rolling cleanup; Off returns it to ordinary recording storage. This moves the original file without duplicating it. Video: Movies/Record/Selected. Photo: Pictures/Record/Selected. Audio: Music/Record/Selected. Saved files have their own storage limit.

        SELECT & DELETE
        The header Folder icon opens recording storage. The header Select icon starts an empty selection; tap files to select them. The header statistics icon reports all files, including the current known size of files being written. The statistics icon beside Delete reports only your selection. Selection statistics shows the selected file count and size in total, then Photos, Audio and Videos. Photo/video groups split Rear and Front; Other / combined covers legacy files. Counts are files, not paired rows. The report is a snapshot when opened.

        Hold a card, its text or a camera button to enter selection mode. Tap cards or checkboxes to select/deselect. Select all toggles all completed files; tap it again to clear the selection. Delete asks for confirmation and removes the selected files. Share sends selected files through Android. Copy puts their file URIs on the clipboard; Paste requires a compatible file manager. Cancel leaves selection mode. A paired card can select two files; the count is files, not cards. Files being written cannot be selected.

        BROWSE & REFRESH
        Drag the short handle in the right gutter or swipe the list. Refresh checks actual files and removes missing records. The first view shows the latest 100 files. Automatic checks inspect recent files only; set their interval and initial file count in Settings (defaults: 2 seconds and 100 files). Refresh loads and checks the entire archive in the background. Large full scans can take time without blocking Back. Reopening returns to the latest 100. Statistics covers currently indexed files; Refresh discovers older files when needed. Close returns Home and keeps active recording running.

        PLAYBACK & EDITING
        The first row browses previous/next files by time and by type/camera. The second row holds Delete, Folder, Share and Copy. Photo-only playback controls stay disabled. Previous is earlier in time (left); Next is later (right). The video ruler supports 0–30×; 0 pauses. Above 8× video plays with audio muted to avoid Android audio-speed limits. Audio-only playback stays within 0–8×. The chosen speed starts at 1× on app launch and is retained while browsing. A video rate above 8× is temporarily capped at 8× for audio, then restored for the next video. Photos do not reset it. Trim lets you drag the beginning/end; Save creates a new copy in Movies/Record/Selected and preserves the source. Photos open as images with same-camera Previous/Next. Every viewer has a trash icon; keeping files is available only through the Recordings switches.
    """.trimIndent()
    val schedule="""
        START, DURATION & END
        Tap the Start caption for now plus 3 minutes, or End for now plus 10 minutes. The date picker shows the selected month number and name. Enter local date/time as YYYY-MM-DD HH:MM. Letters, invalid dates and out-of-range hours/minutes are rejected. Start must be in the future; the initial value is the next whole hour. The minute ruler sets End relative to Start. Changing Start preserves the chosen duration. Editing End also updates the ruler (1–600 minutes). The endpoint is 10 hours; durations above an hour show hours and minutes. Date and Time open Android pickers; typing digits inserts separators automatically, even after clearing the field.

        CAMERAS & QUALITY
        Select Front, Rear or both with the two persistent buttons. At least one is required. Each graduated quality ruler lists that camera's actual supported resolutions. Rear flashlight and front screen light are selected separately. These choices are saved with the schedule. Choose Video, Photos or Audio directly here. For Photos enter the interval in seconds; each capture is a JPEG. Audio creates an AAC file. The mode is saved with this schedule. Audio schedules use only the microphone; camera quality and light controls are hidden. Changing these choices does not change the current Home capture mode until the schedule starts.

        LIGHTS
        The left screen icon selects white screen illumination; the right vertical flashlight selects the rear LED. Selection does not illuminate the phone before capture starts. Video uses continuous light; interval photos illuminate only around exposure. Both lights can be selected with either camera or Both. The screen must remain visible/on; rear LED availability depends on the device.

        SAVE & CANCEL
        Save schedule activates the selected start/end, cameras, resolutions, capture mode and lights. Arm was the old label, meaning enable the schedule. The saved Start, End and Duration appear in a three-line panel here. Cancel removes the pending schedule. Scheduled capture uses its explicit End (up to 10 hours); the manual recording-duration setting still applies to manual starts. An interruption retains that original deadline; manual Stop cancels recovery.

        EXACT TIMING PERMISSION
        Android's alarm scheduler wakes the app at the requested time; this is separate from the Clock app. Allow exact schedule grants precise timing. Without it Android may delay the reminder. Camera/microphone permissions are still required. Keep the app visible for automatic camera start; in the background tap the scheduled notification. Android can restrict background camera startup. Reopen the app after a phone restart to restore pending alarms.
    """.trimIndent()
    fun healthTitle(index:Int)=when(index) {
        0 -> "Battery guide";1 -> "Battery temperature guide";4 -> "CPU temperature guide"
        3 -> "Storage guide";5 -> "Recording quota guide";else -> "Device status guide"
    }
    fun health(index:Int):String=when(index) {
        0 -> "CHARGE\nGreen cells show stored charge. The plug and moving black cable marks show connection, not charger type or measured current.\n\nREADINGS\nmAh capacity and remaining Wh energy are estimates. Tap percent for energy; tap temperature for °C/°F. Tube marks stay Celsius. A 45°C warning re-arms below 42°C; this is an app threshold, not a manufacturer limit.\n\nPOWER\nV × signed battery A = net battery W, not adapter power. Missing data shows —. Tap meters for local 6-hour history sampled every 10 seconds while running. Tap Battery for details and again to return.\n\nLIMIT\nUnplugged capture pauses at your configured battery threshold."
        1 -> "BATTERY TEMPERATURE\nThis is the battery sensor reading in °C, not CPU temperature. The red fluid rises along the horizontal tube as temperature increases; the visual scale is 0–60°C. An unavailable reading appears as —."
        4 -> "CPU TEMPERATURE\nAndroid supplies this sensor reading in °C when the device allows it. The red thermometer uses a 0–100°C visual scale. This badge appears only with an actual reading; thermal severity is a separate system safety signal."
        3 -> "PHONE STORAGE\nThe horizontal barrel fills from left to right with used phone storage. Above it are used percent and used/total size. Free space and the minimum reserve appear below. The next framed cylinder shows ordinary recordings used against their separate quota. Kept files use the saved-files limit.\n\nFOLDER\nOpen recording folder browses the public recording locations."
        5 -> "RECORDING QUOTA\nThe barrel shows ordinary recordings used against their rolling limit, 8 GB by default. Kept, trimmed and exported files have a separate saved-files limit.\n\nCLEANUP\nOld finalized, unprotected recordings may be deleted to stay within the rolling limit. This is separate from total phone storage."
        else -> "SYSTEM SAFETY\nAndroid reports thermal severity separately from temperature. Capture pauses at Severe or higher. Removing the thermal badge does not disable this protection."
    }

    val captureTiming="""
        START & DURATION
        Duration is in minutes, 1–1440; leave it empty for manual stop. Start time selects a future date and time; Now clears it. Close or Android Back saves a valid duration and returns Home. Press the Record circle to start immediately or save the future schedule. The current mode, sources, lights and sound choices are used.

        PAUSE & DEADLINE
        Pause keeps the current file open. Resume or Record continues the same file; paused time is removed from its timeline. The original finish time stays unchanged. Cameras/audio processing and Android sharing remain active to support resuming; use Stop to release them. Safety and storage limits still apply when duration is empty.

        ANDROID SCHEDULING
        Background starts may need you to open the scheduled notification. Screen/Mixed need Android screen-sharing consent at the start of each new session. Hold the lower Schedule icon on Home for the separate page with explicit Start/End and camera quality.
    """.trimIndent()
    val captureMode="""
        MODES & COLORS
        Five icons select Video, Audio, Photo, Screen or Mixed. White is available and unselected; yellow is an initial choice; green means working; gray means unavailable. Record has a red circle and Play has a green triangle; their frames follow these states. Stop before switching modes. The capture row contains Record/Pause/Resume, Stop and Play. Schedule, Folder and Mark are below the capture card; Settings, device readings and tools are on the last row. The selected mode pulses while recording. Play opens the latest completed file of this mode; Folder opens its actual ordinary or Selected location.

        CAMERA CHOICE
        Tap a Front or Rear camera button to select that source without recording. Its button and monitor frame turn yellow. Press Record to start the selected cameras; both frames turn green. While recording, a camera button changes that camera through the capture controls.

        DURATION & START
        The lower Schedule icon opens start time and duration; hold it for the separate Start/End schedule page. Close saves them. Empty duration means no time deadline. Now clears a future start. Press Record to record now or save the future schedule. The Record toggle pauses and keeps the current file open; tap it again to continue the same file without recording paused time or changing the original deadline. Camera/microphone processing remains active while paused; Stop releases it. Safety/storage limits still apply. Background schedules may need you to open the notification; Screen/Mixed always require Android consent when they start.

        AUDIO
        The microphone records the environment; the speaker records internal playback. Either, both or neither can be chosen and changed while Screen/Mixed capture is running. Android permits capture only of playback whose app allows it; protected playback and calls can be silent. Audio mode shows elapsed session time above a real 20-band frequency spectrum, RMS and peak dBFS, 1.2-second peak hold and red CLIP when PCM reaches full scale. Zero dBFS is the digital limit; these are not calibrated environmental sound-pressure dB. The meter is mono, not separate physical microphones.

        IMPORTANT MOMENTS
        Mark saves the current time while recording. Hold Mark to browse saved moments; while stopped, a tap opens the list. Tap an entry after its file is finalized to seek to it (Mixed opens the composed file). A photo mark opens the latest photo at/before that moment in the session. Marks remain locally until app data is cleared, with up to 1000 stored and the latest 100 shown. They do not copy or protect recordings; deleted files cannot be played.

        SCREEN CONTROLS
        Screen minimizes Home after capture starts. Notification controls are the default: Pause/Resume, Stop and an elapsed counter. Enable the Recording notification channel; Android 13+ may ask permission once. Pausing freezes the counter and keeps the file open. Stop finalizes it and opens playback. No floating window is created in this mode. Opening the notification shade can appear in a full-screen capture. Optional Screen floating controls in Settings enables protected overlays, which Android may mask black. Consent and privacy indicators remain. Status uses one fixed line; full stop reasons remain in history.

        MIXED
        Select Front, Rear and/or Screen. Choose any selected source as Background; the others become inset frames. Drag a frame to move, its lower-right grip to resize, or its × to hide. Hiding a frame leaves its separate raw recording running. Screen + Front and Screen + Both are quick presets. Front BG / Rear BG / Screen BG directly choose and enable a background. After a successful screen-backed start, Home minimizes so you can use another app. In the default notification mode there are no floating camera or control windows; the engine still encodes the configured composite. Edit the layout in Home before starting. With optional floating controls, camera windows can be dragged/resized/closed and the toolbar has counter, Pause/Resume and Stop. Only the bubble and frame rectangles receive touches; other areas remain usable. Returning to AppRecord hides these overlays. A Screen inset uses a transparent positioning outline outside the app, avoiding recursive screen previews. Camera preview windows can appear in the raw Screen file. Toolbar, frame labels and resize controls use separate capture-protected windows. Some devices may replace protected areas with black; clean exclusion must be checked on the device. Closing a frame removes it from the composite too; its raw camera file continues. Mixed camera sizes are capped at supported 720p-or-smaller outputs to limit multi-encoder load.
        Files ending _L, _R and _S hold selected source streams; _M is the separately composed result. Screen and Mixed reserve Android screen consent for the session so screen/internal audio can be added later. Keep the system sharing indicator/notification visible. One grant cannot be reused for a new session. Simultaneous cameras and multiple encoders depend on device support. Screen preview inside the captured app can produce a recursive image; switch to the app you are demonstrating after Start.

        VIDEO / PHOTO / FPS
        Video uses selected cameras with microphone audio. Photo saves JPEGs at the chosen interval. Audio saves AAC; Screen/Mixed save MP4. FPS is video frames per second. More FPS needs more processing and does not change photo interval.
    """.trimIndent()
    val screenLight="""
        PURPOSE
        The display shines white at maximum brightness during camera capture. It can be selected with Front, Rear or Both; the rear LED can be used at the same time. The visible monitor is Front when active, otherwise Rear. The screen lights the side facing the display; the rear LED lights the opposite side.

        OFF & EXPOSURE
        Only a fixed top-right Close button remains over the light. The monitor uses one quarter of screen width. Interval-photo exposure changes brightness without replacing the overlay, so Close stays usable at one-second intervals. Leaving the app restores brightness. A brief monitor blink confirms each saved JPEG.
    """.trimIndent()
    val playback="""
        PURPOSE & CONTROLS
        Play a completed video. Use the first icon row for navigation, Play, Pause and Stop. Drag the position bar to pause and preview that time; Play resumes there; after the file ends, Play restarts it from the beginning. The second row holds Delete, Trim, Folder, Share, Copy and 1× normal speed. The outer arrows browse all photo/audio/video files by time; the inner arrows stay with this type and camera. Each row fits the screen. Hold the monitor for viewing rotation; originals are unchanged. Runtime-check errors retry once with standard playback; details appear in interruption history. Move the single speed ruler below the player from 0× to 30×. Zero pauses both views; moving above zero resumes. Rates above 8× disable video audio; returning to 8× or below restores the audio track. High speeds may skip display frames. Play after 0× resumes the last positive speed.

        TRIM & DELETE
        Use the save switch in Recordings to protect a file. The trash icon deletes the displayed file after confirmation. Trim opens a handle-based editor and saves a separate MP4, preserving the original. Close returns to the previous page and releases playback. Playing/exporting files are protected from deletion while in use.
    """.trimIndent()
    val audioPlayback="""
        PLAY & SEEK
        Play AAC or older M4A audio. The first row contains previous/next arrows, Play, Pause and Stop; Trim is disabled for audio. Outer arrows browse all file types; inner arrows stay with audio. Drag the position bar to pause and select a time; Play resumes there; after the file ends, Play restarts it from the beginning. The spectrum shows actual decoded sound, not simulated levels.

        FILE ACTIONS & SPEED
        The second row contains Delete, Trim, Folder, Share, Copy and 1× normal speed. Copy uses file URIs; Paste depends on your file manager. The speed ruler supports 0–8×; zero pauses. Stop pauses at the beginning. Close releases playback. Keep files using the Recordings save switch.
    """.trimIndent()
    val photo="""
        PURPOSE & CONTROLS
        View a completed interval JPEG. The first row browses previous/next files by time and by type/camera. The second row holds Delete, Folder, Share and Copy. Photo-only playback controls stay disabled. Previous is earlier in time (left); Next is later (right). The trash icon deletes this photo after confirmation. The save switch is available only in Recordings and moves the file into Pictures/Record/Selected. Close returns to Recordings.
    """.trimIndent()
    val details="""
        PURPOSE
        Review the active storage path, file duration, session limit, frame rate and ordinary/protected storage totals. Video uses Movies/Record, photos Pictures/Record and audio Music/Record. Selected subfolders hold kept files and trimmed/exported video copies.

        LIMITS
        Old finalized, unprotected files may be deleted at the rolling limit. Protected files use a separate limit; active playback, exports and files being written are excluded from automatic deletion. Close returns to Info & tools without stopping capture.
    """.trimIndent()
    val settings="""
        ARCHIVE
        Refresh seconds sets automatic checks while Recordings is visible (1–3600). Initial files limits the first list and automatic media inspection (1–10000). Refresh loads all. Screen floating controls is off by default; notification Pause/Resume/Stop controls work without overlays; on uses protected controls that Android may mask black. Default 2 seconds / 100 files; 5 seconds / 30–100 files is usually lighter for large archives.

        PURPOSE & INPUTS
        Set file duration (seconds/minutes/hours; default 30 minutes), rolling/saved space, free-space reserve, FPS, battery threshold and session duration (default 120 minutes). FPS means video frames per second; higher rates may increase heat, battery use and file size. It does not control the audio sample rate.

        ACTIONS
        Save validates and stores inputs. Reset restores defaults while capture is stopped. Minimal appearance changes decoration while preserving capture status. Shortcut configures named keys, repeated presses and simultaneous holds; Background recording opens Android battery restrictions. Info & tools beside Settings holds recording details, interruption history and About. Guide text is accessed here through the header icon.

        RECOVERY & CLOSE
        Recording settings are locked during capture/recovery; appearance and archive refresh/count remain editable. Close discards unsaved edits. Android foreground-service/privacy indicators are required. With Continue video when mic is silenced enabled (default), Android-silenced microphone intervals encode silent audio while camera video continues. Sound resumes when Android releases the mic, without camera restart. Disabling the option pauses video instead; audio-only capture always pauses when silenced. A disconnected camera, stopped audio stream or revoked permission can still pause capture; recovery preserves the original deadline and cannot reconstruct missing footage.
    """.trimIndent()
    val about="""
        PURPOSE
        Show the installed app version, developer information and build details. Close returns to the app.
    """.trimIndent()
    val tools="PURPOSE\nInformation that does not change recording settings. Recording details shows paths and limits; Interruption history lists stop/retry reports; Last stop shows the latest reason; About lists the version, language and build tools. Close returns Home without stopping capture."
    fun dialog(title:String):String? = when {
        title.contains("guide",true) -> null
        title in setOf("Start date","End date") -> "DATE\nChoose a date using Android's calendar or date controls. Set date applies it; Close cancels. Start must be in the future."
        title in setOf("Start time","End time") -> "TIME\nChoose hours and minutes with Android's time picker; use its keyboard mode when available. Set time applies it. Times use the phone's local zone and 24-hour format."
        title=="Delete this file?" -> "DELETE\nDelete permanently removes the displayed file and its Recordings entry. Close cancels. Keeping a file protects it from automatic cleanup, not an explicit deletion."
        title=="All files statistics" -> "COUNTS & SIZES\nSnapshot of all indexed physical files, including the currently known size of unfinished files. Totals are grouped by media type and camera or screen. Refresh Recordings to reconcile external deletions. This report changes no files."
        title=="Selection statistics" -> "COUNTS & SIZES\nSnapshot of selected physical files: total, Photos, Audio and Videos. Photos/videos include Rear and Front totals; legacy combined files are listed separately. A paired row can contain two files. Sizes include kept files when selected. Close leaves the selection unchanged; this page never deletes files."
        title=="About" -> about
        title=="Notification controls" -> "SCREEN CONTROL ACCESS\nThe Recording notification holds Pause/Resume, Stop and elapsed time. Enable notifications and the Recording channel in Android settings. Until then capture does not start. Floating controls remain an optional alternative in AppRecord settings."
        title.endsWith(" history") -> "POWER HISTORY\nActual battery measurements sampled every 10 seconds while AppRecord runs. Up to 6 hours are retained locally. Missing readings break the line; this is net battery power, not adapter wattage. Close returns to the meter. You do not get measurements for time when the app was stopped."
        title=="Photo interval" -> "PHOTO INTERVAL\nEnter 1–86400 seconds between JPEG photos and a capture duration in minutes; empty duration is unlimited. Apply stores a valid value; Close leaves it unchanged. Both the monitor and Photo control flash briefly when a photo is saved."
        title=="Capture mode" -> captureMode
        title=="Important moments" -> "MARK & PLAY\nDuring capture, tap Mark to remember the moment; hold it to browse marks. While stopped, a tap opens the list. Tap a saved moment to play its completed file at that time. Photos open the latest preceding photo in the same session. Paused time is not recorded.\n\nSTORAGE\nMarks are local metadata, not file copies or protection. Missing/unfinished files cannot be played. Latest 100 shown; up to 1000 retained. Clearing app data removes them."
        title=="Mixed background" -> "BACKGROUND\nChoose which selected source fills the output canvas. The remaining sources appear as movable inset frames. Raw source files are unaffected."
        title=="Capture start time" -> "START TIME\nChoose a future date/time, then press Start on Home to save the schedule. Now selects immediate capture. Duration is counted from the scheduled start; empty duration means manual stop. Android may require opening the notification and confirming screen capture."
        title=="Viewing rotation" -> "VIEWING ONLY\nHold the monitor to choose 0°, 90°, 180° or 270° for a sideways recording. This rotates viewing without changing the original file. Close leaves the current angle. The next file starts at 0°."
        title=="Playback settings" -> "SPEED\nDrag the ruler to change playback rate. Zero pauses. Video supports up to 30× (audio muted above 8×); audio files support up to 8×. The selected speed continues across previous/next navigation. Close returns to playback."
        title=="Settings" -> settings
        title=="Trim video" -> "PURPOSE & HANDLES\nDrag either blue timeline handle to choose the start/end. Preview selection creates playback only when requested. Large videos use a plain timeline to reduce decoder and memory use.\n\nSAVE & CLOSE\nSave creates a separate MP4 in Movies/Record/Selected, preserving the original. Close cancels without exporting and restores paused playback. Save returns to Files. The copy uses the saved-files limit."
        title=="Export by time" -> "PURPOSE & TIME RANGE\nExtract a local start/end range from completed videos. Select Front, Rear or Both and enter YYYY-MM-DD HH:MM:SS. Missing footage prevents export.\n\nEXPORT & CLOSE\nExport concatenates the available video slices in chronological order into a new MP4 in Movies/Record/Selected; Both makes two independent camera files. Originals remain. This does not begin recording. Close cancels. Photos and audio-only files are excluded. This does not mix different media types or make picture-in-picture output."
        title.startsWith("Interruption history") -> "PURPOSE & REPORT\nView the latest 200 automatic interruptions, errors and retry outcomes. Entries include device, battery, heat, storage and capture configuration when available. Copy report copies the text for troubleshooting.\n\nRECOVERY\nAndroid microphone silencing is recorded separately: video may continue with silent audio and then restore sound without camera restart. Actual stream failures, camera contention, heat, battery or storage limits can still pause capture. Recovery attempt started means an attempt, not success; Capture resumed successfully requires all selected cameras and an active unsilenced microphone (photos need no microphone). Reports include current audio mode and system microphone mute, but cannot identify another app from this status. Recovery uses the original deadline; force-stop, denied permission or background startup restrictions may require reopening. Close leaves capture running."
        title=="Saved space is full" -> "PURPOSE & CHOICES\nThe protected-files quota cannot fit the new file. Increase limit preserves existing kept files. Delete oldest saved makes room by removing older protected files after this explicit choice. Files in playback/export remain protected. Close cancels the pending save/export."
        title=="Delete selected files?" -> "PURPOSE & CONFIRMATION\nDelete permanently removes selected physical files and their records, including kept files. A paired card may select both cameras. Close cancels; files being written or used in playback/export cannot be deleted."
        title=="Recording folder" -> "PURPOSE & PATHS\nOpen the relevant public folder through Android's file manager. Video: Movies/Record. Photos: Pictures/Record. Audio: Music/Record. Each Selected subfolder holds kept files. Android creates folders with the first file. If no compatible browser is installed, this page shows the paths. Close returns without changing files."
        title=="Background recording" -> "PURPOSE & ANDROID LIMITS\nCapture runs in a visible foreground service with a CPU wake lock; minimizing does not request Stop. Unrestricted opens Android battery settings for this app. Another app, permission loss or system resource policy can still interrupt camera/microphone access. Recovery retries when resources return without extending the original end time. Close returns."
        title=="Recording shortcut" -> """
            PURPOSE & LAST SETTINGS
            Start/stop capture using selected physical keys while this app receives key events. It uses the last saved capture type, cameras, quality, FPS, lights and duration. Enable shortcut turns it on; Save validates and stores it; Close discards pending edits.

            BUILD A COMBINATION
            Select Volume up, Volume down, Volume mute, Camera shutter, Camera focus, Headset button, Media play/pause, Media play, Media pause or Media record. Only keys physically present and delivered to this app can work. Choose Hold together for one or several keys and enter 500–5000 ms. Every selected key must be held simultaneously for that duration. Releasing any key early cancels; after triggering, release all selected keys before another trigger. Example: Volume up + Volume down, Hold together, 800 ms.

            REPEATED PRESSES
            Select exactly one key, choose Repeated presses and enter 2–5 presses within two seconds. Holding a key's automatic repeats does not count. Default remains Volume down three times. Disable shortcut restores normal selected-key behavior. While a shortcut is enabled, its keys are consumed rather than changing volume while this app has focus.

            ANDROID & LOCK SCREEN
            Power/Side key, Home, Recent apps and system-reserved assistant keys are controlled by Android and are not offered as working shortcuts. Phone/accessory support for Camera/Headset/Media keys varies. Android/system accessibility key combinations may take priority. Ordinary apps cannot receive global volume chords while backgrounded or locked; use the DuoRecord Quick Settings tile and unlock when prompted, or assign that visible launch through a supported phone shortcut. Losing focus or leaving the app cancels a pending hold; settings dialogs do not trigger capture.
        """.trimIndent()
        else -> "PURPOSE\n$title shows information or controls for this task. Read the action labels before applying changes. Close returns to the previous screen."
    }
}
