# Permanent project instructions

- Follow `design/UI Neon.md` for the neon palette, concealed underlight, rounded frames and controls. Later explicit user requests take precedence.
- **Equal four-sided spacing is a permanent user requirement.** Keep left, right, top and bottom padding equal inside components, and use matching outer gaps between cards and their container. The first card's top gap must equal its side gaps. The only exception is an edge already occupied by another UI object. Account for transparent shadow reserves separately so visible spacing is also equal. Avoid excessive empty space and controls touching frames.
- The Recordings list has no enclosing decorative panel. Each recording has its own compact card. The R playback square and its vertical save switch share a frame, with the switch on the right; the F pair has its switch on the left. Align the two-line metadata block to the visible square's top and bottom. Preserve native Android switch geometry when rotating it.
- The user gathers requests until they send «تمام». Do not begin coding a new batch before that word; a batch already approved may be completed autonomously.
- Keep user-facing progress and delivery explanations in Persian; the app interface and sequential delivery filenames remain English (`AppRecord-N.apk`, `AppRecord-N-source.zip`). Preserve previous releases.
- Verify changes with appropriate existing build/tests. Distinguish native test renders from phone screenshots; do not claim camera, hardware or background endurance testing without a device.
- **Help placement is permanent:** whenever a page/dialog has explanatory help, place a Guide icon immediately to the left of its top-right Close (×) icon. Open grouped explanations through that icon instead of placing help paragraphs among the controls.

- Every subsidiary page and application-owned dialog must provide its own grouped, purpose-specific Guide immediately left of Close. Guide dialogs themselves do not recursively add another Guide.

- Explicit AppRecord-17 exception: the white screen-light overlay shows only a fixed Close control; do not add Guide or rear-LED controls there. Keep that overlay stable between photo pulses.

- Every titled box must put its title inside the shared dark title strip, with the template divider. Do not leave a box title floating outside its frame. Untitled boxes need no empty title strip.

- Provide a numbered, grouped scope list before each authorized change batch and a numbered completion/status list with delivery. Distinguish completed work from hardware checks or unavailable references.
- Align related visible component edges on shared left/right guides, accounting for decorative shadow reserves. Main mode icons must stay within the capture card edges; audio transport, counter and spectrum share visible edges.

- Ignore exact repeated requests already implemented. The user copies batches from Samsung Notes and may accidentally repeat older text; verify the implementation before changing it again.

- Explicit AppRecord-30 floating transport exception: Screen/Mixed floating transport has only the elapsed counter, Pause/Resume toggle and Stop in a transparent white frame. No title, Guide or Close in this specific toolbar; Stop closes capture and returns to completed playback. Android capture-protected overlays may leave masks; keep notification-only controls available and do not promise unsupported background-preserving exclusion.
- Current icon colors: normal white, unavailable gray, selected/working green; Record is red while recording, Pause/Resume and Stop yellow when available, Play green while playing.

Screen/Mixed recording defaults to notification controls per the user choice for AppRecord 31. Do not require floating overlay permission in notification mode. Preserve later explicit manual control choices.

Explicit AppRecord-32 exception: power-history charts have no header, Guide or top Close icon; use only their Close action. Avoid duplicate close controls when a bottom Close action exists. Main five mode icons are yellow when available and green when selected; preserve white bottom transport rows.

AppRecord-33 overrides earlier colors: available unselected white, unavailable gray, initial selection yellow, working green. Record glyph red and Play glyph green independently of status frames. Border only camera monitor, not its surrounding controls. Ignore already completed duplicates and explicitly report their status. Home order Video, Audio, Photo, Screen, Mixed.

AppRecord-34: no separate Pause button in capture or playback rows. Capture Record toggles recording/pause/resume in the same file; glyph Record red while recording, Pause yellow while paused. Stop remains separate. Battery numeric colors stay phosphor green across energy/unit toggles. Small mAh/Wh units above their numbers. Charger upright, aligned to cell index 8, straight cable with moving black dashes.

AppRecord-35 overrides: five Home modes yellow available/green selected; armed Schedule yellow. Battery details show supported standard Android readings and mark missing values explicitly. History columns; tiny matching units at upper right; no white percent underlight. Charger straight to rightmost cell index 9; matching body/tube gaps, number top inset 24dp.
