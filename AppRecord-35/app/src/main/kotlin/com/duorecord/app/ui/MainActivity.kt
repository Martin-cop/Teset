package com.duorecord.app.ui

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duorecord.app.*
import com.duorecord.contracts.*
import com.duorecord.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private val app get()=application as DuoApp
    private val scope=MainScope()
    private lateinit var root:LinearLayout
    private var screen="home"
    private var previousScreen="home"
    private var adapter:ClipListAdapter?=null
    private var selectionBar:LinearLayout?=null
    private var selectionCount:TextView?=null
    private var selectAllCheck:SelectAllCheckBox?=null
    private var expandedMonitor:View?=null
    private var expandedParent:android.view.ViewGroup?=null
    private var expandedIndex=0
    private var expandedParams:android.view.ViewGroup.LayoutParams?=null
    private var monitorOverlay:FrameLayout?=null
    private var player:PlayerComponent?=null
    private var photoLoad:Job?=null
    private var audioRecordButton:Button?=null
    private var torchButton:Button?=null
    private var screenLightButton:Button?=null
    private var scheduleSummary:TextView?=null
    private var scheduleSummaryPanel:View?=null
    private var healthPowerRefresh:((DeviceHealth)->Unit)?=null
    private var healthDetailRefresh:(()->Unit)?=null
    private var captureModeButton:SignButton?=null
    private var captureModeEditing=false
    private var selectionStats:SignButton?=null
    private var healthDetailIndex=0
    private var whiteLamp:FrameLayout?=null
    private var lampMonitor:View?=null
    private var lampParent:android.view.ViewGroup?=null
    private var lampIndex=0
    private var lampParams:android.view.ViewGroup.LayoutParams?=null
    private var oldBrightness=-1f
    private var lastTorchMessage=""
    private var playerPin:AutoCloseable?=null
    private var openingPlayer=false
    private var playbackSpeed=1f
    private var allMedia:List<Clip> = emptyList()
    private var archiveAll=false
    private var archiveLimitLabel:TextView?=null
    private val captureChoices=mutableListOf<SignButton>()
    private var screenMicButton:SignButton?=null
    private var screenInternalButton:SignButton?=null
    private var durationEntry:EditText?=null
    private var startDateButton:Button?=null
    private var latestPlayButton:SignButton?=null
    private val transport=linkedMapOf<HeaderIcon,SignButton>()
    private var timingSaving=false
    private var audioLevelView:AudioLevelView?=null
    private var scheduleShortcut:SignButton?=null
    private var captureCounter:com.duorecord.components.ElapsedCounterView?=null
    private var playbackToggle:SignButton?=null
    private var playbackPosition:SeekBar?=null
    private var playbackPause:SignButton?=null
    private var scrubbingPlayback=false
    private var pendingSeekMs:Long?=null
    private var pendingSeekUntil=0L
    private var autoPlayScreenAfter:Long?=null
    private var autoPlayScreenBusy=false
    private var mixedEditor:MixedLayoutEditor?=null
    private var mixedBackgroundButton:Button?=null
    private val mixedSourcesUi=mutableMapOf<CameraSource,SignButton>()
    private val mixedBackgroundUi=mutableMapOf<CameraSource,SignButton>()
    private var mixedTexture:TextureView?=null
    private var mixedSurface:Surface?=null
    private var leaveForMixedCapture=false
    private var mixedPreviewOwner:Any?=null
    private var mixedSave:Job?=null
    private var mixedPending:RecordingConfig?=null
    private var playbackRuler:QualityRuler?=null
    private var playbackRateLabel:TextView?=null
    private var currentClip:Clip?=null
    private var activeTrimDialog:Dialog?=null
    private var trimStart=0L;private var trimEnd=0L
    private var statusLabel:PulseStatusLabel?=null
    private var transientMessage:String?=null
    private var transientUntil=0L
    private var healthLabel:TextView?=null
    private var lastHealthUpdate=0L
    private var rollingBytes:Long?=null
    private var storageLabel:TextView?=null
    private var exportLabel:PulseStatusLabel?=null
    private var exportPanel:GlowPanel?=null
    private var exportProgress:ProgressBar?=null
    private var handledSavedExport:ExportStatus?=null
    private var protectionButton:Button?=null
    private var protectionBusy=false
    private var startPreparing=false
    private var stats=""
    private val modeButtons=mutableMapOf<CameraMode,Button>()
    private var modeSaving:Job?=null
    private val cameraPanels=mutableMapOf<CameraSource,CameraPanel>()
    private var qualityJob:Job?=null
    private var requestedMode=CameraMode.BOTH
    private var qualitySignature=""
    private var companionClip:Clip?=null
    private var refreshing=false
    private var refreshPending=false
    private var archiveScanJob:Job?=null
    private var activityVisible=false
    private var archiveManualRefresh=false
    private var lastRevision=-1L
    private var pendingStart=false
    private val mainHandler=Handler(Looper.getMainLooper())
    private val dateFormat=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss[.SSS]",Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)

    override fun attachBaseContext(base:android.content.Context) { super.attachBaseContext(base.inEnglish()) }

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        showHome()
        if(intent.getBooleanExtra("quickRecord",false))mainHandler.post { toggleRecording(app.repository?.config()?.cameraMode ?: CameraMode.BOTH) }
        scope.launch { app.status.collectLatest { state ->
            renderStatus()
            if(state.revision!=lastRevision) { lastRevision=state.revision;refreshData() }
        } }
        scope.launch { app.audioMeter.collectLatest { if(screen=="home")audioLevelView?.meter=it } }
        scope.launch { app.torch.collectLatest { renderTorch() } }
        scope.launch { app.photoLighting.collectLatest { renderScreenLight() } }
        scope.launch { app.photoCaptured.collectLatest {
            cameraPanels[it]?.flashCaptured()
            if(screen=="home" && app.repository?.config()?.photoMode==true) {
                captureChoices.getOrNull(2)?.let { button -> button.captureState=0;mainHandler.postDelayed({ button.captureState=null },160) }
                captureModeButton?.let { button -> button.captureState=1;mainHandler.postDelayed({ if(captureModeButton===button)button.captureState=0 },160) }
            }
        } }
        scope.launch { app.export.collectLatest { renderExport() } }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if(app.status.value.phase==Phase.IDLE && !app.export.value.busy)app.repository?.reconcileRecent()
                    cacheDir.listFiles()?.filter { it.name.startsWith("export-") && System.currentTimeMillis()-it.lastModified()>24*60*60*1000L }?.forEach { it.delete() }
                }
                refreshData()
                intent.getStringExtra("clip")?.let { id -> val clip=withContext(Dispatchers.IO) { app.repository?.get(id) };if(clip!=null)openPlayer(clip) }
            } catch(t:Throwable) { showError(t) }
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.getBooleanExtra("quickRecord",false))toggleRecording(app.repository?.config()?.cameraMode ?: CameraMode.BOTH)
        if(intent.getBooleanExtra("scheduled",false))checkSchedule()
        intent.getStringExtra("clip")?.let { id -> autoPlayScreenAfter=null;scope.launch { val clip=withContext(Dispatchers.IO) { app.repository?.get(id) };if(clip!=null)openPlayer(clip) } }
    }
    private fun newPage(title:String,subtitle:String?=null,back:Boolean=false):LinearLayout {
        collapseMonitor();disableScreenLight()
        mainHandler.removeCallbacks(archiveTicker);archiveScanJob?.cancel();archiveScanJob=null
        cameraPanels.values.forEach { it.dispose() };cameraPanels.clear();qualityJob?.cancel();qualitySignature=""
        Palette.simple=app.repository?.config()?.simpleTheme==true
        mixedPreviewOwner?.let { app.preview(CameraSource.MIXED,it,null) };mixedPreviewOwner=null;mixedSurface?.release();mixedSurface=null;mixedTexture=null;mixedSourcesUi.clear();mixedBackgroundUi.clear();mixedEditor=null;mixedBackgroundButton=null;durationEntry=null;startDateButton=null;latestPlayButton=null;audioLevelView=null;captureCounter=null;scheduleShortcut=null;playbackToggle=null;playbackPause=null;playbackPosition=null;scrubbingPlayback=false;pendingSeekMs=null;screenInternalButton=null;
        healthPowerRefresh=null;healthDetailRefresh=null;captureModeButton=null;selectionStats=null;archiveLimitLabel=null;screenMicButton=null;captureChoices.clear();transport.clear();
        scheduleSummary=null;scheduleSummaryPanel=null;statusLabel=null;storageLabel=null;healthLabel=null;lastHealthUpdate=0;exportLabel=null;exportPanel=null;exportProgress=null;adapter=null;playbackRuler=null;playbackRateLabel=null;audioRecordButton=null;torchButton=null;screenLightButton=null;selectionBar=null;selectionCount=null;selectAllCheck=null;protectionButton=null;modeButtons.clear()
        val horizontalInset=if(screen=="archive")12 else 10
        root=column().apply { setPadding(dp(horizontalInset),dp(12),dp(horizontalInset),dp(12));setBackgroundColor(Palette.background);layoutDirection=View.LAYOUT_DIRECTION_LTR }
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(horizontalInset)+bars.left,dp(12)+bars.top,dp(horizontalInset)+bars.right,dp(12)+bars.bottom);insets
        }
        setContentView(root)
        if(screen=="home") {
        } else {
            val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false;if(screen in setOf("health","battery_metrics"))setPadding(dp(6),0,dp(6),0) }
            val archive=screen=="archive"
            val size=if(archive)40 else 48
            val heading=button(if(archive)"Files" else title) { if(screen=="health" && healthDetailIndex==0)showBatteryMetrics() else if(screen=="battery_metrics")showDeviceStatus(0) }.apply {
                squareFace=true;textSize=if(archive)14f else 18f
                contentDescription=if(screen=="health" && healthDetailIndex==0)"Battery electrical details" else "Page title · $title"
                if(!(screen=="health" && healthDetailIndex==0) && screen!="battery_metrics")isClickable=false
            }
            header.addView(heading,LinearLayout.LayoutParams(0,dp(size),1f))
            fun headerAction(icon:HeaderIcon,description:String?=null,action:()->Unit) {
                header.addView(iconButton(icon,action).apply { squareFace=true;description?.let { text=it;contentDescription=it;tooltipText=it } },LinearLayout.LayoutParams(dp(size),dp(size)).apply { marginStart=dp(4) })
            }
            if(archive) {
                headerAction(HeaderIcon.FOLDER) { openRecordingFolder() }
                headerAction(HeaderIcon.SELECT,"Select files") { adapter?.beginSelection() }
                headerAction(HeaderIcon.STATISTICS,"All files statistics") { showSelectionStatistics(true) }
                headerAction(HeaderIcon.REFRESH) { refreshArchive(manual=true) }
            }
            if(back || archive)headerAction(HeaderIcon.GUIDE) { pageGuide() }
            if(back || archive)headerAction(HeaderIcon.CLOSE) { if(header.parent===root)back() }
            root.addWide(header,if(screen=="archive")40 else 48)
            if(subtitle!=null) {
                root.addGap(4)
                val metadata=label(subtitle,13f,Palette.muted).apply { tag="media-metadata";setPadding(dp(8),dp(8),dp(8),dp(8));background=rounded(android.graphics.Color.BLACK,dp(6).toFloat()) }
                root.addWide(metadata)
            }
            root.addGap(12)
        }
        return root
    }
    private fun addHomeTools(parent:LinearLayout) {
            val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false }
            healthLabel=HealthBadges(this).apply {
                text="Reading device status…";setTextColor(0xff42bfff.toInt())
                onBadgeClick={ index -> showDeviceStatus(index) }
                isFocusable=true;contentDescription="Battery temperature, battery level, thermal status and free storage. Tap for details."
            }
            header.addView(healthLabel!!,LinearLayout.LayoutParams(0,dp(48),1f))
            val tools=LinearLayout(this).apply {
                gravity=Gravity.CENTER;setPadding(dp(2),0,dp(2),0);clipChildren=false;clipToPadding=false
                contentDescription="Tools"
            }
            listOf(HeaderIcon.GUIDE to { guideDialog() },HeaderIcon.INFO_TOOLS to { showInfoTools() },HeaderIcon.SETTINGS to { settingsDialog() }).forEachIndexed { index,(icon,action) ->
                tools.addView(iconButton(icon,action).apply { compactHeader=true;strongUnderlight=true },LinearLayout.LayoutParams(dp(52),dp(52)).apply { if(index>0)marginStart=dp(2) })
            }
            header.addView(tools,LinearLayout.LayoutParams(dp(164),dp(52)).apply { marginStart=dp(4);marginEnd=dp(4) })
            parent.addWide(header,52)
            updateHealth(true)
    }
    private fun updateHealth(force:Boolean=false) {
        val label=healthLabel ?: return
        val now=SystemClock.elapsedRealtime();if(!force && now-lastHealthUpdate<5000)return
        lastHealthUpdate=now
        if(!force && screen=="home")refreshData()
        val cfg=app.repository?.config() ?: RecordingConfig()
        val health=DeviceHealthReader.read(this,cfg.stopBatteryPercent,cfg.reserveBytes).copy(rollingBytes=rollingBytes,rollingLimitBytes=cfg.quotaBytes)
        checkBatteryHeat(health)
        (label as? HealthBadges)?.health=health
        label.text=health.headerText()
        label.setTextColor(if(health.danger)Palette.red else if((health.thermalStatus ?: 0)>=PowerManager.THERMAL_STATUS_MODERATE)0xffffdf58.toInt() else 0xff42bfff.toInt())
        label.contentDescription="Battery ${health.batteryPercent ?: "unknown"} percent. Battery temperature ${health.temperatureText()}. Thermal state ${health.thermalLabel}. Battery health ${health.batteryHealth}. Tap for details."
    }
    private fun showDeviceStatus(index:Int) = showDeviceStatusAt(index,false)
    private fun showBatteryMetrics() = showDeviceStatusAt(0,true)
    private fun showDeviceStatusAt(index:Int,details:Boolean) {
        val cfg=app.repository?.config() ?: RecordingConfig()
        fun readHealth()=DeviceHealthReader.read(this,cfg.stopBatteryPercent,cfg.reserveBytes).copy(rollingBytes=rollingBytes,rollingLimitBytes=cfg.quotaBytes)
        var health=readHealth()
        releasePlayer();screen=if(details)"battery_metrics" else "health";healthDetailIndex=index
        val names=listOf("Battery","Battery temperature","Thermal status","Storage","CPU temperature","Recording quota")
        val page=newPage(if(details)"Battery details" else names[index],back=true)
        val body=column()
        page.addView(ViewportScrollView(this).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        val panel=GlowPanel(this,if(details)"Electrical readings" else null).apply { setPadding(dp(18),dp(18),dp(18),dp(18)) }
        val formula=if(index==0)label(health.powerFormula(),22f,Palette.mint,true).apply {
            tag="power-formula-strip";gravity=Gravity.CENTER;includeFontPadding=false
            setPadding(dp(4),dp(12),dp(4),dp(12));background=rounded(0xff050607.toInt(),dp(8).toFloat())
            isClickable=false;isFocusable=false
        } else null
        val readings=mutableListOf<HealthReading>()
        fun addGauge(i:Int) {
            val title=when(i) { 3 -> "Phone storage";5 -> "Recording quota";else -> null }
            val box=GlowPanel(this,title).apply { setPadding(dp(12),dp(12),dp(12),dp(12));tag="health-reading-$i" }
            val reading=HealthReading(this,health,i);readings+=reading;box.addWide(reading,if(i in setOf(0,1,4))200 else 160)
            body.addWide(box);body.addGap(12)
        }
        if(!details) { addGauge(index);if(index==0)addGauge(1);if(index==3)addGauge(5) }
        fun metrics(h:DeviceHealth):List<Pair<String,String>> = when(index) {
            0 -> h.batteryDetails()+listOf("Stop · unplugged" to "${cfg.stopBatteryPercent}%")
            1 -> listOf("Battery temperature" to h.temperatureText(),"Thermal status" to h.thermalLabel,"Charging" to h.chargingText())
            2 -> listOf("Thermal status" to h.thermalLabel,"Stop at" to "Severe or higher")
            4 -> listOf("Sensor" to "CPU","Thermal status" to h.thermalLabel)
            5 -> listOf("Used · ordinary files" to (h.rollingBytes?.let(::fileSize) ?: "Unknown"),"Rolling limit" to fileSize(cfg.quotaBytes),"Kept files limit" to fileSize(cfg.savedQuotaBytes))
            else -> listOf("Free storage" to (h.freeBytes?.let(::fileSize) ?: "Unknown"),"Total capacity" to (h.totalBytes?.let(::fileSize) ?: "Unknown"),
                "Keep free" to fileSize(cfg.reserveBytes),"Recording limit" to fileSize(cfg.quotaBytes),"Saved limit" to fileSize(cfg.savedQuotaBytes))
        }
        val metricsBody=column().apply { visibility=if(index==0 && !details)View.GONE else View.VISIBLE }
        val values=mutableListOf<TextView>()
        val initial=metrics(health)
        initial.forEachIndexed { position,(name,value) ->
            val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(36);setPadding(0,dp(6),0,dp(6)) }
            row.addView(label("$name:",15f).apply { includeFontPadding=false;setLineSpacing(0f,1f) },LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(12) })
            val text=label(value,14f,Palette.mint,true).apply { gravity=Gravity.END;includeFontPadding=false;setLineSpacing(0f,1f) };values+=text
            row.addView(text,LinearLayout.LayoutParams(0,-2,1.3f));metricsBody.addWide(row)
            if(position<initial.lastIndex)metricsBody.addView(View(this).apply { setBackgroundColor(0xff343638.toInt()) },LinearLayout.LayoutParams(-1,dp(1)))
        }
        if(index!=0 || details) {
            panel.addWide(metricsBody);formula?.let { panel.addGap(8);panel.addWide(it) };body.addWide(panel)
        } else {
            panel.setPadding(dp(10),dp(10),dp(10),dp(10))
            val gauges=LinearLayout(this).apply { contentDescription="Voltage current and power meters";setPadding(dp(12),dp(12),dp(12),dp(12));clipChildren=false;clipToPadding=false }
            val meterHeight=(resources.displayMetrics.widthPixels/resources.displayMetrics.density/3*.95f).toInt().coerceIn(100,144)
            val analog=listOf("V" to 5.0,"A" to 8.0,"W" to 40.0).map { (unit,range) -> com.duorecord.components.AnalogPowerView(this).apply { this.unit=unit;fullScale=range;showUnitInValue=false;setOnClickListener { showPowerHistory(unit) };isClickable=true } }
            analog.forEachIndexed { i,meter -> gauges.addView(meter,LinearLayout.LayoutParams(0,dp(meterHeight),1f).apply { if(i<2)marginEnd=dp(6) }) }
            panel.addWide(gauges,meterHeight+24)
            panel.addView(View(this).apply { setBackgroundColor(0xff343638.toInt()) },LinearLayout.LayoutParams(-1,dp(1)))
            formula?.let { panel.addView(it,LinearLayout.LayoutParams(-1,-2).apply { marginStart=dp(1);marginEnd=dp(1);bottomMargin=dp(1) }) };body.addWide(panel)
            fun bindPower(h:DeviceHealth) { analog[0].value=h.batteryVoltageV;analog[1].value=h.batteryCurrentMa?.div(1000);analog[2].value=h.batteryPowerWatts }
            bindPower(health);healthPowerRefresh={ h -> bindPower(h) }
        }
        if(index==3) { body.addGap();body.addWide(button("Open recording folder",ButtonRole.OUTSIDE) { openRecordingFolder() },48) }
        healthDetailRefresh={
            health=readHealth();checkBatteryHeat(health);healthPowerRefresh?.invoke(health);readings.forEach { it.bind(health) };formula?.text=health.powerFormula()
            metrics(health).forEachIndexed { i,entry -> values[i].text=entry.second }
        }
    }
    private fun checkBatteryHeat(health:DeviceHealth) {
        app.observeBatteryTemperature(health.batteryTemperatureC)
        if(!app.pendingTemperaturePage || !activityVisible)return
        app.pendingTemperaturePage=false
        mainHandler.post {
            if(!activityVisible || isFinishing) { app.pendingTemperaturePage=true;return@post }
            if(screen!="health" || healthDetailIndex!=1)showDeviceStatus(1)
            toast("Battery temperature ${health.temperatureText()}: allow the phone to cool.")
        }
    }
    private fun showPowerHistory(unit:String) {
        val chart=com.duorecord.components.HistoryChartView(this).apply { this.unit=unit }
        val body=column();val panel=GlowPanel(this);panel.addWide(chart,240);body.addWide(panel)
        val dialog=NeonDialog.Builder(this).setTitle("$unit history").withoutHeader().setView(body).setNegativeButton("Close",null).create()
        dialog.show()
        scope.launch {
            val points=withContext(Dispatchers.IO) { PowerHistory(this@MainActivity).read().map { it.at to when(unit) { "V" -> it.voltage;"A" -> it.amps;else -> it.watts } } }
            if(dialog.isShowing)chart.points=points
        }
    }
    private fun showRecordingDetails() {
        releasePlayer();screen="details"
        val page=newPage("Recording details",back=true)
        val panel=GlowPanel(this,"Recording details")
        storageLabel=label(stats,15f);panel.addContent(storageLabel!!)
        page.addWide(panel);refreshData()
    }
    private fun addExportStatus(parent:LinearLayout) {
        val panel=GlowPanel(this);exportPanel=panel
        exportLabel=PulseStatusLabel(this);panel.addContent(exportLabel!!)
        exportProgress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=100;progressTintList=android.content.res.ColorStateList.valueOf(0xffffdf58.toInt());indeterminateTintList=progressTintList
        }
        panel.addView(exportProgress!!,LinearLayout.LayoutParams(-1,dp(8)).apply { leftMargin=dp(12);rightMargin=dp(12);topMargin=dp(8) })
        parent.addWide(panel);renderExport()
    }
    private fun showHome() {
        releasePlayer();screen="home"
        val page=newPage("DuoRecord")
        val body=column()
        page.addView(ViewportScrollView(this).apply { isFillViewport=true;clipChildren=true;clipToPadding=false;addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        val cfg=app.repository?.config() ?: RecordingConfig()
        val chosen=when { cfg.mixedMode -> 4;cfg.screenMode -> 3;cfg.audioOnly -> 1;cfg.photoMode -> 2;else -> 0 }
        val modes=LinearLayout(this).apply { gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false }
        listOf(HeaderIcon.VIDEO_MODE,HeaderIcon.AUDIO_MODE,HeaderIcon.PHOTO_MODE,HeaderIcon.SCREEN_MODE,HeaderIcon.MIXED_MODE).forEachIndexed { index,icon ->
            val choice=iconButton(icon) { selectCaptureMode(index) }.apply { squareFace=true;isSelected=index==chosen;strongUnderlight=true;persistentSelectionColors=true;unavailableRed=false }
            captureChoices+=choice;modes.addView(choice,LinearLayout.LayoutParams(0,dp(56),1f))
        }
        body.addView(modes,LinearLayout.LayoutParams(-1,dp(56)).apply { leftMargin=dp(6);rightMargin=dp(6) });body.addGap(12)
        val card=GlowPanel(this).apply { tag="capture-card";setPadding(dp(18),dp(18),dp(18),dp(22)) }
        statusLabel=PulseStatusLabel(this).apply {
            text="Ready";setLines(1);ellipsize=android.text.TextUtils.TruncateAt.END;tag="capture-status-strip";gravity=Gravity.CENTER
            setPadding(dp(8),dp(6),dp(8),dp(6));background=rounded(0xff050607.toInt(),dp(6).toFloat())
        }
        card.addWide(statusLabel!!,40)
        card.addView(View(this).apply { setBackgroundColor(0xff343638.toInt()) },LinearLayout.LayoutParams(-1,dp(2)))
        card.addGap(12)
        addTransport(card)
        card.addGap(16)
        if(cfg.photoMode) {
            captureModeButton=button("Photo · ${cfg.photoIntervalSeconds}s") { photoIntervalDialog() }.apply { captureState=0 }
            card.addContent(captureModeButton!!,48);card.addGap(12)
        }
        if(!cfg.audioOnly && !cfg.screenMode && !cfg.mixedMode) {
        val pair=LinearLayout(this).apply { layoutDirection=View.LAYOUT_DIRECTION_LTR;clipChildren=false;clipToPadding=false }
        listOf(CameraSource.FRONT,CameraSource.REAR).forEachIndexed { index,source ->
            val mode=if(source==CameraSource.FRONT)CameraMode.FRONT else CameraMode.REAR
            val name=if(source==CameraSource.FRONT)"Front" else "Rear"
            val previewOwner=Any()
            val panel=CameraPanel(this,name,{ selectCamera(mode) },{ app.preview(source,previewOwner,it) },{ changeQuality(source,it) },{ toggleMonitor(source) })
            (panel.record.layoutParams as LinearLayout.LayoutParams).apply {
                if(source==CameraSource.FRONT)marginEnd=dp(42) else marginStart=dp(42)
            }
            panel.photoIntervalSeconds=cfg.photoIntervalSeconds.takeIf { cfg.photoMode }
            cameraPanels[source]=panel
            pair.addView(panel,LinearLayout.LayoutParams(0,-2,1f).apply { if(index==1)marginStart=dp(8) })
        }
        val pairedFrame=FrameLayout(this).apply { clipChildren=false;clipToPadding=false }
        pairedFrame.addView(pair,FrameLayout.LayoutParams(-1,-2))
        torchButton=iconButton(HeaderIcon.LIGHT) { toggleTorch() }.apply { squareFace=true;toggleAppearance=true }
        val lights=LinearLayout(this).apply { clipChildren=false;clipToPadding=false }
        screenLightButton=iconButton(HeaderIcon.SCREEN_LIGHT) { app.screenLightWanted=!app.screenLightWanted;renderScreenLight() }.apply { squareFace=true;toggleAppearance=true }
        lights.addView(screenLightButton!!,LinearLayout.LayoutParams(dp(40),dp(48)))
        lights.addView(torchButton!!,LinearLayout.LayoutParams(dp(40),dp(48)).apply { marginStart=dp(4) })
        pairedFrame.addView(lights,FrameLayout.LayoutParams(dp(84),dp(48),Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        card.addView(pairedFrame,LinearLayout.LayoutParams(-1,-2).apply { leftMargin=dp(4);rightMargin=dp(4) })
        } else {
            if(cfg.screenMode || cfg.mixedMode) {
                captureCounter=com.duorecord.components.ElapsedCounterView(this);card.addView(captureCounter!!,LinearLayout.LayoutParams(-1,dp(54)).apply { leftMargin=dp(4);rightMargin=dp(4) });card.addGap(12)
                if(cfg.mixedMode)addMixedControls(card,cfg)
                addScreenAudioControls(card,cfg)
            } else {
                captureCounter=com.duorecord.components.ElapsedCounterView(this);card.addView(captureCounter!!,LinearLayout.LayoutParams(-1,dp(54)).apply { leftMargin=dp(4);rightMargin=dp(4) });card.addGap(8)
                audioLevelView=AudioLevelView(this);card.addView(audioLevelView!!,LinearLayout.LayoutParams(-1,dp(220)).apply { leftMargin=dp(2);rightMargin=dp(2) })
                card.addContent(label("Microphone · AAC",15f,Palette.mint,true).apply { gravity=Gravity.CENTER })
            }
        }
        body.addWide(card);body.addGap(10)
        val shortcuts=LinearLayout(this).apply { gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false }
        listOf(HeaderIcon.RECORDINGS to { showArchive() },HeaderIcon.FOLDER to { openRecordingFolder() },HeaderIcon.EXPORT to { absoluteExportDialog() },HeaderIcon.SCHEDULE to { showCaptureTiming() },HeaderIcon.SHARE to { shareLatestCapture() },HeaderIcon.BOOKMARK to { if(app.status.value.phase==Phase.RECORDING)markMoment() else showMarkers() }).forEachIndexed { index,(icon,action) ->
            shortcuts.addView(iconButton(icon,action).apply {
                squareFace=true;blueUnderlight=true;strongUnderlight=true
                if(icon==HeaderIcon.SCHEDULE) { scheduleShortcut=this;transport[icon]=this;text="Capture timing";contentDescription="Capture timing";tooltipText="Capture timing · hold for advanced schedule";setOnLongClickListener { showSchedule();true } }
                if(icon==HeaderIcon.BOOKMARK) { transport[icon]=this;setOnLongClickListener { showMarkers();true } }
                if(icon==HeaderIcon.EXPORT)isEnabled=app.editor!=null
            },LinearLayout.LayoutParams(0,dp(56),1f).apply { if(index>0)marginStart=dp(4) })
        }
        body.addView(shortcuts,LinearLayout.LayoutParams(-1,dp(56)).apply { gravity=Gravity.CENTER_HORIZONTAL });body.addGap(12)
        addExportStatus(body)
        body.addGap(12);addHomeTools(body)
        loadQualities();renderStatus();refreshData()
    }
    private fun addTransport(card:GlowPanel) {
        val row=LinearLayout(this).apply { tag="capture-transport";gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false }
        listOf(HeaderIcon.RECORD,HeaderIcon.STOP,HeaderIcon.PLAY).forEach { icon ->
            val control=iconButton(icon) {
                when(icon) {
                    HeaderIcon.RECORD -> if(app.status.value.phase==Phase.RECORDING)startService(Intent(this,RecordingService::class.java).setAction(RecordingService.PAUSE)) else if(app.status.value.phase==Phase.USER_PAUSED)startService(Intent(this,RecordingService::class.java).setAction(RecordingService.RESUME)) else if(app.status.value.phase==Phase.IDLE)toggleRecording(if(selectedCameras.size==2)CameraMode.BOTH else if(CameraSource.FRONT in selectedCameras)CameraMode.FRONT else if(CameraSource.REAR in selectedCameras)CameraMode.REAR else app.repository?.config()?.cameraMode ?: CameraMode.BOTH)
                    HeaderIcon.STOP -> stopRecording()
                    HeaderIcon.PLAY -> playLatestCapture()
                    HeaderIcon.SCHEDULE -> showCaptureTiming()
                    HeaderIcon.FOLDER -> openLatestFolder()
                    HeaderIcon.BOOKMARK -> if(app.status.value.phase==Phase.RECORDING)markMoment() else showMarkers()
                    else -> Unit
                }
            }.apply { squareFace=true;strongUnderlight=true }
            control.semanticTransport=true
            if(icon==HeaderIcon.RECORD)control.captureState=0
            if(icon==HeaderIcon.PAUSE)control.showUnavailablePause=false
            if(icon==HeaderIcon.SCHEDULE) { control.text="Capture timing";control.contentDescription="Capture start time and duration";control.tooltipText=control.contentDescription }
            if(icon==HeaderIcon.FOLDER) { control.text="Latest file folder";control.contentDescription="Open latest capture folder";control.tooltipText=control.contentDescription }
            if(icon==HeaderIcon.BOOKMARK)control.setOnLongClickListener { showMarkers();true }
            transport[icon]=control
            if(icon==HeaderIcon.PLAY)latestPlayButton=control
            row.addView(control,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        card.addWide(row,48)
    }
    private fun showCaptureTiming() {
        if(app.status.value.phase!=Phase.IDLE)return
        releasePlayer();screen="capture_timing"
        val page=newPage("Capture timing",back=true)
        val panel=GlowPanel(this,"Start & duration")
        addCaptureTiming(panel,app.repository!!.config())
        page.addWide(panel)
    }
    private fun saveCaptureTiming() {
        if(timingSaving)return
        val raw=normalDigits(durationEntry?.text?.toString().orEmpty()).trim()
        val minutes=if(raw.isEmpty())0 else raw.toIntOrNull()
        if(minutes==null || minutes !in 0..1440) { durationEntry?.error="Use 1–1440 minutes, or empty for no limit.";return }
        timingSaving=true
        scope.launch {
            try { withContext(Dispatchers.IO) { val repo=app.repository!!;repo.saveConfig(repo.config().copy(maxRecordingMinutes=minutes)) };showHome() }
            catch(t:Throwable) { showError(t) } finally { timingSaving=false }
        }
    }
    private fun openLatestFolder() {
        scope.launch { try {
            val latest=withContext(Dispatchers.IO) { app.repository!!.list().filter { matchesMode(it) && it.status==ClipStatus.READY }.maxByOrNull { it.startMs } }
            val cfg=app.repository!!.config()
            val base=when { latest?.isAudio==true || latest==null && cfg.audioOnly -> "Music/Record";latest?.isPhoto==true || latest==null && cfg.photoMode -> "Pictures/Record";else -> "Movies/Record" }
            val path=base+if(latest!=null && latest.kind!=ClipKind.ROLLING)"/Selected" else ""
            if(!RecordingFolderBrowser.open(this@MainActivity,path))NeonDialog.Builder(this@MainActivity).setTitle("Recording folder").setMessage("Folder: $path. No installed file manager can open this folder directly.").setNegativeButton("Back",null).show()
        } catch(t:Throwable) { showError(t) } }
    }
    private fun markMoment() {
        val state=app.status.value
        if(state.phase!=Phase.RECORDING)return
        val at=System.currentTimeMillis();val sources=state.cameras.filterValues { it.recording }.keys
        scope.launch { try {
            val file=withContext(Dispatchers.IO) { app.repository!!.list().filter { it.camera in sources && it.startMs>=state.startedMs && it.startMs<=at && !it.isPhoto }.sortedWith(compareByDescending<Clip> { it.camera==CameraSource.MIXED }.thenByDescending { it.startMs }).firstOrNull() }
            val offset=file?.let { (at-it.startMs-CaptureRecovery(this@MainActivity).pausedBetween(it.startMs,at)).coerceAtLeast(0) }
            CaptureMarkers(this@MainActivity).add(state.startedMs,sources,at,file?.id,offset)
            val count=CaptureMarkers(this@MainActivity).list().count { it.sessionStart==state.startedMs }
            transport[HeaderIcon.BOOKMARK]?.badgeCount=count
            statusLabel?.text="Moment marked · $count"
            toast("Moment marked · $count · ${java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(java.util.Date(at))}")
        } catch(t:Throwable) { showError(t) } }
    }

    private fun showMarkers() {
        val marks=CaptureMarkers(this).list()
        val body=column()
        if(marks.isEmpty())body.addWide(label("No marked moments yet.",15f))
        marks.takeLast(100).reversed().forEach { mark ->
            val date=java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss",Locale.US).format(java.util.Date(mark.at))
            body.addWide(button("$date · ${mark.sources.joinToString("/") { it.name.take(1) }}") {
                scope.launch {
                    val found=withContext(Dispatchers.IO) { if(mark.clipId!=null)app.repository!!.get(mark.clipId)?.takeIf { it.status==ClipStatus.READY } else app.repository!!.list().filter { it.status==ClipStatus.READY && it.camera in mark.sources && it.startMs>=mark.sessionStart && it.startMs<=mark.at && (it.isPhoto || it.startMs+it.durationMs>=mark.at) }.sortedWith(compareByDescending<Clip> { it.camera==CameraSource.MIXED }.thenByDescending { it.startMs }).firstOrNull() }
                    if(found==null)toast("The matching file is unfinished, removed, or no photo was captured at this moment.")
                    else { NeonDialog.showing(this@MainActivity)?.dismiss();openPlayerAt(found,false,mark.offsetMs ?: (mark.at-found.startMs).coerceAtLeast(0)) }
                }
            },48)
        }
        NeonDialog.Builder(this).setTitle("Important moments").setView(body).setNegativeButton("Back",null).show()
    }
    private fun addCaptureTiming(card:GlowPanel,cfg:RecordingConfig) {
        durationEntry=timeInput(if(cfg.maxRecordingMinutes==0)"" else cfg.maxRecordingMinutes.toString()).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER;hint="Unlimited";contentDescription="Capture duration in minutes; empty means manual stop"
            filters=arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        card.addContent(inputRow("Duration · min",durationEntry!!),48);card.addGap(8)
        val at=getSharedPreferences("home_start",0).getLong("at",0)
        startDateButton=button(if(at>System.currentTimeMillis())"Start · ${java.text.SimpleDateFormat("MM/dd HH:mm",Locale.US).format(java.util.Date(at))}" else "Start time · Now") { homeStartDialog() }
        val startRow=LinearLayout(this)
        startRow.addView(startDateButton!!,LinearLayout.LayoutParams(0,dp(44),1f))
        startRow.addView(iconButton(HeaderIcon.DELETE) {
            getSharedPreferences("home_start",0).edit().remove("at").apply();RecordingAutomation.cancel(this);startDateButton?.text="Start time · Now";updateScheduleIcons()
        }.apply { squareFace=true;contentDescription="Clear scheduled start" },LinearLayout.LayoutParams(dp(44),dp(44)))
        card.addContent(startRow,44);card.addGap(8)
    }
    private fun homeStartDialog() {
        val at=getSharedPreferences("home_start",0).getLong("at",0)
        val initial=if(at>System.currentTimeMillis())LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at),ZoneId.systemDefault()) else LocalDateTime.now().plusMinutes(3).withSecond(0).withNano(0)
        val input=timeInput(initial.format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.US)))
        val body=scheduleDateRow("Start",input,initial)
        NeonDialog.Builder(this).setTitle("Capture start time").setView(body).setPositiveButton("Set") { _,_ ->
            try {
                val value=LocalDateTime.parse(input.text.toString(),DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.US)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                require(value>System.currentTimeMillis()) { "Choose a future start time." }
                getSharedPreferences("home_start",0).edit().putLong("at",value).apply();startDateButton?.text="Start · ${input.text}"
            } catch(t:Throwable) { showError(t) }
        }.setNeutralButton("Now") { _,_ -> getSharedPreferences("home_start",0).edit().remove("at").apply();startDateButton?.text="Start time · Now" }.setNegativeButton("Cancel",null).show()
    }
    private fun armHomeCapture(c:RecordingConfig,mode:CameraMode,start:Long) {
        val end=if(c.maxRecordingMinutes==0)Long.MAX_VALUE else start+c.maxRecordingMinutes*60000L
        getSharedPreferences("automation",0).edit().putBoolean("armed",true).putLong("start",start).putLong("end",end).putLong("activeEnd",0)
            .putString("mode",mode.name).putString("front",c.frontQuality).putString("rear",c.rearQuality).putBoolean("photos",c.photoMode).putBoolean("audioOnly",c.audioOnly)
            .putBoolean("screenMode",c.screenMode).putBoolean("mixedMode",c.mixedMode).putBoolean("screenAudio",c.screenAudio).putBoolean("screenInternal",c.screenInternalAudio)
            .putBoolean("mixedFront",c.mixedFront).putBoolean("mixedRear",c.mixedRear).putBoolean("mixedScreen",c.mixedScreen).putString("background",c.mixedBackground.name).putString("layout",c.mixedLayout)
            .putInt("fps",c.fps).putBoolean("continueVideoWithoutMic",c.continueVideoWithoutMic).putInt("minutes",c.maxRecordingMinutes).putInt("interval",c.photoIntervalSeconds).putBoolean("light",app.lightForNextRecording).putBoolean("screenLight",app.screenLightWanted).apply()
        RecordingAutomation.alarm(this,start,"start");RecordingAutomation.alarm(this,if(end==Long.MAX_VALUE)0 else end,"end")
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),902)
        getSharedPreferences("home_start",0).edit().remove("at").apply()
        app.status.update { it.copy(message="Scheduled capture saved. Open the app at the start time${if(c.requiresProjection)" and confirm Android screen sharing" else ""}.") }
        getSharedPreferences("runtime",0).edit().putString("lastStop",app.status.value.message).apply();showHome()
    }
    private fun matchesMode(clip:Clip):Boolean {
        val c=app.repository?.config() ?: return false
        return when { c.mixedMode -> clip.camera==CameraSource.MIXED;c.screenMode -> clip.camera==CameraSource.SCREEN;c.audioOnly -> clip.isAudio;c.photoMode -> clip.isPhoto;else -> !clip.isPhoto && !clip.isAudio && clip.camera in setOf(CameraSource.FRONT,CameraSource.REAR,CameraSource.LEGACY) }
    }
    private fun playLatestCapture() {
        if(app.status.value.phase!=Phase.IDLE)return
        scope.launch { try {
            val clip=withContext(Dispatchers.IO) { app.repository!!.list().filter { it.status==ClipStatus.READY && matchesMode(it) }.maxByOrNull { it.startMs } }
            if(clip==null)toast("No completed capture for this mode.") else openPlayer(clip)
        } catch(t:Throwable) { showError(t) } }
    }
    private fun addScreenAudioControls(card:GlowPanel,cfg:RecordingConfig) {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER;clipChildren=false;clipToPadding=false }
        screenMicButton=iconButton(HeaderIcon.MICROPHONE) { changeScreenAudio(false) }.apply { squareFace=true;toggleAppearance=true;isSelected=cfg.screenAudio }
        screenInternalButton=iconButton(HeaderIcon.INTERNAL_AUDIO) { changeScreenAudio(true) }.apply { squareFace=true;toggleAppearance=true;isSelected=cfg.screenInternalAudio }
        row.addView(screenMicButton,LinearLayout.LayoutParams(dp(48),dp(48)));row.addView(screenInternalButton,LinearLayout.LayoutParams(dp(48),dp(48)).apply { marginStart=dp(12) })
        card.addContent(row,48)
    }
    private fun changeScreenAudio(internal:Boolean) {
        val c=mixedPending ?: app.repository!!.config()
        val next=if(internal)c.copy(screenInternalAudio=!c.screenInternalAudio) else c.copy(screenAudio=!c.screenAudio)
        if(app.status.value.phase!=Phase.IDLE && next.screenInternalAudio && !c.requiresProjection) { toast("Stop and grant screen sharing before adding internal audio.");return }
        updateMixedConfig(next)
        screenMicButton?.isSelected=next.screenAudio;screenInternalButton?.isSelected=next.screenInternalAudio
    }
    private fun updateMixedConfig(next:RecordingConfig) {
        mixedPending=next;mixedSourcesUi.forEach { (s,b) -> b.isSelected=s in next.mixedSources };mixedBackgroundUi.forEach { (s,b) -> b.isSelected=s==next.mixedBackground };mixedBackgroundButton?.text="Background · ${next.mixedBackground.name}";mixedEditor?.config=next;mixedEditor?.invalidate()
        if(app.activeEngine!=null)app.activeEngine?.update(next,if(next.mixedMode)next.mixedSources else setOf(CameraSource.SCREEN))
        mixedSave?.cancel();mixedSave=scope.launch {
            delay(160)
            try { withContext(Dispatchers.IO) { app.repository!!.saveConfig(next) };if(app.activeEngine!=null) { CaptureRecovery(this@MainActivity).update(next);startService(Intent(this@MainActivity,RecordingService::class.java).setAction(RecordingService.UPDATE).putExtra("sources",(if(next.mixedMode)next.mixedSources else setOf(CameraSource.SCREEN)).map { it.name }.toTypedArray())) } }
            catch(t:Throwable) { showError(t) }
        }
    }
    private fun addMixedControls(card:GlowPanel,cfg:RecordingConfig) {
        mixedPending=cfg
        val presets=LinearLayout(this).apply { clipChildren=false;clipToPadding=false }
        listOf("Screen + Front" to false,"Screen + Both" to true).forEach { (label,both) -> presets.addView(button(label) {
            updateMixedConfig(MixedSetup.presentation(mixedPending ?: app.repository!!.config(),both))
        },LinearLayout.LayoutParams(0,dp(44),1f)) }
        card.addContent(presets,44);card.addGap(6)
        val row=LinearLayout(this).apply { clipChildren=false;clipToPadding=false;contentDescription="Mixed recording sources" }
        MixedSetup.sources.forEach { source ->
            val control=button(source.name.lowercase().replaceFirstChar { it.uppercase() }) {
                runCatching { MixedSetup.toggle(mixedPending ?: app.repository!!.config(),source) }.onSuccess(::updateMixedConfig).onFailure { toast(it.message ?: "Keep a source selected.") }
            }.apply { isSelected=source in cfg.mixedSources;toggleAppearance=true;contentDescription="Include ${source.name} source" }
            mixedSourcesUi[source]=control;row.addView(control,LinearLayout.LayoutParams(0,dp(44),1f))
        }
        card.addContent(row,44);card.addGap(6)
        val backgrounds=LinearLayout(this).apply { clipChildren=false;clipToPadding=false;contentDescription="Mixed background selection" }
        MixedSetup.sources.forEach { source ->
            val control=button("${source.name.lowercase().replaceFirstChar { it.uppercase() }} BG") {
                updateMixedConfig(MixedSetup.background(mixedPending ?: app.repository!!.config(),source))
            }.apply { isSelected=source==cfg.mixedBackground;toggleAppearance=true;contentDescription="Use ${source.name} as background" }
            mixedBackgroundUi[source]=control;backgrounds.addView(control,LinearLayout.LayoutParams(0,dp(44),1f))
        }
        card.addContent(backgrounds,44);card.addGap(8)
        val owner=Any();mixedPreviewOwner=owner
        val preview=TextureView(this).apply { contentDescription="Live mixed recording preview";surfaceTextureListener=object:TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture:android.graphics.SurfaceTexture,w:Int,h:Int) { mixedSurface?.release();mixedSurface=Surface(texture);app.preview(CameraSource.MIXED,owner,mixedSurface) }
            override fun onSurfaceTextureSizeChanged(texture:android.graphics.SurfaceTexture,w:Int,h:Int)=Unit
            override fun onSurfaceTextureUpdated(texture:android.graphics.SurfaceTexture)=Unit
            override fun onSurfaceTextureDestroyed(texture:android.graphics.SurfaceTexture):Boolean { app.preview(CameraSource.MIXED,owner,null);mixedSurface?.release();mixedSurface=null;return true }
        } }
        mixedTexture=preview
        val bounds=getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val outputWidth=bounds.width().takeIf { it>0 } ?: resources.displayMetrics.widthPixels
        val outputHeight=bounds.height().takeIf { it>0 } ?: resources.displayMetrics.heightPixels
        val frame=MixedPreviewFrame(this,outputWidth.toFloat()/outputHeight.coerceAtLeast(1)).apply { setBackgroundColor(android.graphics.Color.BLACK);addView(preview,FrameLayout.LayoutParams(-1,-1)) }
        mixedEditor=MixedLayoutEditor(this,cfg,::updateMixedConfig);frame.addView(mixedEditor,FrameLayout.LayoutParams(-1,-1))
        card.addContent(frame,280);card.addGap(8)
    }
    private fun toggleMonitor(source:CameraSource) {
        if(monitorOverlay!=null) { collapseMonitor();return }
        if(whiteLamp!=null)app.screenLightWanted=false
        disableScreenLight()
        val monitor=cameraPanels[source]?.monitor ?: return
        val parent=monitor.parent as? android.view.ViewGroup ?: return
        expandedMonitor=monitor;expandedParent=parent;expandedIndex=parent.indexOfChild(monitor);expandedParams=monitor.layoutParams
        parent.removeView(monitor)
        val overlay=FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        overlay.addView(monitor,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        monitorOverlay=overlay;addContentView(overlay,android.view.ViewGroup.LayoutParams(-1,-1))
        window.insetsController?.hide(WindowInsets.Type.systemBars())
    }
    private fun collapseMonitor() {
        val overlay=monitorOverlay ?: return
        monitorOverlay=null
        expandedMonitor?.let { monitor -> overlay.removeView(monitor);expandedParent?.addView(monitor,expandedIndex,expandedParams) }
        (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
        expandedMonitor=null;expandedParent=null;expandedParams=null
        window.insetsController?.show(WindowInsets.Type.systemBars())
    }
    private fun enableScreenLight() {
        if(!activityVisible || screen!="home" || app.status.value.phase!=Phase.RECORDING || !app.screenLightWanted)return
        collapseMonitor()
        val cameras=app.status.value.cameras
        val source=listOf(CameraSource.FRONT,CameraSource.REAR).firstOrNull { cameras[it]?.recording==true } ?: return
        val monitor=cameraPanels[source]?.monitor ?: return
        if(whiteLamp!=null) { if(lampMonitor===monitor)return;disableScreenLight() }
        val parent=monitor.parent as? android.view.ViewGroup ?: return
        lampMonitor=monitor;lampParent=parent;lampIndex=parent.indexOfChild(monitor);lampParams=monitor.layoutParams
        parent.removeView(monitor)
        val overlay=FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.WHITE) }
        val width=(resources.displayMetrics.widthPixels*.25f).toInt()
        overlay.addView(monitor,FrameLayout.LayoutParams(width,width*4/3,Gravity.CENTER))
        val controls=LinearLayout(this).apply { setPadding(dp(8),dp(8),dp(8),dp(8)) }
        controls.addView(iconButton(HeaderIcon.CLOSE) { app.screenLightWanted=false;renderScreenLight() }.apply { squareFace=true;contentDescription="Turn off screen light";tooltipText="Turn off screen light" },LinearLayout.LayoutParams(dp(48),dp(48)))
        overlay.addView(controls,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.RIGHT))
        val gesture=GestureDetector(this,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:MotionEvent)=true
            override fun onDoubleTap(e:MotionEvent):Boolean { app.screenLightWanted=false;renderScreenLight();return true }
        })
        overlay.setOnTouchListener { _,e -> gesture.onTouchEvent(e) }
        overlay.contentDescription="White screen light. Tap the top-right Close to turn off; double tap white area or press Back also turns off."
        oldBrightness=window.attributes.screenBrightness
        window.attributes=window.attributes.apply { screenBrightness=1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.insetsController?.hide(WindowInsets.Type.systemBars())
        whiteLamp=overlay;addContentView(overlay,android.view.ViewGroup.LayoutParams(-1,-1));renderTorch()
    }
    private fun disableScreenLight() {
        val overlay=whiteLamp ?: return
        whiteLamp=null
        lampMonitor?.let { monitor -> overlay.removeView(monitor);lampParent?.addView(monitor,lampIndex,lampParams) }
        (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
        lampMonitor=null;lampParent=null;lampParams=null
        window.attributes=window.attributes.apply { screenBrightness=oldBrightness }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.insetsController?.show(WindowInsets.Type.systemBars())
    }
    private fun renderScreenLight() {
        val selected=app.screenLightWanted
        screenLightButton?.apply { isSelected=selected;stateDescription=if(selected)"Selected" else "Off";contentDescription="Front screen light, ${if(selected)"selected, turn off" else "off, select"}" }
        val recording=app.status.value.phase==Phase.RECORDING && app.status.value.cameras.any { (source,state) -> source in setOf(CameraSource.FRONT,CameraSource.REAR) && state.recording }
        val photos=app.repository?.config()?.photoMode==true
        if(selected && recording && activityVisible && screen=="home") {
            enableScreenLight()
            val lit=!photos || app.photoLighting.value.isNotEmpty()
            whiteLamp?.setBackgroundColor(if(lit)android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            window.attributes=window.attributes.apply { screenBrightness=if(lit)1f else oldBrightness }
        } else disableScreenLight()
    }
    private var selectedCameras=setOf<CameraSource>()
    private fun selectCamera(mode:CameraMode) {
        if(app.status.value.phase!=Phase.IDLE) { toggleRecording(mode);return }
        val sources=CapturePolicy.sources(mode)
        selectedCameras=if(selectedCameras.containsAll(sources))selectedCameras-sources else selectedCameras+sources
        val chosen=if(selectedCameras.size==2)CameraMode.BOTH else if(CameraSource.FRONT in selectedCameras)CameraMode.FRONT else CameraMode.REAR
        scope.launch { try { withContext(Dispatchers.IO) { val repo=app.repository!!;repo.saveConfig(repo.config().copy(cameraMode=chosen)) };renderStatus() } catch(t:Throwable) { if(t !is CancellationException)showError(t) } }
        renderStatus()
    }
    private fun selectCaptureMode(index:Int) {
        mixedSave?.cancel();mixedSave=null;mixedPending=null
        if(app.status.value.phase!=Phase.IDLE || modeSaving?.isActive==true || pendingStart || startPreparing) { toast("Stop capture before changing mode.");return }
        modeSaving=scope.launch {
            try { withContext(Dispatchers.IO) { val repo=app.repository!!;repo.saveConfig(repo.config().copy(photoMode=index==2,audioOnly=index==1,screenMode=index==3,mixedMode=index==4)) };modeSaving=null;showHome() }
            catch(t:Throwable) { if(t !is CancellationException)showError(t) }
        }
    }
    private fun photoIntervalDialog() {
        if(app.status.value.phase!=Phase.IDLE) { toast("Stop capture before changing interval.");return }
        val input=EditText(this).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(app.repository!!.config().photoIntervalSeconds.toString());contentDescription="Photo interval seconds" }
        val duration=EditText(this).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;setText(app.repository!!.config().maxRecordingMinutes.takeIf { it>0 }?.toString() ?: "");hint="Unlimited";contentDescription="Photo capture duration minutes" }
        val body=column();body.addWide(inputRow("Seconds",input),48);body.addGap(12);body.addWide(inputRow("Duration · min",duration),48)
        NeonDialog.Builder(this).setTitle("Photo interval").setView(body).setPositiveButton("Apply") { _,_ ->
            val seconds=normalDigits(input.text.toString()).toIntOrNull()
            if(seconds==null || seconds !in 1..86400) { toast("Photo interval must be 1–86400 seconds.");return@setPositiveButton }
            scope.launch { try { withContext(Dispatchers.IO) { val repo=app.repository!!;repo.saveConfig(repo.config().copy(photoIntervalSeconds=seconds,maxRecordingMinutes=normalDigits(duration.text.toString()).let { if(it.isBlank())0 else it.toIntOrNull()?.takeIf { n -> n in 1..1440 } ?: throw IllegalArgumentException("Duration must be 1–1440 minutes or empty.") })) };showHome() } catch(t:Throwable) { showError(t) } }
        }.setNegativeButton("Close",null).show()
    }
    private fun toggleScreenMicrophone() {
        if(app.status.value.phase!=Phase.IDLE || pendingStart) { toast("Stop capture before changing audio.");return }
        scope.launch { try { withContext(Dispatchers.IO) { val repo=app.repository!!;repo.saveConfig(repo.config().copy(screenAudio=!repo.config().screenAudio)) };showHome() } catch(t:Throwable) { showError(t) } }
    }
    private fun showPhoto(clip:Clip) {
        val returnScreen=if(screen in setOf("player","photo"))previousScreen else screen
        releasePlayer();previousScreen=returnScreen;screen="photo";currentClip=clip
        val page=newPage("Photo","${timestamp(clip.startMs)} · Photo · ${fileSize(clip.bytes)}",back=true)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription=clip.name }
        page.addView(image,LinearLayout.LayoutParams(-1,0,1f))
        page.addWide(mediaControls(clip))
        photoLoad=scope.launch {
            var pin:AutoCloseable?=null
            try {
                val bitmap=withContext(Dispatchers.IO) {
                    pin=app.repository!!.pin(setOf(clip.id))
                    val uri=android.net.Uri.parse(clip.uri)
                    val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
                    var sample=1
                    while(options.outWidth/sample>resources.displayMetrics.widthPixels*2 || options.outHeight/sample>resources.displayMetrics.heightPixels*2)sample*=2
                    options.inJustDecodeBounds=false;options.inSampleSize=sample
                    contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it,null,options) } ?: error("Photo unavailable.")
                }
                if(screen=="photo" && root===page) { playerPin=pin;pin=null;image.setImageBitmap(bitmap) } else bitmap.recycle()
            } catch(t:Throwable) { if(t !is CancellationException)showError(t) }
            finally { pin?.close() }
        }
    }

    private fun toggleTorch() {
        val state=app.torch.value
        if(app.status.value.phase==Phase.IDLE) {
            app.lightForNextRecording=!app.lightForNextRecording;renderTorch()
        } else if(state.available && !state.pending && app.activeEngine!=null) {
            val enabled=!(if(app.repository?.config()?.photoMode==true)app.lightForNextRecording else state.enabled)
            app.lightForNextRecording=enabled
            startService(Intent(this,RecordingService::class.java).setAction(RecordingService.TORCH).putExtra("enabled",enabled))
        }
    }
    private fun renderTorch() {
        val state=app.torch.value
        val idle=app.status.value.phase==Phase.IDLE
        val selected=if(idle || app.repository?.config()?.photoMode==true)app.lightForNextRecording else state.enabled
        listOfNotNull(torchButton).forEach { it.apply {
            isEnabled=if(idle)!startPreparing && !pendingStart else state.available && !state.pending && app.status.value.phase==Phase.RECORDING
            isSelected=selected
            contentDescription="Recording light, ${if(selected)"on, turn off" else "off, turn on"}"
            tooltipText=if(idle)"Light ${if(selected)"on" else "off"} for next recording" else state.message.takeIf { it.isNotBlank() } ?: "Rear flashlight"
            stateDescription=if(state.pending)"Updating" else if(idle)"${if(selected)"On" else "Off"} for next recording" else if(state.message.isNotBlank())state.message else if(selected)"On" else "Off"
        } }
        if(state.message.startsWith("Light ") && state.message!=lastTorchMessage)toast(state.message)
        lastTorchMessage=state.message
    }
    private fun playLatest(source:CameraSource) {
        scope.launch {
            try {
                val clip=withContext(Dispatchers.IO) { app.repository?.list()?.filter { it.camera==source && it.status==ClipStatus.READY && it.kind!=ClipKind.EXTRACT }?.maxByOrNull { it.startMs } }
                if(clip==null)toast("No completed ${source.name.lowercase()} videos yet.") else openPlayer(clip,false)
            } catch(t:Throwable) { showError(t) }
        }
    }
    private fun loadQualities() {
        if(screen!="home" || cameraPanels.isEmpty())return
        qualityJob?.cancel()
        qualityJob=scope.launch {
            val config=app.repository?.config() ?: return@launch
            val dual=app.status.value.cameras.count { it.value.recording }==2
            val effective=if(dual)config.copy(cameraMode=CameraMode.BOTH) else config.copy(cameraMode=CameraMode.FRONT)
            val panels=cameraPanels.toMap()
            for((source,panel) in panels) {
                val result=withContext(Dispatchers.Default) { runCatching { app.capture?.create(this@MainActivity)?.qualities(source,effective).orEmpty() } }
                if(cameraPanels[source]!==panel)return@launch
                panel.qualities(result.getOrDefault(emptyList()),if(source==CameraSource.FRONT)config.frontQuality else config.rearQuality,result.exceptionOrNull()?.message)
            }
        }
    }
    private fun changeQuality(source:CameraSource,quality:CaptureQuality) {
        if(startPreparing || pendingStart || modeSaving?.isActive==true || app.status.value.cameras.values.any { it.pending })return
        modeSaving=scope.launch {
            try {
                val running=app.status.value.cameras.filterValues { it.recording }.keys
                withContext(Dispatchers.IO) {
                    val repo=app.repository ?: error("Storage module unavailable.")
                    val c=repo.config()
                    val next=if(source==CameraSource.FRONT)c.copy(frontQuality=quality.key) else c.copy(rearQuality=quality.key)
                    if(source in running) {
                        val support=app.capture!!.create(this@MainActivity).support(next.copy(cameraMode=if(running.size==2)CameraMode.BOTH else if(source==CameraSource.FRONT)CameraMode.FRONT else CameraMode.REAR))
                        check(support.available) { support.detail }
                    }
                    repo.saveConfig(next)
                }
                if(source in running) {
                    app.status.update { old -> old.copy(error=false,message="Applying quality…",cameras=old.cameras+(source to old.cameras.getValue(source).copy(pending=true))) }
                    startService(Intent(this@MainActivity,RecordingService::class.java).setAction(RecordingService.UPDATE).putExtra("sources",running.map { it.name }.toTypedArray()))
                }
                loadQualities()
            } catch(t:Throwable) { loadQualities();showError(t) }
            finally { modeSaving=null;renderStatus() }
        }
        renderStatus()
    }
    private fun showArchive() {
        releasePlayer();screen="archive";archiveAll=false
        val page=newPage("Recordings")
        page.clipChildren=true;page.clipToPadding=true
        selectionBar=column().apply {
            visibility=View.GONE;clipChildren=true;clipToPadding=true
            selectionCount=label("0 files selected",13f).apply { includeFontPadding=false;setLineSpacing(0f,1f);gravity=Gravity.CENTER_VERTICAL }
            addWide(selectionCount!!,28)
            val actions=LinearLayout(this@MainActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            selectAllCheck=SelectAllCheckBox(this@MainActivity).apply { text="All";contentDescription="Select all files";setOnClickListener { toggleAllArchive() } }
            actions.addView(selectAllCheck!!,LinearLayout.LayoutParams(0,dp(48),1f))
            actions.addView(iconButton(HeaderIcon.DELETE) { deleteSelected() }.apply { squareFace=true;text="Delete selected files";contentDescription=text },LinearLayout.LayoutParams(dp(40),dp(40)))
            selectionStats=iconButton(HeaderIcon.STATISTICS) { showSelectionStatistics() }.apply { squareFace=true;isEnabled=false }
            actions.addView(selectionStats!!,LinearLayout.LayoutParams(dp(40),dp(40)))
            actions.addView(iconButton(HeaderIcon.SHARE) { shareFiles(adapter?.selectedClips().orEmpty()) }.apply { squareFace=true;contentDescription="Share selected files" },LinearLayout.LayoutParams(dp(40),dp(40)))
            actions.addView(iconButton(HeaderIcon.COPY) { copyFiles(adapter?.selectedClips().orEmpty()) }.apply { squareFace=true;contentDescription="Copy selected files" },LinearLayout.LayoutParams(dp(40),dp(40)))
            actions.addView(iconButton(HeaderIcon.CLOSE) { adapter?.clearSelection() }.apply { squareFace=true;text="Cancel selection";contentDescription=text },LinearLayout.LayoutParams(dp(40),dp(40)))
            addWide(actions,48);addGap(12)
        }
        page.addWide(selectionBar!!,88)
        adapter=ClipListAdapter({ openPlayer(it,false) },{ protectClip(it) }) {
            selectionBar?.visibility=if(adapter?.selecting==true)View.VISIBLE else View.GONE
            val count=adapter?.selectedClips()?.size ?: 0
            selectionCount?.text="$count ${if(count==1)"file" else "files"} selected"
            selectAllCheck?.bind(adapter?.allSelected==true,count>0)
            selectionStats?.isEnabled=count>0
        }
        val list=layoutInflater.inflate(com.duorecord.app.R.layout.recording_list,page,false) as RecyclerView
        list.layoutManager=LinearLayoutManager(this);list.adapter=adapter;list.setHasFixedSize(true);list.itemAnimator=null
        list.setPadding(0,0,0,0)
        list.addItemDecoration(object:RecyclerView.ItemDecoration() {
            override fun getItemOffsets(out:android.graphics.Rect,view:View,parent:RecyclerView,state:RecyclerView.State) {
                val position=parent.getChildAdapterPosition(view)
                out.set(0,0,0,if(position>=0 && position<state.itemCount-1)dp(8) else 0)
            }
        })
        val viewport=ClippedListFrame(this)
        viewport.addView(list,FrameLayout.LayoutParams(-1,-1).apply { rightMargin=dp(20) })
        viewport.addView(ArchiveScrollHandle(this,list),FrameLayout.LayoutParams(dp(20),-1,Gravity.END))
        page.addView(viewport,LinearLayout.LayoutParams(-1,0,1f))
        archiveLimitLabel=label("Latest ${app.repository?.config()?.archiveInitialLimit ?: 100} files · Refresh for all",12f,Palette.muted)
        page.addView(archiveLimitLabel,LinearLayout.LayoutParams(-1,dp(22)))
        refreshData();refreshArchive()
        armArchiveRefresh()
    }
    private val archiveTicker=object:Runnable { override fun run() {
        if(activityVisible && screen=="archive") { refreshArchive();armArchiveRefresh() }
    } }
    private fun armArchiveRefresh() {
        mainHandler.removeCallbacks(archiveTicker)
        if(activityVisible && screen=="archive")mainHandler.postDelayed(archiveTicker,(app.repository?.config()?.archiveRefreshSeconds ?: 2)*1000L)
    }
    private fun refreshArchive(manual:Boolean=false) {
        if(screen!="archive")return
        if(manual) { archiveAll=true;archiveLimitLabel?.text="Loading all files…";refreshData() }
        if(archiveScanJob?.isActive==true) { archiveManualRefresh=archiveManualRefresh || manual;return }
        archiveManualRefresh=manual
        val owner=root
        archiveScanJob=scope.launch {
            try {
                do {
                    val full=archiveManualRefresh;archiveManualRefresh=false
                    withContext(Dispatchers.IO) {
                        val active=app.status.value.phase!=Phase.IDLE || app.export.value.busy
                        if(full)app.repository?.reconcile(active) else app.repository?.reconcileRecent(active)
                    }
                    if(screen=="archive" && root===owner)refreshData()
                } while(archiveManualRefresh && screen=="archive" && root===owner)
            } catch(t:Throwable) {
                if(t is CancellationException)throw t
                if(manual)showError(t) else android.util.Log.w("ArchiveRefresh","Could not refresh recordings",t)
            }
        }
    }
    private fun showSelectionStatistics(all:Boolean=false) {
        val selected=(if(all)allMedia else adapter?.selectedClips().orEmpty()).distinctBy { it.id }
        if(!all && selected.isEmpty())return
        val body=column()
        fun group(title:String,files:List<Clip>,cameras:Boolean) {
            val box=GlowPanel(this,title).apply { setPadding(dp(12),dp(12),dp(12),dp(24)) }
            box.addContent(label("${files.size} ${if(files.size==1)"file" else "files"} · ${fileSize(files.sumOf { it.bytes.coerceAtLeast(0) })}",16f,Palette.mint,true))
            if(cameras) {
                listOf(CameraSource.REAR to "Rear",CameraSource.FRONT to "Front",CameraSource.SCREEN to "Screen",CameraSource.MIXED to "Mixed",CameraSource.LEGACY to "Other / combined").forEach { (source,name) ->
                    val part=files.filter { it.camera==source }
                    if(source in setOf(CameraSource.REAR,CameraSource.FRONT) || part.isNotEmpty()) { box.addGap(6);box.addContent(label("$name: ${part.size} · ${fileSize(part.sumOf { it.bytes.coerceAtLeast(0) })}",14f)) }
                }
            }
            body.addWide(box);body.addGap(8)
        }
        group(if(all)"All files" else "All selected",selected,false)
        group("Photos",selected.filter { it.isPhoto },true)
        group("Audio",selected.filter { it.isAudio },false)
        group("Videos",selected.filter { !it.isPhoto && !it.isAudio },true)
        NeonDialog.Builder(this).setTitle(if(all)"All files statistics" else "Selection statistics").setView(body).setNegativeButton("Back",null).show()
    }
    private fun deleteSelected() {
        val selected=adapter?.selectedClips().orEmpty();if(selected.isEmpty()) { toast("Select files first.");return }
        val cameraCounts=selected.groupingBy { it.camera }.eachCount().entries.joinToString { "${it.key.name}: ${it.value}" }
        NeonDialog.Builder(this).setTitle("Delete selected files?")
            .setMessage("${selected.size} files · ${fileSize(selected.sumOf { it.bytes })}\n$cameraCounts\nIncludes protected files if selected. Files are permanently deleted. A row containing both cameras selects both files.")
            .setPositiveButton("Delete") { _,_ -> scope.launch {
                try { withContext(Dispatchers.IO) { app.repository!!.delete(selected.map { it.id }.toSet()) };adapter?.clearSelection();app.changed() }
                catch(t:Throwable) { showError(t) }
                finally { refreshArchive() }
            } }.setNegativeButton("Back",null).show()
    }
    private fun refreshData() {
        if(refreshing) { refreshPending=true;return }
        refreshing=true
        scope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val repo=app.repository
                    if(repo==null)Triple(emptyList<Clip>(),"Storage module unavailable.",false)
                    else {
                        val clips=repo.list().map { clip ->
                            if(clip.status!=ClipStatus.WRITING)clip else clip.copy(bytes=runCatching {
                                contentResolver.openFileDescriptor(android.net.Uri.parse(clip.uri),"r")?.use { it.statSize.coerceAtLeast(clip.bytes) } ?: clip.bytes
                            }.getOrDefault(clip.bytes))
                        };val config=repo.config();val ready=repo.folderReady()
                        val count=clips.count { it.kind==ClipKind.ROLLING && it.status==ClipStatus.READY }
                        val rolling=clips.filter { it.kind==ClipKind.ROLLING }.sumOf { it.bytes }
                        val saved=clips.filter { it.protected }.sumOf { it.bytes }
                        val text=if(!ready)"Storage unavailable."
                        else "Folder: ${repo.storagePath()}\nFile duration: ${config.segmentSeconds} sec · Target: ${config.fps} fps\nSession limit: ${config.maxRecordingMinutes} min\nRecent: $count files · ${fileSize(rolling)} / ${fileSize(config.quotaBytes)}\nProtected: ${fileSize(saved)} / ${fileSize(config.savedQuotaBytes)}"
                        Triple(clips,text,ready)
                    }
                }
                stats=result.second;storageLabel?.text=stats
                rollingBytes=result.first.filter { it.kind==ClipKind.ROLLING }.sumOf { it.bytes };updateHealth(true)
                allMedia=result.first
                adapter?.let { a -> a.allFiles=result.first;a.submit(if(archiveAll)result.first else result.first.sortedByDescending { it.startMs }.take(app.repository?.config()?.archiveInitialLimit ?: 100)) }
                archiveLimitLabel?.text=if(archiveAll)"All ${result.first.size} files" else "Latest ${minOf(app.repository?.config()?.archiveInitialLimit ?: 100,result.first.size)} of ${result.first.size} files · Refresh for all"
                cameraPanels.forEach { (source,panel) -> panel.playbackAvailable=app.player!=null && result.first.any { it.camera==source && it.kind!=ClipKind.EXTRACT && it.status==ClipStatus.READY } }
            } catch(t:Throwable) { if(t !is CancellationException)showError(t) }
            finally { refreshing=false;if(refreshPending) { refreshPending=false;refreshData() } }
        }
    }
    private fun renderStatus() {
        renderTorch()
        renderScreenLight()
        updateHealth()
        val state=app.status.value
        if(state.phase!=Phase.IDLE && app.repository?.config()?.requiresProjection==true)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val temporary=transientMessage?.takeIf { SystemClock.uptimeMillis()<transientUntil }
        val lastReason=getSharedPreferences("runtime",0).getString("lastStop",null)
        val statusText=temporary ?: if(state.phase==Phase.IDLE && state.message=="Ready" && !lastReason.isNullOrBlank())lastReason else if(state.phase==Phase.RECORDING && !state.error)"${state.message} · ${clock(app.captureClock.elapsedMs())}" else state.message
        statusLabel?.show(statusText,state.error && temporary==null,state.phase!=Phase.IDLE)
        val busy=state.phase in setOf(Phase.STARTING,Phase.STOPPING,Phase.PAUSING) || state.cameras.values.any { it.pending } || startPreparing || pendingStart || modeSaving?.isActive==true
        val enabled=!busy && app.capture!=null && app.repository!=null
        val active=state.cameras.filterValues { it.recording }.keys
        if(leaveForMixedCapture && state.phase==Phase.RECORDING && (CameraSource.MIXED in active || CameraSource.SCREEN in active) && !busy) {
            leaveForMixedCapture=false;mainHandler.post { if(activityVisible && app.status.value.phase==Phase.RECORDING)moveTaskToBack(true) }
        }
        if(state.phase==Phase.IDLE)leaveForMixedCapture=false
        captureChoices.forEach { it.captureState=if(it.isSelected) { if(state.phase==Phase.RECORDING)2 else 1 } else 0;it.isEnabled=state.phase==Phase.IDLE && !busy;it.pulseGlyph=it.isSelected && state.phase==Phase.RECORDING;it.invalidate() }
        screenMicButton?.isEnabled=!busy;screenInternalButton?.isEnabled=!busy
        durationEntry?.isEnabled=state.phase==Phase.IDLE && !busy;startDateButton?.isEnabled=state.phase==Phase.IDLE && !busy
        latestPlayButton?.isEnabled=state.phase==Phase.IDLE && !busy && (allMedia.any { it.status==ClipStatus.READY && matchesMode(it) } || app.latestCapture.value?.let { matchesMode(it) && it.status==ClipStatus.READY }==true)
        transport[HeaderIcon.RECORD]?.apply {
            val paused=state.phase==Phase.USER_PAUSED
            icon=if(paused)HeaderIcon.PAUSE else HeaderIcon.RECORD
            isEnabled=((state.phase==Phase.IDLE && enabled) || state.phase in setOf(Phase.RECORDING,Phase.USER_PAUSED)) && !busy
            contentDescription=when(state.phase) { Phase.RECORDING -> "Pause recording";Phase.USER_PAUSED -> "Resume recording in same file";else -> "Record" };tooltipText=contentDescription
            captureState=if(state.phase==Phase.RECORDING)2 else if(paused || state.phase==Phase.STARTING || startPreparing || pendingStart)1 else 0
        }
        transport[HeaderIcon.STOP]?.isEnabled=state.phase!=Phase.IDLE
        transport[HeaderIcon.SCHEDULE]?.isEnabled=state.phase==Phase.IDLE && !busy
        transport[HeaderIcon.FOLDER]?.isEnabled=app.repository!=null
        transport[HeaderIcon.BOOKMARK]?.apply {
            isEnabled=state.phase in setOf(Phase.IDLE,Phase.RECORDING,Phase.USER_PAUSED)
            contentDescription=if(state.phase==Phase.RECORDING)"Mark important moment; hold for saved marks" else "Saved important moments"
        }
        modeButtons[CameraMode.BOTH]?.apply {
            (this as? SignButton)?.captureState=if(busy && requestedMode==CameraMode.BOTH)1 else if(active.size==2)2 else 0
            isEnabled=state.phase==Phase.PAUSED || enabled;isSelected=active.size==2;text=if(state.phase==Phase.PAUSED)"Stop recovery" else if(busy)"Please wait…" else if(isSelected)"Stop both" else "Start both"
            contentDescription=if(state.phase==Phase.PAUSED)"Cancel automatic recovery" else if(isSelected)"Both cameras recording. Stop both." else "Start both cameras."
        }
        audioRecordButton?.apply {
            val mixed=app.repository?.config()?.mixedMode==true
            val screenMode=app.repository?.config()?.screenMode==true
            val recording=(if(mixed)CameraSource.MIXED else if(screenMode)CameraSource.SCREEN else CameraSource.AUDIO) in active
            (this as? SignButton)?.captureState=if(busy)1 else if(recording)2 else 0
            isEnabled=state.phase==Phase.PAUSED || enabled;isSelected=recording
            text=if(state.phase==Phase.PAUSED)"Stop recovery" else if(busy)"Please wait…" else if(recording) { if(mixed)"Stop mixed" else if(screenMode)"Stop screen" else "Stop audio" } else if(mixed)"Start mixed" else if(screenMode)"Start screen" else "Start audio"
            contentDescription=text
        }
        cameraPanels.forEach { (source,panel) ->
            var capture=state.cameras[source] ?: CameraCaptureStatus()
            if((state.phase==Phase.STARTING || startPreparing || pendingStart) && source in CapturePolicy.sources(requestedMode))capture=capture.copy(pending=true)
            panel.initialSelection=source in selectedCameras
            panel.render(capture,enabled && state.phase !in setOf(Phase.PAUSED,Phase.USER_PAUSED,Phase.PAUSING))
        }
        val signature=active.sortedBy { it.ordinal }.joinToString()
        if(signature!=qualitySignature) { qualitySignature=signature;loadQualities() }
    }
    private fun renderExport() {
        val state=app.export.value
        exportPanel?.visibility=if(state.message.isBlank() && !state.busy)View.GONE else View.VISIBLE
        exportLabel?.show(state.message,state.error,state.busy)
        exportLabel?.visibility=if(state.message.isBlank())View.GONE else View.VISIBLE
        exportProgress?.visibility=if(state.busy)View.VISIBLE else View.GONE
        exportProgress?.isIndeterminate=state.busy && state.progress==0
        exportProgress?.progress=state.progress
        if(state.retryIntent!=null && state!==handledSavedExport && !isDestroyed) {
            handledSavedExport=state
            NeonDialog.Builder(this).setTitle("Saved space is full")
                .setMessage("Increase the saved limit to keep all protected videos, or delete the oldest saved files to make room for this export.")
                .setPositiveButton("Increase limit") { _,_ -> scope.launch {
                    try {
                        withContext(Dispatchers.IO) { val repo=app.repository!!;val config=repo.config();val gb=1073741824L;repo.saveConfig(config.copy(savedQuotaBytes=maxOf(config.savedQuotaBytes+gb,((state.requiredSavedBytes+gb-1)/gb)*gb))) }
                        startExport(Intent(state.retryIntent))
                    } catch(t:Throwable) { showError(t) }
                } }
                .setNeutralButton("Delete oldest saved") { _,_ -> startExport(Intent(state.retryIntent).putExtra("rotateSaved",true)) }
                .setNegativeButton("Cancel",null).show()
        }
    }
    private fun toggleRecording(mode:CameraMode,scheduled:Boolean=false,valuesApplied:Boolean=false) {
        if(!scheduled && !valuesApplied && app.status.value.phase==Phase.IDLE) {
            mixedSave?.cancel();mixedSave=null
            val raw=normalDigits(durationEntry?.text?.toString() ?: app.repository!!.config().maxRecordingMinutes.let { if(it==0)"" else it.toString() }).trim()
            val minutes=if(raw.isEmpty())0 else raw.toIntOrNull()
            if(minutes==null || minutes !in 0..1440) { toast("Enter 1–1440 minutes, or leave empty for no time limit.");return }
            if(startPreparing)return
            startPreparing=true;renderStatus()
            val config=(mixedPending ?: app.repository!!.config()).copy(maxRecordingMinutes=minutes)
            val at=getSharedPreferences("home_start",0).getLong("at",0)
            scope.launch { try {
                withContext(Dispatchers.IO) { app.repository!!.saveConfig(config) }
                startPreparing=false
                mixedPending=null
                if(at>System.currentTimeMillis()) { armHomeCapture(config,mode,at);return@launch }
                toggleRecording(mode,scheduled,true)
            } catch(t:Throwable) { showError(t) } finally { startPreparing=false;renderStatus() } };return
        }
        if(!scheduled && app.status.value.phase==Phase.IDLE)getSharedPreferences("automation",0).edit().putLong("activeEnd",0).apply()
        val state=app.status.value
        if(state.phase==Phase.PAUSED) { stopRecording();return };if(state.phase in setOf(Phase.USER_PAUSED,Phase.PAUSING))return
        if(state.phase in setOf(Phase.STARTING,Phase.STOPPING,Phase.PAUSING) || state.cameras.values.any { it.pending } || startPreparing || pendingStart || modeSaving?.isActive==true)return
        val active=state.cameras.filterValues { it.recording }.keys
        captureModeButton?.apply {
            captureState=if(state.phase==Phase.STARTING || startPreparing || pendingStart || state.cameras.values.any { it.pending })1 else if(state.phase==Phase.RECORDING)2 else 0
            stateDescription=when(captureState) { 1 -> "Starting";2 -> "Recording";else -> "Ready" }
        }
        val cfg=app.repository?.config() ?: RecordingConfig()
        if(cfg.screenMode || cfg.mixedMode) {
            if(state.phase==Phase.IDLE && !ScreenControls.floating(this@MainActivity) && !ScreenControls.notificationsAllowed(this)) {
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED && !getSharedPreferences("screen_controls",0).getBoolean("asked_notifications",false)) {
                    getSharedPreferences("screen_controls",0).edit().putBoolean("asked_notifications",true).apply()
                    pendingStart=true;renderStatus();requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),12)
                } else notificationControlsSettings()
                return
            }
            if(CameraSource.SCREEN in active || CameraSource.MIXED in active) { stopRecording();return }
            val missing=CapturePermissions.runtime(cfg).filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
            if(missing.isNotEmpty()) { pendingStart=true;renderStatus();requestPermissions(missing.toTypedArray(),11);return }
            if((cfg.mixedMode || cfg.screenMode) && ScreenControls.floating(this@MainActivity) && !android.provider.Settings.canDrawOverlays(this)) {
                pendingStart=true;startActivityForResult(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")),904);return
            }
            if(!cfg.requiresProjection) { startScreenCapture(null);return }
            pendingStart=true;renderStatus()
            try { startActivityForResult(screenConsentIntent(),903) }
            catch(t:Throwable) { pendingStart=false;renderStatus();showError(t) }
            return
        }
        val target=if(cfg.audioOnly) { if(CameraSource.AUDIO in active)emptySet() else setOf(CameraSource.AUDIO) } else CapturePolicy.toggle(active,mode)
        if(target.isEmpty()) { stopRecording();return }
        requestedMode=if(cfg.audioOnly)cfg.cameraMode else if(target.size==2)CameraMode.BOTH else if(CameraSource.FRONT in target)CameraMode.FRONT else CameraMode.REAR
        val missing=CapturePermissions.runtime(cfg).filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) { pendingStart=true;renderStatus();requestPermissions(missing.toTypedArray(),10);return }
        prepareStart()
    }
    @Deprecated("Platform screen-capture consent result")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==904) { pendingStart=false;if(android.provider.Settings.canDrawOverlays(this))toggleRecording(CameraMode.BOTH,valuesApplied=true) else { toast("Floating controls need display-over-other-apps permission.");renderStatus() };return }
        if(requestCode!=903)return
        pendingStart=false
        if(resultCode!=Activity.RESULT_OK || data==null || app.repository?.config()?.let { it.screenMode || it.mixedMode }!=true) {
            getSharedPreferences("runtime",0).edit().putString("lastStop","Screen sharing permission was cancelled.").apply();renderStatus();return
        }
        startScreenCapture(data)
    }
    private fun screenConsentIntent():Intent {
        val manager=getSystemService(android.media.projection.MediaProjectionManager::class.java)
        return if(Build.VERSION.SDK_INT>=34)manager.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
    }
    private fun startScreenCapture(grant:Intent?) {
        pendingStart=false
        if(app.status.value.phase!=Phase.IDLE)return
        try {
            app.latestCapture.value=null
            leaveForMixedCapture=app.repository?.config()?.let { it.screenMode || it.mixedMode && it.mixedScreen }==true
            app.status.update { it.copy(phase=Phase.STARTING,message="Starting capture…",error=false) }
            val intent=Intent(this,RecordingService::class.java).setAction(RecordingService.START)
            if(grant!=null)intent.putExtra("screenGrant",grant)
            ContextCompat.startForegroundService(this,intent)
        } catch(t:Throwable) { app.status.update { it.copy(phase=Phase.IDLE) };showError(t) }
        renderStatus()
    }

    private fun prepareStart() {
        if(CapturePermissions.runtime(app.repository?.config() ?: RecordingConfig()).any { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }) {
            toast(if(app.repository?.config()?.audioOnly==true)"Microphone permission required." else if(app.repository?.config()?.photoMode==true)"Camera permission required." else "Camera and microphone permission required.");renderStatus();return
        }
        startPreparing=true;renderStatus()
        scope.launch {
            try {
                val mode=requestedMode
                val config=withContext(Dispatchers.IO) {
                    val repo=app.repository ?: error("Storage module unavailable.")
                    repo.prepareStorage()
                    var c=repo.config().copy(cameraMode=mode)
                    val catalog=app.capture!!.create(this@MainActivity)
                    // Resolve initial preferences to actual single-camera sizes before checking a pair.
                    for(source in if(c.audioOnly)emptySet() else CapturePolicy.sources(mode)) {
                        val q=CapturePolicy.choose(catalog.qualities(source,c.copy(cameraMode=if(source==CameraSource.FRONT)CameraMode.FRONT else CameraMode.REAR)),if(source==CameraSource.FRONT)c.frontQuality else c.rearQuality)
                        c=if(source==CameraSource.FRONT)c.copy(frontQuality=q.key) else c.copy(rearQuality=q.key)
                    }
                    val support=catalog.support(c);check(support.available) { support.detail }
                    repo.saveConfig(c);c
                }
                val sources=if(config.audioOnly)setOf(CameraSource.AUDIO) else CapturePolicy.sources(config.cameraMode)
                if(app.status.value.phase==Phase.IDLE) {
                    app.status.update { it.copy(phase=Phase.STARTING,error=false,message=if(config.audioOnly)"Starting microphone…" else "Starting cameras…") }
                    ContextCompat.startForegroundService(this@MainActivity,Intent(this@MainActivity,RecordingService::class.java).setAction(RecordingService.START))
                } else {
                    app.status.update { old -> old.copy(error=false,message="Updating cameras…",cameras=(old.cameras.keys+sources).associateWith { source ->
                        val status=old.cameras[source] ?: CameraCaptureStatus()
                        status.copy(pending=status.recording!=(source in sources))
                    }) }
                    startService(Intent(this@MainActivity,RecordingService::class.java).setAction(RecordingService.UPDATE).putExtra("sources",sources.map { it.name }.toTypedArray()))
                }
                loadQualities()
            } catch(t:Throwable) {
                if(app.activeEngine==null)app.status.update { it.copy(phase=Phase.IDLE) }
                showError(t)
            } finally { startPreparing=false;renderStatus() }
        }
    }
    private fun stopRecording() {
        if(app.repository?.config()?.screenMode==true)autoPlayScreenAfter=CaptureRecovery(this).read()?.startWall ?: System.currentTimeMillis()
        app.status.update { it.copy(phase=Phase.STOPPING,message="Finishing…") }
        startService(Intent(this,RecordingService::class.java).setAction(RecordingService.STOP))
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==12) { pendingStart=false;if(grantResults.isNotEmpty() && grantResults.all { it==PackageManager.PERMISSION_GRANTED })toggleRecording(CameraMode.BOTH,valuesApplied=true) else { renderStatus();notificationControlsSettings() };return }
        if(requestCode==11) { pendingStart=false;if(grantResults.isNotEmpty() && grantResults.all { it==PackageManager.PERMISSION_GRANTED })toggleRecording(CameraMode.BOTH,valuesApplied=true) else { toast("Audio permission is required for input selection during screen capture.");renderStatus() };return }
        if(requestCode==10 && pendingStart) { pendingStart=false;prepareStart() }
    }
    private fun notificationControlsSettings() {
        NeonDialog.Builder(this).setTitle("Notification controls")
            .setMessage("Enable AppRecord notifications and its Recording channel to access Pause, Resume and Stop. Screen capture has not started.")
            .setPositiveButton("Android settings") { _,_ -> startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) }
            .setNegativeButton("Close",null).show()
    }
    private fun protectClip(clip:Clip,allowRotation:Boolean=false) {
        if(protectionBusy) { adapter?.protectionFinished(clip.id);return }
        protectionBusy=true;protectionButton?.isEnabled=false
        app.scope.launch {
            try {
                val result=withContext(Dispatchers.IO) { app.repository!!.protect(clip.id,allowRotation) }
                toast(if(result.protected)"Saved tag on" else "Saved tag off • eligible for rolling cleanup")
                protectionButton?.text=if(result.protected)"Unselect this file" else "Keep this file"
            } catch(t:Throwable) {
                if(t is SavedQuotaExceeded && !isDestroyed)savedSpaceDialog(clip,t) else showError(t)
            } finally { protectionBusy=false;adapter?.protectionFinished(clip.id);protectionButton?.isEnabled=true;app.changed();if(!isDestroyed) { refreshData() } }
        }
    }
    private fun savedSpaceDialog(clip:Clip,error:SavedQuotaExceeded) {
        NeonDialog.Builder(this).setTitle("Saved space is full")
            .setMessage("Increase the saved limit to keep all protected videos. Otherwise, saving this video will delete the oldest saved files. Videos being played or exported stay protected.")
            .setPositiveButton("Increase limit") { _,_ -> scope.launch {
                try {
                    withContext(Dispatchers.IO) { val repo=app.repository!!;val config=repo.config();val gb=1073741824L;val next=maxOf(config.savedQuotaBytes+gb,((error.requiredBytes+gb-1)/gb)*gb);repo.saveConfig(config.copy(savedQuotaBytes=next)) }
                    protectClip(clip)
                } catch(t:Throwable) { showError(t) }
            } }
            .setNeutralButton("Delete oldest saved") { _,_ -> protectClip(clip,true) }
            .setNegativeButton("Cancel",null).show()
    }
    private fun openPlayer(clip:Clip,pair:Boolean=true) = openPlayerAt(clip,pair,null)
    private fun openPlayerAt(clip:Clip,pair:Boolean,seekMs:Long?) = openPlayerAt(clip,pair,seekMs,false)
    private fun openPlayerAt(clip:Clip,pair:Boolean,seekMs:Long?,startPaused:Boolean) {
        if(clip.isPhoto) { showPhoto(clip);return }
        if(app.player==null) { toast("Player unavailable.");return }
        if(openingPlayer)return
        openingPlayer=true
        val returnScreen=if(screen in setOf("player","photo"))previousScreen else screen
        val openingOwner=root
        scope.launch {
            var pin:AutoCloseable?=null
            try {
                val other=withContext(Dispatchers.IO) {
                    if(!pair || clip.isAudio || clip.camera==CameraSource.LEGACY || clip.sessionId==null)null else app.repository!!.list()
                        .filter { !it.isAudio && !it.isPhoto && it.status==ClipStatus.READY && it.sessionId==clip.sessionId && it.startMs<clip.endMs && it.endMs>clip.startMs && it.camera!=clip.camera && it.camera!=CameraSource.LEGACY }
                        .minByOrNull { if(it.kind==clip.kind)0 else 1 }
                }
                val primary=if(clip.camera==CameraSource.FRONT && other?.camera==CameraSource.REAR)other else clip
                val secondary=if(primary.id==clip.id)other else clip
                pin=withContext(Dispatchers.IO) { app.repository!!.pin(listOfNotNull(primary,secondary).map { it.id }.toSet()) }
                if(root!==openingOwner) { pin?.close();pin=null;return@launch }
                releasePlayer();playerPin=pin;pin=null
                previousScreen=returnScreen;screen="player";currentClip=primary;companionClip=secondary;trimStart=0;trimEnd=primary.durationMs
                val page=newPage(if(clip.isAudio)"Audio playback" else "Playback","${timestamp(clip.startMs)} · ${clock(clip.durationMs)} · ${fileSize(clip.bytes)}",true)
                player=app.player!!.create(this@MainActivity) { app.diagnostics.add("Playback",it);toast(it.substringBefore("\n")) }
                val monitor=FrameLayout(this@MainActivity)
                if(primary.isAudio) {
                    audioLevelView=AudioLevelView(this@MainActivity).apply { sourceLabel="FILE  •  MONO SPECTRUM" }
                    monitor.addView(audioLevelView,FrameLayout.LayoutParams(-1,-1))
                } else monitor.addView(player!!.view,FrameLayout.LayoutParams(-1,-1))
                if(!primary.isAudio)monitor.setOnLongClickListener {
                    val options=column()
                    listOf(0,90,180,270).forEach { degrees -> options.addWide(button("$degrees°") { player?.setViewingRotation(degrees);NeonDialog.showing(this@MainActivity)?.dismiss() },44) }
                    NeonDialog.Builder(this@MainActivity).setTitle("Viewing rotation").setView(options).setNegativeButton("Close",null).show();true
                }
                page.addView(monitor,LinearLayout.LayoutParams(-1,if(primary.isAudio)dp(220) else 0,if(primary.isAudio)0f else 1f))
                val effectiveSpeed=if(primary.isAudio)playbackSpeed.coerceAtMost(8f) else playbackSpeed
                val speeds=if(primary.isAudio)PlaybackRates.values.filter { it<=8f } else PlaybackRates.values
                val speedValue=label(playbackRateText(effectiveSpeed),13f).apply { gravity=Gravity.CENTER;includeFontPadding=false }

                val speedRuler=QualityRuler(this@MainActivity).apply {
                    contentDescription="Playback speed";steps=speeds.size;selected=speeds.indexOf(effectiveSpeed)
                    changed={ index,_ -> playbackSpeed=speeds[index];player?.setSpeed(playbackSpeed);speedValue.text=playbackRateText(playbackSpeed);stateDescription=if(playbackSpeed==0f)"Paused" else "${playbackSpeed} times" }
                }
                playbackRuler=speedRuler;playbackRateLabel=speedValue

                playbackPosition=SeekBar(this@MainActivity).apply {
                    contentDescription="Playback position";max=1000
                    setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar:SeekBar,p:Int,user:Boolean) { if(user) {
                            player?.pause();pendingSeekMs=primary.durationMs*p/1000;pendingSeekUntil=SystemClock.uptimeMillis()+1500
                            player?.seekTo(pendingSeekMs!!)
                        } }
                        override fun onStartTrackingTouch(bar:SeekBar) { scrubbingPlayback=true;player?.pause() }
                        override fun onStopTrackingTouch(bar:SeekBar) { scrubbingPlayback=false;pendingSeekMs=primary.durationMs*bar.progress/1000;pendingSeekUntil=Long.MAX_VALUE;player?.seekTo(pendingSeekMs!!);updatePlaybackControls() }
                    })
                }
                page.addWide(playbackPosition!!,28)
                page.addWide(mediaControls(primary))
                page.addWide(speedValue,22)
                page.addWide(speedRuler,42)
                page.addGap(10);addExportStatus(page)
                player!!.open(primary,secondary)
                seekMs?.let { player!!.seekTo(it.coerceIn(0,primary.durationMs)) }
                player!!.setSpeed(effectiveSpeed)
                if(startPaused)player!!.pause()
            } catch(t:Throwable) { pin?.close();releasePlayer();showError(t);showHome() }
            finally { openingPlayer=false }
        }
    }
    private fun restartOrPlay() {
        val p=player ?: return
        val duration=p.durationMs().takeIf { it>0 } ?: currentClip?.durationMs ?: 0
        val selected=pendingSeekMs
        if(selected!=null && SystemClock.uptimeMillis()<pendingSeekUntil) { p.seekTo(selected);pendingSeekMs=null }
        else if(duration>0 && p.positionMs()>=duration) { p.seekTo(0);pendingSeekMs=0;pendingSeekUntil=SystemClock.uptimeMillis()+1500 }
        p.play()
    }
    private fun mediaControls(clip:Clip):View = GlowPanel(this).apply {
        tag="shared-playback-controls"
        setPadding(dp(10),dp(10),dp(10),dp(10))
        addWide(mediaNavigation(clip));if(clip.isAudio)addWide(com.duorecord.components.GlossyControlRail(this@MainActivity),8)
        addGap(12);addWide(mediaFileActions(clip));if(clip.isAudio)addWide(com.duorecord.components.GlossyControlRail(this@MainActivity),8)
    }
    private var selectingAllArchive=false
    private fun toggleAllArchive() {
        val target=adapter ?: return
        if(target.allSelected) { target.toggleAll();return }
        if(selectingAllArchive)return
        selectingAllArchive=true;selectAllCheck?.isEnabled=false
        scope.launch {
            try {
                val files=withContext(Dispatchers.IO) { val repo=app.repository!!;repo.reconcile(app.status.value.phase!=Phase.IDLE);repo.list() }
                if(adapter===target) { allMedia=files;target.allFiles=files;target.selectAll() }
            } catch(t:Throwable) { showError(t) }
            finally { selectingAllArchive=false;selectAllCheck?.isEnabled=true }
        }
    }
    private fun mediaNavigation(clip:Clip):View {
        val row=SquareControlRow(this).apply { gravity=Gravity.CENTER;contentDescription="Media navigation" }
        val controls=mutableListOf<Pair<SignButton,Pair<Boolean,Boolean>>>()
        fun add(icon:HeaderIcon,same:Boolean,next:Boolean) {
            val control=iconButton(icon) {}.apply {
                squareFace=true;semanticTransport=true;isEnabled=false
                contentDescription="${if(next)"Next" else "Previous"} ${if(same)"same type and camera" else "file, all types"}"
            }
            controls+=control to (same to next);row.addView(control,LinearLayout.LayoutParams(0,dp(48),1f))
        }
        add(HeaderIcon.PREVIOUS_ALL,false,false);add(HeaderIcon.PREVIOUS,true,false)
        val play=iconButton(HeaderIcon.PLAY) { if(player==null && !clip.isPhoto)openPlayerAt(clip,false,pendingSeekMs) else if(player?.isPlaying()==true)player?.pause() else restartOrPlay();updatePlaybackControls() }.apply { squareFace=true;semanticTransport=true;contentDescription="Play playback";isEnabled=!clip.isPhoto }
        playbackToggle=play;row.addView(play,LinearLayout.LayoutParams(0,dp(48),1f))
        row.addView(iconButton(HeaderIcon.STOP) { player?.pause();player?.seekTo(0);pendingSeekMs=0;pendingSeekUntil=SystemClock.uptimeMillis()+1500;updatePlaybackControls() }.apply { squareFace=true;semanticTransport=true;contentDescription="Stop playback";isEnabled=!clip.isPhoto },LinearLayout.LayoutParams(0,dp(48),1f))

        add(HeaderIcon.NEXT,true,true);add(HeaderIcon.NEXT_ALL,false,true)
        val owner=root
        scope.launch {
            try {
                val files=withContext(Dispatchers.IO) { app.repository!!.list() }
                if(root!==owner)return@launch
                for((button,direction) in controls) {
                    val pair=PlaybackNavigation.neighbors(files,clip,direction.first)
                    val target=if(direction.second)pair.second else pair.first
                    button.isEnabled=target!=null;button.setOnClickListener { target?.let { openPlayer(it,false) } }
                }
            } catch(t:Throwable) { if(t !is CancellationException && root===owner)showError(t) }
        }
        if(clip.isPhoto)return row
        return row
    }

    private fun mediaFileActions(clip:Clip):View = SquareControlRow(this).apply {
        gravity=Gravity.CENTER;contentDescription="Media file actions"
        val actions=listOf(
            iconButton(HeaderIcon.RECORDINGS) { showArchive() }.apply { contentDescription="Open recorded files" },
            deleteMediaButton(clip),
            iconButton(HeaderIcon.TRIM) { trimDialog(clip) }.apply { contentDescription="Trim video";isEnabled=!clip.isPhoto && !clip.isAudio && app.editor!=null },
            iconButton(HeaderIcon.FOLDER) { openClipFolder(clip) }.apply { contentDescription="Open this file folder" },
            iconButton(HeaderIcon.SHARE) { shareFiles(listOf(clip)) }.apply { contentDescription="Share this file" },
            iconButton(HeaderIcon.COPY) { copyFiles(listOf(clip)) }.apply { contentDescription="Copy this file" },
            iconButton(HeaderIcon.NORMAL_SPEED) { playbackSpeed=1f;player?.setSpeed(1f);playbackRuler?.selected=PlaybackRates.values.indexOf(1f);playbackRateLabel?.text="1×";updatePlaybackControls() }.apply { isEnabled=!clip.isPhoto }
        )
        actions.forEach { addView(it.apply { squareFace=true;semanticTransport=true },LinearLayout.LayoutParams(0,dp(48),1f)) }
    }
    private fun openClipFolder(clip:Clip) {
        val base=when { clip.isAudio -> "Music/Record";clip.isPhoto -> "Pictures/Record";else -> "Movies/Record" }
        val path=base+if(clip.protected)"/Selected" else ""
        if(!RecordingFolderBrowser.open(this,path))NeonDialog.Builder(this).setTitle("Recording folder").setMessage("Folder: $path. No compatible file manager installed.").setNegativeButton("Close",null).show()
    }
    private fun shareFiles(files:List<Clip>) {
        val ready=files.filter { it.status==ClipStatus.READY }.distinctBy { it.id }
        if(ready.isEmpty()) { toast("Select completed files first.");return }
        try {
            fun mime(c:Clip)=if(c.isPhoto)"image/jpeg" else if(c.isAudio)"audio/aac" else "video/mp4"
            val uris=ArrayList(ready.map { android.net.Uri.parse(it.uri) })
            val data=android.content.ClipData.newRawUri(ready.first().name,uris.first())
            uris.drop(1).forEach { data.addItem(android.content.ClipData.Item(it)) }
            val intent=Intent(if(ready.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType(ready.map(::mime).distinct().singleOrNull() ?: "*/*").apply {
                if(ready.size==1)putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                clipData=data;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent,"Share files"))
        } catch(t:Throwable) { showError(t) }
    }
    private fun copyFiles(files:List<Clip>) {
        val ready=files.filter { it.status==ClipStatus.READY }.distinctBy { it.id }
        if(ready.isEmpty()) { toast("Select completed files first.");return }
        try {
            val data=android.content.ClipData.newRawUri("${ready.size} recording files",android.net.Uri.parse(ready.first().uri))
            ready.drop(1).forEach { data.addItem(android.content.ClipData.Item(android.net.Uri.parse(it.uri))) }
            (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(data)
            toast("${ready.size} files copied. Paste requires a file manager that supports file URIs.")
        } catch(t:Throwable) { showError(t) }
    }
    private fun updatePlaybackControls() {
        if(screen!="player")return
        val playing=player?.isPlaying()==true
        playbackToggle?.apply { icon=if(playing)HeaderIcon.PAUSE else HeaderIcon.PLAY;contentDescription=if(playing)"Pause playback" else "Play playback";isSelected=playing;isEnabled=true;invalidate() }
        playbackPause?.apply { isSelected=!playing;isEnabled=true;invalidate() }
        if(!scrubbingPlayback) {
            val position=if(pendingSeekMs!=null && SystemClock.uptimeMillis()<pendingSeekUntil)pendingSeekMs!! else { pendingSeekMs=null;player?.positionMs() ?: 0L }
            playbackPosition?.progress=(position*1000/(currentClip?.durationMs ?: 1L).coerceAtLeast(1)).toInt().coerceIn(0,1000)
        }
        if(currentClip?.isAudio==true)audioLevelView?.meter=player?.audioMeter() ?: AudioMeter()
    }
    private fun deleteMediaButton(clip:Clip)=iconButton(HeaderIcon.DELETE) {
        NeonDialog.Builder(this).setTitle("Delete this file?").setMessage("Delete ${clip.name}? This permanently removes the file.")
            .setPositiveButton("Delete") { _,_ -> scope.launch {
                try {
                    photoLoad?.cancelAndJoin();releasePlayer()
                    withContext(Dispatchers.IO) { app.repository!!.delete(setOf(clip.id)) }
                    app.changed();showArchive()
                } catch(t:Throwable) { showArchive();showError(t) }
            } }.setNegativeButton("Cancel",null).show()
    }.apply { squareFace=true;semanticTransport=true;contentDescription="Delete this file" }
    private fun playbackRateText(rate:Float):String {
        val value=String.format(Locale.US,"%.2f",rate).trimEnd('0').trimEnd('.')
        return "$value×"+if(rate==0f)" · paused" else if(rate>8f && currentClip?.isAudio!=true)" · audio muted" else ""
    }
    private fun syncPlaybackRate() {
        val current=player?.speed() ?: return
        if(screen!="player" || current==(if(currentClip?.isAudio==true)playbackSpeed.coerceAtMost(8f) else playbackSpeed))return
        playbackSpeed=current
        val rates=if(currentClip?.isAudio==true)PlaybackRates.values.filter { it<=8f } else PlaybackRates.values
        playbackRuler?.selected=rates.indexOf(current).coerceAtLeast(0)
        playbackRateLabel?.text=playbackRateText(current)
    }
    private fun openRecordingFolder() {
        if(!RecordingFolderBrowser.open(this,app.repository?.storagePath() ?: "Movies/Record"))NeonDialog.Builder(this).setTitle("Recording folder")
            .setMessage("No installed file manager can open this folder. Videos: Movies/Record. Photos: Pictures/Record. Audio: Music/Record. Kept files: Selected inside each folder.")
            .setNegativeButton("Back",null).show()
    }
    private fun releasePlayer() { photoLoad?.cancel();photoLoad=null;player?.release();player=null;playerPin?.close();playerPin=null;currentClip=null;companionClip=null }
    private fun trimDialog(clip:Clip) {
        if(app.export.value.busy) { toast("Export already running.");return }
        val owner=root;val restorePosition=player?.positionMs() ?: 0L;val restorePair=companionClip!=null
        player?.release();player=null // Do not keep a second high-resolution decoder alive.
        val body=column();val monitor=FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK);contentDescription="Trim preview" }
        monitor.addView(label("Preview selection to play",14f,Palette.muted).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
        body.addWide(monitor,180)
        val rangeLabel=label("${clock(0)} – ${clock(clip.durationMs)}",14f)
        val timeline=TrimTimeline(this,clip.durationMs)
        body.addWide(rangeLabel,28);body.addWide(timeline,76)
        var trimClosed=false;var saving=false;var previewing=false
        var previewPlayer:PlayerComponent?=null;var previewJob:Job?=null
        trimStart=0;trimEnd=clip.durationMs
        val thumbnailJob=scope.launch {
            val frames=withContext(Dispatchers.IO) {
                TrimThumbnails.load(this@MainActivity,android.net.Uri.parse(clip.uri),clip.durationMs,
                    (getSystemService(ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice,
                    { !trimClosed && !isDestroyed && isActive },{ app.diagnostics.add("Trim",it) })
            }
            if(!trimClosed && !isDestroyed)timeline.frames=frames else frames.forEach { it.recycle() }
        }
        body.addWide(button("Preview selection") {
            previewJob?.cancel()
            previewJob=scope.launch {
                try {
                    thumbnailJob.join() // Thumbnail retriever finishes before preview decoding.
                    if(trimClosed)return@launch
                    if(previewPlayer==null) {
                        previewPlayer=app.player!!.create(this@MainActivity) { app.diagnostics.add("Trim preview",it);toast(it.substringBefore("\n")) }
                        monitor.removeAllViews();monitor.addView(previewPlayer!!.view,FrameLayout.LayoutParams(-1,-1))
                        previewPlayer!!.open(clip)
                    }
                    previewPlayer!!.seekTo(trimStart);previewPlayer!!.play();previewing=true
                } catch(t:Throwable) { if(t is CancellationException)throw t;app.diagnostics.add("Trim preview",t.stackTraceToString());showError(t) }
            }
        },48)
        val previewTick=object:Runnable { override fun run() {
            if(trimClosed)return
            if(previewing && (previewPlayer?.positionMs() ?: 0)>=trimEnd) { previewPlayer?.pause();previewing=false }
            mainHandler.postDelayed(this,100)
        } };mainHandler.post(previewTick)
        timeline.changed={ start,end -> val point=if(start!=trimStart)start else end;trimStart=start;trimEnd=end;rangeLabel.text="${clock(start)} – ${clock(end)}";previewPlayer?.pause();previewPlayer?.seekTo(point);previewing=false }
        val dialog=NeonDialog.Builder(this).setTitle("Trim video").setView(body).setPositiveButton("Save",null).setNegativeButton("Back",null).create()
        activeTrimDialog=dialog
        dialog.setOnDismissListener {
            activeTrimDialog=null;trimClosed=true;thumbnailJob.cancel();previewJob?.cancel();mainHandler.removeCallbacks(previewTick)
            previewPlayer?.release();previewPlayer=null;timeline.frames.forEach { if(!it.isRecycled)it.recycle() };timeline.frames=emptyList()
            if(!saving && activityVisible && !isDestroyed && root===owner && screen=="player")scope.launch {
                thumbnailJob.join()
                if(activityVisible && root===owner && screen=="player")openPlayerAt(clip,restorePair,restorePosition,true)
            }
        }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(saving)return@setOnClickListener
            try {
                require(trimEnd>trimStart) { "Choose a non-empty range." }
                saving=true;dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                scope.launch {
                    try {
                        thumbnailJob.cancelAndJoin();previewJob?.cancelAndJoin();previewPlayer?.release();previewPlayer=null
                        startExport(Intent(this@MainActivity,ExportService::class.java).putExtra("id",clip.id).putExtra("from",trimStart).putExtra("to",trimEnd))
                        dialog.dismiss();showArchive()
                    } catch(t:Throwable) {
                        saving=false;dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                        if(t is CancellationException)throw t;app.diagnostics.add("Trim",t.stackTraceToString());showError(t)
                    }
                }
            } catch(t:Throwable) { saving=false;app.diagnostics.add("Trim",t.stackTraceToString());showError(t) }
        } };dialog.show()
    }
    private fun absoluteExportDialog() {
        if(app.export.value.busy) { toast("Export already running.");return }
        scope.launch {
            val clips=withContext(Dispatchers.IO) { app.repository?.list().orEmpty().filter { !it.isAudio && !it.isPhoto && it.status==ClipStatus.READY } }
            if(clips.isEmpty()) { toast("No completed videos yet.");return@launch }
            val latest=clips.maxBy { it.endMs }
            val zone=ZoneId.systemDefault()
            val body=column()
            body.addWide(label("Local time: YYYY-MM-DD HH:MM:SS",14f))
            val begin=java.time.Instant.ofEpochMilli(latest.startMs).atZone(zone).toLocalDateTime().format(dateFormat)
            val finish=java.time.Instant.ofEpochMilli(latest.endMs).atZone(zone).toLocalDateTime().format(dateFormat)
            val sources=clips.map { it.camera }.distinct().sortedBy { it.ordinal }
            val choices=sources.map { it.name }.toMutableList().apply { if(CameraSource.REAR in sources && CameraSource.FRONT in sources)add("BOTH") }
            val camera=NeonSpinner(this@MainActivity)
            body.addWide(spinnerRow("Camera",camera,choices,if("BOTH" in choices)choices.lastIndex else 0),48)
            val from=timeInput(begin);val to=timeInput(finish)
            body.addGap();body.addWide(inputRow("From",from),48);body.addGap();body.addWide(inputRow("To",to),48)
            val dialog=NeonDialog.Builder(this@MainActivity).setTitle("Export by time").setView(body).setPositiveButton("Export",null).setNegativeButton("Back",null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val start=LocalDateTime.parse(normalDigits(from.text.toString()).trim(),dateFormat).atZone(zone).toInstant().toEpochMilli()
                    val end=LocalDateTime.parse(normalDigits(to.text.toString()).trim(),dateFormat).atZone(zone).toInstant().toEpochMilli()
                    val choice=choices[camera.selectedItemPosition]
                    val requested=if(choice=="BOTH")listOf(CameraSource.REAR,CameraSource.FRONT) else listOf(CameraSource.valueOf(choice))
                    requested.forEach { source -> TimelinePlanner.plan(clips.filter { it.camera==source },start,end) }
                    startExport(Intent(this@MainActivity,ExportService::class.java).putExtra("start",start).putExtra("end",end).putExtra("camera",choice));dialog.dismiss()
                } catch(t:Throwable) { showError(t) }
            } };dialog.show()
        }
    }
    private fun startExport(intent:Intent) {
        intent.action=ExportService.START
        ContextCompat.startForegroundService(this,intent)
    }
    private fun pageGuide() {
        val title=when(screen) { "archive" -> "Recordings guide";"schedule" -> "Schedule guide";"capture_timing" -> "Capture timing guide";"health","battery_metrics" -> PageGuides.healthTitle(healthDetailIndex);else -> "Guide" }
        val text=when(screen) { "archive" -> PageGuides.recordings;"player" -> if(currentClip?.isAudio==true)PageGuides.audioPlayback else PageGuides.playback;"photo" -> PageGuides.photo;"schedule" -> PageGuides.schedule;"capture_timing" -> PageGuides.captureTiming;"health","battery_metrics" -> PageGuides.health(healthDetailIndex);"details" -> PageGuides.details;"tools" -> PageGuides.tools;else -> null }
        if(text==null)guideDialog() else NeonDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("Back",null).show()
    }
    private fun guideDialog() {
        val body=label(PageGuides.captureMode+"\n\n"+PageGuides.captureTiming+"\n\nSTORAGE & RECOVERY\nRecordings lists saved files; its Guide explains playback, keeping, deleting and exports. Settings contains limits and refresh options. Info & tools contains interruption history, recording details and About. Android controls microphone/camera availability; automatic recovery preserves the original deadline. A user Pause remains paused until Resume. Required privacy indicators remain visible.",17f).apply { setPadding(dp(16),dp(12),dp(16),dp(12)) }
        NeonDialog.Builder(this).setTitle("Guide").setView(body).setNegativeButton("Back",null).show()
    }
    private fun showInfoTools() {
        releasePlayer();screen="tools"
        val page=newPage("Info & tools",back=true)
        val body=column();page.addView(ViewportScrollView(this).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        val panel=GlowPanel(this,"Information").apply { setPadding(dp(12),dp(12),dp(12),dp(12)) }
        panel.addContent(button("Recording details") { showRecordingDetails() },48);panel.addGap(8)
        panel.addContent(button("Interruption history") { diagnosticsDialog() },48);panel.addGap(8)
        panel.addContent(button("About") { aboutDialog() },48);body.addWide(panel);body.addGap(8)
        val runtime=getSharedPreferences("runtime",0)
        val stop=if(runtime.getBoolean("unfinished",false) && app.status.value.phase==Phase.IDLE)"Previous session interrupted by Android or a crash; reopen recordings to recover finalized files." else runtime.getString("lastStop","No previous stop recorded.")!!
        val last=GlowPanel(this,"Last stop").apply { setPadding(dp(12),dp(12),dp(12),dp(12));addContent(label(stop,14f)) };body.addWide(last)
    }
    private fun aboutDialog() {
        val info=packageManager.getPackageInfo(packageName,0)
        val body=column()
        fun section(title:String,rows:List<Pair<String,String>>) {
            val box=GlowPanel(this,title).apply { setPadding(dp(12),dp(12),dp(12),dp(12)) }
            rows.forEachIndexed { i,(key,value) ->
                if(i>0)box.addGap(8)
                box.addContent(label("$key: $value",15f).apply { setTextIsSelectable(true) })
            };body.addWide(box);body.addGap(8)
        }
        section("Application",listOf("Name" to applicationInfo.loadLabel(packageManager).toString(),"Version" to info.versionName.orEmpty(),"Build" to info.longVersionCode.toString()))
        section("Development",listOf("Programming language" to "Kotlin","Android SDK" to "35","Minimum Android" to "11 (API 30)","Media APIs" to "Camera2 / MediaCodec / OpenGL ES","Build tools" to "Gradle 8.9 / AGP 8.7.3"))
        section("Author",listOf("Developer" to "Morteza","Contact" to "rassoly@gmail.com","Location" to "New York","Release" to "2026-10"))
        NeonDialog.Builder(this).setTitle("About").setView(body).setNegativeButton("Back",null).show()
    }
    private fun diagnosticsDialog() {
        val body=column()
        val records=app.diagnostics.entries()
        if(records.isEmpty())body.addWide(label("No automatic interruptions recorded."))
        records.forEach { record ->
            val panel=GlowPanel(this);panel.addContent(label(record,13f));body.addWide(panel);body.addGap(8)
        }
        NeonDialog.Builder(this).setTitle("Interruption history · latest 200")
            .setView(body).setPositiveButton("Copy report") { _,_ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("AppRecord interruptions",records.joinToString("\n\n")))
                toast("Report copied.")
            }.setNegativeButton("Back",null).show()
    }
    private fun settingsDialog() {
        scope.launch {
            val config=withContext(Dispatchers.IO) { app.repository?.config() } ?: return@launch
            val captureLocked=app.status.value.phase!=Phase.IDLE || startPreparing || pendingStart
            val body=column()
            fun field(title:String,value:String,decimal:Boolean=false):EditText {
                val input=timeInput(value).apply {
                    inputType=android.text.InputType.TYPE_CLASS_NUMBER or (if(decimal)android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
                    contentDescription=title
                    filters=arrayOf(android.text.InputFilter.LengthFilter(14));isEnabled=!captureLocked;alpha=if(captureLocked).5f else 1f
                }
                body.addWide(inputRow(title,input),48);body.addGap(12);return input
            }
            fun gb(bytes:Long)=java.math.BigDecimal.valueOf(bytes).divide(java.math.BigDecimal.valueOf(1073741824L),6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            val unitPrefs=getSharedPreferences("display_units",0)
            val segmentUnit=NeonSpinner(this@MainActivity).apply { contentDescription="File duration unit";isEnabled=!captureLocked }
            val unit=unitPrefs.getInt("segment",1).coerceIn(0,2)
            val divisor=listOf(1,60,3600)[unit]
            val value=java.math.BigDecimal(config.segmentSeconds).divide(java.math.BigDecimal(divisor),6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            val length=timeInput(value).apply {
                inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                contentDescription="File duration";filters=arrayOf(android.text.InputFilter.LengthFilter(14));isEnabled=!captureLocked;alpha=if(captureLocked).5f else 1f
            }
            body.addWide(durationInputRow(length,segmentUnit,unit),48);body.addGap(12)
            var previousUnit=unit
            segmentUnit.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
              override fun onNothingSelected(parent:AdapterView<*>?)=Unit
              override fun onItemSelected(parent:AdapterView<*>?,view:View?,selected:Int,id:Long) {
                val number=normalDigits(length.text.toString()).toBigDecimalOrNull()
                if(number!=null && selected!=previousUnit) {
                    length.setText(number.multiply(java.math.BigDecimal(listOf(1,60,3600)[previousUnit]))
                        .divide(java.math.BigDecimal(listOf(1,60,3600)[selected]),6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                }
                previousUnit=selected
              }
            }
            val quota=field("Record · GB",gb(config.quotaBytes),true)
            val savedQuota=field("Saved · GB",gb(config.savedQuotaBytes),true)
            val reserve=field("Keep free · GB",gb(config.reserveBytes),true)
            val fps=field("Rate · fps",config.fps.toString())
            val battery=field("Battery · %",config.stopBatteryPercent.toString())
            val maxMinutes=field("Stop · min · 0=∞",config.maxRecordingMinutes.toString())
            val archiveRefresh=field("Refresh · sec",config.archiveRefreshSeconds.toString()).apply { isEnabled=true;alpha=1f }
            val archiveCount=field("Initial files",config.archiveInitialLimit.toString()).apply { isEnabled=true;alpha=1f }
            val hevc=NeonSwitch(this@MainActivity).apply { text="H.265 · Smaller files";isChecked=config.preferHevc;isEnabled=!captureLocked }
            val continueMic=NeonSwitch(this@MainActivity).apply { text="Continue video when mic is silenced";isChecked=config.continueVideoWithoutMic;isEnabled=!captureLocked;contentDescription=text }
            val floatingControls=NeonSwitch(this@MainActivity).apply { text="Screen floating controls";isChecked=ScreenControls.floating(this@MainActivity);isEnabled=!captureLocked;contentDescription="Screen floating controls. Off uses notification Pause and Stop, with no floating capture mask.";setOnCheckedChangeListener { _,checked -> ScreenControls.setFloating(this@MainActivity,checked) } }
            val simple=NeonSwitch(this@MainActivity).apply { text="Minimal appearance";isChecked=config.simpleTheme }
            body.addWide(button("Recording shortcut") { shortcutDialog() },48);body.addGap()
            body.addWide(button("Background recording") {
                NeonDialog.Builder(this@MainActivity).setTitle("Background recording")
                    .setMessage("Recording runs in a camera/microphone foreground service with a CPU wake lock. Minimizing the app does not request Stop. On phones that restrict background activity, set this app's battery mode to Unrestricted and remove it from sleeping apps. Android, permission revocation or another camera app can still interrupt recording. See Interruption history after reopening the app.")
                    .setPositiveButton("App settings") { _,_ -> startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) }
                    .setNegativeButton("Back",null).show()
            },48);body.addGap()
            body.addWide(floatingControls,48);body.addGap();body.addWide(hevc,48);body.addGap();body.addWide(continueMic,48);body.addGap();body.addWide(simple,48);body.addGap()
            if(captureLocked) { body.addWide(label("Recording settings are locked until capture stops. Appearance can still be changed.",17f));body.addGap() }
            val dialog=NeonDialog.Builder(this@MainActivity).setTitle("Settings").setView(body)
                .setPositiveButton("Save",null).setNegativeButton("Back",null).setNeutralButton("Reset defaults",null).create()
            dialog.addGuide { NeonDialog.Builder(this@MainActivity).setTitle("Settings guide").setMessage(PageGuides.settings).setNegativeButton("Back",null).show() }
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).apply { isEnabled=!captureLocked;setOnClickListener {
                    scope.launch { try { withContext(Dispatchers.IO) { app.repository!!.saveConfig(RecordingConfig()) };RecordingAutomation.cancel(this@MainActivity);getSharedPreferences("shortcut",0).edit().clear().apply();unitPrefs.edit().putInt("segment",1).apply();app.lightForNextRecording=false;app.screenLightWanted=false;dialog.dismiss();showHome() } catch(t:Throwable) { showError(t) } }
                } }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                fun number(input:EditText,name:String):Int=normalDigits(input.text.toString()).trim().toIntOrNull() ?: throw IllegalArgumentException("Enter a whole number for $name.")
                fun bytes(input:EditText,name:String):Long {
                    val n=normalDigits(input.text.toString()).trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a number for $name.")
                    require(n>java.math.BigDecimal.ZERO && n<=java.math.BigDecimal(10240)) { "$name must be greater than zero and at most 10240 GB." }
                    return n.multiply(java.math.BigDecimal.valueOf(1073741824L)).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()
                }
                try {
                    val next=if(captureLocked)config.copy(simpleTheme=simple.isChecked,archiveRefreshSeconds=number(archiveRefresh,"archive refresh"),archiveInitialLimit=number(archiveCount,"initial files")) else config.copy(segmentSeconds=ScheduleDefaults.segmentSeconds(normalDigits(length.text.toString()),segmentUnit.selectedItemPosition),quotaBytes=bytes(quota,"recording limit"),savedQuotaBytes=bytes(savedQuota,"saved limit"),reserveBytes=bytes(reserve,"free-space reserve"),fps=number(fps,"frame rate"),stopBatteryPercent=number(battery,"battery threshold"),maxRecordingMinutes=number(maxMinutes,"recording duration"),archiveRefreshSeconds=number(archiveRefresh,"archive refresh"),archiveInitialLimit=number(archiveCount,"initial files"),preferHevc=hevc.isChecked,continueVideoWithoutMic=continueMic.isChecked,simpleTheme=simple.isChecked)
                    CapturePolicy.validate(next)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val catalog=app.capture?.create(this@MainActivity)
                                if(catalog!=null && !next.audioOnly && next.fps!=config.fps) {
                                    val any=listOf(CameraSource.FRONT,CameraSource.REAR).any { source -> runCatching { catalog.qualities(source,next.copy(cameraMode=if(source==CameraSource.FRONT)CameraMode.FRONT else CameraMode.REAR)).isNotEmpty() }.getOrDefault(false) }
                                    require(any) { "Neither camera supports this frame rate. Enter a lower value." }
                                }
                                val saved=if(captureLocked)app.repository!!.config().copy(simpleTheme=next.simpleTheme,archiveRefreshSeconds=next.archiveRefreshSeconds,archiveInitialLimit=next.archiveInitialLimit) else next
                                app.repository!!.saveConfig(saved);if(!captureLocked)app.repository!!.enforceRetention()
                            }
                            if(!captureLocked)unitPrefs.edit().putInt("segment",segmentUnit.selectedItemPosition).apply()
                            dialog.dismiss();showHome()
                        } catch(t:Throwable) { dialog.showError(t.message ?: "Could not save settings.");dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true }
                    }
                } catch(t:Throwable) { dialog.showError(t.message ?: "Check the entered numbers.") }
            } }
            dialog.show()
        }
    }
    private fun addScheduleSummary(parent:LinearLayout) {
        val panel=GlowPanel(this).apply { setPadding(dp(18),dp(18),dp(18),dp(18)) }
        val value=label("",15f,Palette.mint).apply { contentDescription="Saved schedule" }
        panel.addWide(value);parent.addWide(panel)
        scheduleSummary=value;scheduleSummaryPanel=panel;updateScheduleSummary()
    }
    private fun updateScheduleSummary() {
        val p=getSharedPreferences("automation",0);val start=p.getLong("start",0);val end=p.getLong("end",0)
        val visible=start>0 && end>System.currentTimeMillis() && (p.getBoolean("armed",false) || p.getLong("activeEnd",0)==end)
        scheduleSummaryPanel?.visibility=if(visible)View.VISIBLE else View.GONE
        if(visible) {
            val format=java.text.SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.US)
            scheduleSummary?.text=if(end==Long.MAX_VALUE)"Start: ${format.format(java.util.Date(start))}\nEnd: Manual stop\nDuration: Unlimited" else "Start: ${format.format(java.util.Date(start))}\nEnd: ${format.format(java.util.Date(end))}\nDuration: ${ScheduleDefaults.durationText(((end-start)/60000).toInt())}"
        }
    }
    private fun scheduleDateRow(name:String,input:EditText,fallback:LocalDateTime):LinearLayout {
        val format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.US)
        fun value()=runCatching { LocalDateTime.parse(input.text.toString(),format) }.getOrDefault(fallback)
        val box=column();val dateRow=inputRow(name,input)
        dateRow.getChildAt(0).apply {
            isFocusable=true;isClickable=true;contentDescription="$name · now + ${if(name=="Start")3 else 10} minutes"
            setOnClickListener { input.setText(LocalDateTime.now().plusMinutes(if(name=="Start")3 else 10).withSecond(0).withNano(0).format(format)) }
            accessibilityDelegate=object:View.AccessibilityDelegate() { override fun onInitializeAccessibilityNodeInfo(host:View,info:android.view.accessibility.AccessibilityNodeInfo) { super.onInitializeAccessibilityNodeInfo(host,info);info.className=Button::class.java.name } }
        }
        box.addWide(dateRow,48);box.addGap(4)
        val actions=LinearLayout(this)
        actions.addView(button("Date") {
            val initial=value()
            val month=label("",15f,Palette.mint,true).apply { gravity=Gravity.CENTER;contentDescription="Selected month number and name" }
            fun monthText(year:Int,m:Int) { month.text="${m+1} · ${java.time.Month.of(m+1).getDisplayName(java.time.format.TextStyle.FULL,Locale.US)} · $year" }
            val picker=DatePicker(this).apply { init(initial.year,initial.monthValue-1,initial.dayOfMonth) { _,year,m,_ -> monthText(year,m) } }
            monthText(initial.year,initial.monthValue-1)
            val content=column().apply { addWide(month,32);addWide(picker) }
            NeonDialog.Builder(this).setTitle("$name date").setView(content).setPositiveButton("Set date") { _,_ ->
                input.setText(value().withYear(picker.year).withMonth(1).withDayOfMonth(1).withMonth(picker.month+1).withDayOfMonth(picker.dayOfMonth).format(format))
            }.setNegativeButton("Cancel",null).show()
        }.apply { contentDescription="Choose $name date" },LinearLayout.LayoutParams(0,dp(48),1f))
        actions.addView(button("Time") {
            val initial=value();val picker=TimePicker(this).apply { setIs24HourView(true);hour=initial.hour;minute=initial.minute }
            NeonDialog.Builder(this).setTitle("$name time").setView(picker).setPositiveButton("Set time") { _,_ ->
                input.setText(value().withHour(picker.hour).withMinute(picker.minute).format(format))
            }.setNegativeButton("Cancel",null).show()
        }.apply { contentDescription="Choose $name time" },LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(8) })
        box.addWide(actions);return box
    }
    private fun checkSchedule() {
        val p=getSharedPreferences("automation",0)
        val now=System.currentTimeMillis();val start=p.getLong("start",0);val end=p.getLong("end",0)
        if(!p.getBoolean("armed",false))return
        if(now>=end) { p.edit().putBoolean("armed",false).apply();return }
        if(now>=start && app.status.value.phase==Phase.IDLE && !startPreparing && !pendingStart && modeSaving?.isActive!=true && !captureModeEditing) {
            p.edit().putBoolean("armed",false).putLong("activeEnd",if(end==Long.MAX_VALUE)0 else end).apply()
            val mode=runCatching { CameraMode.valueOf(p.getString("mode","BOTH")!!) }.getOrDefault(CameraMode.BOTH)
            val current=app.repository!!.config()
            val config=current.copy(screenMode=p.getBoolean("screenMode",false),mixedMode=p.getBoolean("mixedMode",false),screenAudio=p.getBoolean("screenAudio",current.screenAudio),screenInternalAudio=p.getBoolean("screenInternal",current.screenInternalAudio),mixedFront=p.getBoolean("mixedFront",current.mixedFront),mixedRear=p.getBoolean("mixedRear",current.mixedRear),mixedScreen=p.getBoolean("mixedScreen",current.mixedScreen),mixedBackground=runCatching { CameraSource.valueOf(p.getString("background",current.mixedBackground.name)!!) }.getOrDefault(current.mixedBackground),mixedLayout=p.getString("layout",current.mixedLayout)!!,maxRecordingMinutes=p.getInt("minutes",current.maxRecordingMinutes),fps=p.getInt("fps",current.fps),continueVideoWithoutMic=p.getBoolean("continueVideoWithoutMic",current.continueVideoWithoutMic),audioOnly=p.getBoolean("audioOnly",false),cameraMode=mode,frontQuality=p.getString("front", "480x640")!!,rearQuality=p.getString("rear","480x640")!!,
                photoMode=p.getBoolean("photos",current.photoMode),photoIntervalSeconds=p.getInt("interval",current.photoIntervalSeconds))
            app.lightForNextRecording=p.getBoolean("light",app.lightForNextRecording);app.screenLightWanted=p.getBoolean("screenLight",app.screenLightWanted)
            app.repository!!.saveConfig(config);showHome();requestedMode=mode;toggleRecording(mode,true)
        }
    }
    private fun showSchedule() {
        releasePlayer();screen="schedule"
        val page=newPage("Schedule",back=true)
        val body=column()
        page.addView(ViewportScrollView(this).apply { addView(body) },LinearLayout.LayoutParams(-1,0,1f))
        val format=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm",Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)
        val currentConfig=app.repository!!.config().copy(screenMode=false,mixedMode=false);val prefs=getSharedPreferences("automation",0)
        val existing=prefs.getBoolean("armed",false) && prefs.getLong("start",0)>System.currentTimeMillis() && prefs.getLong("end",0)!=Long.MAX_VALUE && !prefs.getBoolean("screenMode",false) && !prefs.getBoolean("mixedMode",false)
        var cfg=if(existing)currentConfig.copy(audioOnly=prefs.getBoolean("audioOnly",false),photoMode=prefs.getBoolean("photos",false),photoIntervalSeconds=prefs.getInt("interval",10)) else currentConfig
        addScheduleSummary(body)
        val captureTypes=LinearLayout(this)
        val captureButtons=listOf("Video","Photos","Audio").map { button(it) {}.apply { toggleAppearance=true } }
        captureButtons.forEach { captureTypes.addView(it,LinearLayout.LayoutParams(0,dp(48),1f)) }
        body.addWide(captureTypes,48);body.addGap(8)
        val interval=timeInput(cfg.photoIntervalSeconds.toString()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;contentDescription="Scheduled photo interval in seconds" }
        val intervalRow=inputRow("Photo · sec",interval)
        fun local(ms:Long)=java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0)
        val startDefault=if(existing)local(prefs.getLong("start",0)) else ScheduleDefaults.nextHour(LocalDateTime.now())
        val endDefault=if(existing)local(prefs.getLong("end",0)) else startDefault.plusMinutes((cfg.maxRecordingMinutes.takeIf { it>0 } ?: 120).coerceAtMost(ScheduleDefaults.maxMinutes).toLong())
        val from=scheduleInput(startDefault.format(format));val to=scheduleInput(endDefault.format(format))
        body.addWide(scheduleDateRow("Start",from,startDefault));body.addGap(8)
        body.addWide(intervalRow,48);body.addGap(8)
        val duration=QualityRuler(this).apply { steps=600;selected=((if(existing)((prefs.getLong("end",0)-prefs.getLong("start",0))/60000).toInt() else (cfg.maxRecordingMinutes.takeIf { it>0 } ?: 120))-1).coerceIn(0,599);contentDescription="Schedule duration in minutes" }
        val durationLabel=label("",12f,0xffffdf58.toInt()).apply { gravity=Gravity.CENTER;includeFontPadding=false;setLineSpacing(0f,1f) }
        body.addWide(durationLabel,20);body.addWide(duration,36);body.addGap(8)
        body.addWide(scheduleDateRow("End",to,endDefault));body.addGap(12)
        var changing=false
        var endInstantFromRuler:Long?=null
        fun setEnd() {
            durationLabel.text="Duration · ${ScheduleDefaults.durationText(duration.selected+1)}"
            val start=runCatching { LocalDateTime.parse(from.text.toString(),format) }.getOrNull() ?: return
            val end=start.atZone(ZoneId.systemDefault()).plusMinutes((duration.selected+1).toLong())
            endInstantFromRuler=end.toInstant().toEpochMilli()
            changing=true;to.setText(end.toLocalDateTime().format(format));changing=false
        }
        duration.changed={ _,_ -> setEnd() }
        from.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { if(!changing)setEnd() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        to.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                if(!changing)runCatching {
                    endInstantFromRuler=null
                    val minutes=java.time.Duration.between(LocalDateTime.parse(from.text.toString(),format).atZone(ZoneId.systemDefault()).toInstant(),LocalDateTime.parse(s.toString(),format).atZone(ZoneId.systemDefault()).toInstant()).toMinutes()
                    if(minutes in 1..600) { duration.selected=minutes.toInt()-1;durationLabel.text="Duration · ${ScheduleDefaults.durationText(minutes.toInt())}" }
                }
            }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        setEnd()
        val selectedMode=runCatching { CameraMode.valueOf(if(existing)prefs.getString("mode","BOTH")!! else "BOTH") }.getOrDefault(CameraMode.BOTH)
        val frontButton=button("Front") {}.apply { toggleAppearance=true;isSelected=selectedMode!=CameraMode.REAR }
        val rearButton=button("Rear") {}.apply { toggleAppearance=true;isSelected=selectedMode!=CameraMode.FRONT }
        val frontOptions=mutableListOf<CaptureQuality>();val rearOptions=mutableListOf<CaptureQuality>()
        val front=QualityRuler(this);val rear=QualityRuler(this)
        val frontLabel=label("Loading quality…",12f,0xffffdf58.toInt()).apply { gravity=Gravity.CENTER;isSingleLine=true;includeFontPadding=false }
        val rearLabel=label("Loading quality…",12f,0xffffdf58.toInt()).apply { gravity=Gravity.CENTER;isSingleLine=true;includeFontPadding=false }
        val pair=LinearLayout(this).apply { clipChildren=false;clipToPadding=false }
        listOf(Triple(frontButton,frontLabel,front),Triple(rearButton,rearLabel,rear)).forEachIndexed { index,(button,label,ruler) ->
            val col=column();col.addWide(button,48)
            (button.layoutParams as LinearLayout.LayoutParams).apply { if(index==0)marginEnd=dp(42) else marginStart=dp(42) }
            col.addGap(3);col.addWide(label,20);col.addWide(ruler,36)
            pair.addView(col,LinearLayout.LayoutParams(0,-2,1f).apply { if(index==1)marginStart=dp(8) })
        }
        val frame=FrameLayout(this).apply { clipChildren=false;clipToPadding=false;addView(pair,FrameLayout.LayoutParams(-1,-2)) }
        val lights=LinearLayout(this).apply { clipChildren=false;clipToPadding=false }
        val screenLight=iconButton(HeaderIcon.SCREEN_LIGHT) {}.apply { squareFace=true;toggleAppearance=true;isSelected=if(existing)prefs.getBoolean("screenLight",app.screenLightWanted) else app.screenLightWanted }
        val rearLight=iconButton(HeaderIcon.LIGHT) {}.apply { squareFace=true;toggleAppearance=true;isSelected=if(existing)prefs.getBoolean("light",app.lightForNextRecording) else app.lightForNextRecording }
        listOf(screenLight,rearLight).forEachIndexed { index,b ->
            b.setOnClickListener { b.isSelected=!b.isSelected;b.stateDescription=if(b.isSelected)"Selected" else "Off" }
            lights.addView(b,LinearLayout.LayoutParams(dp(40),dp(48)).apply { if(index>0)marginStart=dp(4) })
        }
        frame.addView(lights,FrameLayout.LayoutParams(dp(84),dp(48),Gravity.TOP or Gravity.CENTER_HORIZONTAL));body.addWide(frame);if(cfg.audioOnly)frame.visibility=View.GONE
        fun chosenMode()=if(frontButton.isSelected && rearButton.isSelected)CameraMode.BOTH else if(frontButton.isSelected)CameraMode.FRONT else CameraMode.REAR
        fun quality(ruler:QualityRuler,label:TextView,options:List<CaptureQuality>,selected:Boolean) {
            label.text=options.getOrNull(ruler.selected)?.let { "${it.width} × ${it.height}" } ?: "Quality unavailable"
            label.setTextColor(if(selected)0xffffdf58.toInt() else Palette.muted);ruler.isEnabled=selected && options.size>1
        }
        var loading:Job?=null
        fun load() {
            if(cfg.audioOnly)return
            loading?.cancel()
            val currentMode=chosenMode()
            val keys=listOf(frontOptions.getOrNull(front.selected)?.key ?: if(existing)prefs.getString("front",cfg.frontQuality)!! else cfg.frontQuality,
                rearOptions.getOrNull(rear.selected)?.key ?: if(existing)prefs.getString("rear",cfg.rearQuality)!! else cfg.rearQuality)
            loading=scope.launch {
                val values=withContext(Dispatchers.Default) { val catalog=app.capture?.create(this@MainActivity);listOf(CameraSource.FRONT,CameraSource.REAR).map { source -> runCatching { catalog?.qualities(source,cfg.copy(cameraMode=currentMode)).orEmpty() }.getOrDefault(emptyList()) } }
                if(screen!="schedule" || root!==page)return@launch
                listOf(frontOptions,rearOptions).forEachIndexed { index,options -> options.clear();options.addAll(values[index]) }
                listOf(front,rear).forEachIndexed { index,ruler -> ruler.steps=values[index].size;ruler.selected=values[index].indexOfFirst { it.key==keys[index] }.coerceAtLeast(0);ruler.contentDescription="Scheduled ${if(index==0)"front" else "rear"} resolution" }
                quality(front,frontLabel,frontOptions,frontButton.isSelected);quality(rear,rearLabel,rearOptions,rearButton.isSelected)
            }
        }
        front.changed={ _,_ -> quality(front,frontLabel,frontOptions,frontButton.isSelected) };rear.changed={ _,_ -> quality(rear,rearLabel,rearOptions,rearButton.isSelected) }
        listOf(frontButton,rearButton).forEach { b -> b.setOnClickListener { b.isSelected=!b.isSelected;b.stateDescription=if(b.isSelected)"Selected" else "Off";load() } }
        val error=label("",14f,Palette.red);body.addGap(12);body.addWide(error)
        val arm=button("Save schedule",ButtonRole.OUTSIDE) { scope.launch {
            try {
                val start=LocalDateTime.parse(from.text.toString(),format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end=endInstantFromRuler ?: LocalDateTime.parse(to.text.toString(),format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                require(start>System.currentTimeMillis() && end>start && end-start<=600*60000L) { "Start must be in the future; duration must be 1–600 minutes." }
                require(cfg.audioOnly || frontButton.isSelected || rearButton.isSelected) { "Select at least one camera." }
                if(cfg.photoMode) {
                    val seconds=normalDigits(interval.text.toString()).toIntOrNull() ?: error("Enter photo interval seconds.")
                    require(seconds in 1..86400) { "Photo interval must be 1–86400 seconds." };cfg=cfg.copy(photoIntervalSeconds=seconds)
                }
                val selected=chosenMode()
                require(cfg.audioOnly || ((!frontButton.isSelected || frontOptions.isNotEmpty()) && (!rearButton.isSelected || rearOptions.isNotEmpty()))) { "Selected camera quality is unavailable." }
                require(app.status.value.phase==Phase.IDLE) { "Stop recording before saving a schedule." }
                val frontKey=frontOptions.getOrNull(front.selected)?.key ?: cfg.frontQuality;val rearKey=rearOptions.getOrNull(rear.selected)?.key ?: cfg.rearQuality
                val scheduledConfig=cfg.copy(cameraMode=selected,frontQuality=frontKey,rearQuality=rearKey)
                val useRearLight=rearLight.isSelected;val useScreenLight=screenLight.isSelected
                val support=withContext(Dispatchers.IO) { app.capture?.create(this@MainActivity)?.support(scheduledConfig) }
                require(support?.available==true) { support?.detail ?: "Camera module unavailable." }
                prefs.edit().putBoolean("screenMode",false).putBoolean("mixedMode",false).putInt("minutes",((end-start)/60000).toInt()).putInt("fps",scheduledConfig.fps).putBoolean("armed",true).putLong("start",start).putLong("end",end).putString("mode",selected.name).putString("front",frontKey).putString("rear",rearKey)
                    .putBoolean("light",!scheduledConfig.audioOnly && useRearLight).putBoolean("screenLight",!scheduledConfig.audioOnly && useScreenLight).putBoolean("continueVideoWithoutMic",scheduledConfig.continueVideoWithoutMic).putBoolean("audioOnly",scheduledConfig.audioOnly).putBoolean("photos",scheduledConfig.photoMode).putInt("interval",scheduledConfig.photoIntervalSeconds).commit()
                RecordingAutomation.alarm(this@MainActivity,start,"start");RecordingAutomation.alarm(this@MainActivity,end,"end")
                val required=CapturePermissions.runtime(scheduledConfig).toMutableList()
                if(Build.VERSION.SDK_INT>=33)required+=Manifest.permission.POST_NOTIFICATIONS
                val missing=required.filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED };if(missing.isNotEmpty())requestPermissions(missing.toTypedArray(),902)
                error.setTextColor(Palette.mint);error.text="Schedule saved.";updateScheduleSummary()
            } catch(t:Throwable) { if(t !is CancellationException) { error.setTextColor(Palette.red);error.text=t.message ?: "Invalid schedule." } }
        } }
        body.addGap(8);body.addWide(arm,48);body.addGap(8)
        body.addWide(button("Cancel schedule") { RecordingAutomation.cancel(this);error.text="Schedule cancelled.";updateScheduleSummary() },48);body.addGap(8)
        val exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        if(exact)body.addWide(label("Exact timing · allowed",14f,Palette.mint))
        else body.addWide(button("Allow exact schedule") { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:$packageName"))) },48)
        fun showCaptureType() {
            val selected=if(cfg.audioOnly)2 else if(cfg.photoMode)1 else 0
            captureButtons.forEachIndexed { i,b -> b.isSelected=i==selected }
            intervalRow.visibility=if(cfg.photoMode)View.VISIBLE else View.GONE
            frame.visibility=if(cfg.audioOnly)View.GONE else View.VISIBLE
        }
        captureButtons.forEachIndexed { i,b -> b.setOnClickListener {
            cfg=cfg.copy(audioOnly=i==2,photoMode=i==1);showCaptureType();load()
        } }
        showCaptureType();load()
    }
    private fun shortcutDialog() {
        val body=column();val saved=RecordingShortcut.read(this)
        val enabled=NeonSwitch(this).apply { text="Enable shortcut";isChecked=saved.enabled }
        body.addWide(enabled,48);body.addGap(12)
        val selected=saved.keys.toMutableSet()
        val gesture=NeonSpinner(this)
        body.addWide(spinnerRow("Gesture",gesture,listOf("Repeated presses","Hold together"),if(saved.gesture==ShortcutGesture.HOLD)1 else 0),48);body.addGap(12)
        val count=timeInput(saved.count.toString()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;contentDescription="Shortcut press count" }
        val hold=timeInput(saved.holdMs.toString()).apply { inputType=android.text.InputType.TYPE_CLASS_NUMBER;contentDescription="Shortcut hold milliseconds" }
        val countRow=inputRow("Press count",count);val holdRow=inputRow("Hold · ms",hold)
        body.addWide(countRow,48);body.addWide(holdRow,48);body.addGap(12)
        val choices=mutableListOf<NeonSwitch>()
        fun renderFields() {
            countRow.visibility=if(gesture.selectedItemPosition==0)View.VISIBLE else View.GONE
            holdRow.visibility=if(gesture.selectedItemPosition==1)View.VISIBLE else View.GONE
            gesture.isEnabled=enabled.isChecked;count.isEnabled=enabled.isChecked;hold.isEnabled=enabled.isChecked
            choices.forEach { it.isEnabled=enabled.isChecked }
        }
        RecordingShortcut.keys.forEach { (code,name) ->
            val choice=NeonSwitch(this).apply {
                text=name;isChecked=code in selected;contentDescription="Shortcut key: $name"
                onUserChange={ checked ->
                    if(checked)selected+=code else selected-=code
                    if(selected.size>1)gesture.setSelection(1)
                    renderFields()
                }
            }
            choices+=choice;body.addWide(choice,48);body.addGap(8)
        }
        gesture.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent:AdapterView<*>?)=Unit
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { renderFields() }
        }
        enabled.onUserChange={ renderFields() };renderFields()
        val dialog=NeonDialog.Builder(this).setTitle("Recording shortcut").setView(body).setPositiveButton("Save",null).setNegativeButton("Back",null).create()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val mode=if(gesture.selectedItemPosition==1)ShortcutGesture.HOLD else ShortcutGesture.PRESSES
                val n=if(enabled.isChecked && mode==ShortcutGesture.PRESSES)normalDigits(count.text.toString()).toIntOrNull() ?: error("Enter a whole press count.") else saved.count
                val ms=if(enabled.isChecked && mode==ShortcutGesture.HOLD)normalDigits(hold.text.toString()).toLongOrNull() ?: error("Enter a whole hold duration.") else saved.holdMs
                RecordingShortcut.save(this,ShortcutConfig(enabled.isChecked,selected.toSet(),mode,n,ms))
                resetShortcut();shortcutMatcher=null;dialog.dismiss()
            } catch(t:Throwable) { dialog.showError(t.message ?: "Check the shortcut.") }
        };dialog.show()
    }
    private var swipeStartX=0f
    private var swipeStartY=0f
    private var swipeAt=0L
    private var swipeBlocked=false
    private fun touchesControl(view:View,x:Float,y:Float):Boolean {
        if(view.visibility!=View.VISIBLE)return false
        val at=IntArray(2);view.getLocationOnScreen(at)
        if(x<at[0] || x>at[0]+view.width || y<at[1] || y>at[1]+view.height)return false
        if(view is android.widget.EditText || view is android.widget.SeekBar || view is android.widget.Button || view.javaClass.simpleName in setOf("QualityRuler","MixedLayoutEditor","NativeSaveSwitch"))return true
        if(view is ViewGroup)for(i in 0 until view.childCount)if(touchesControl(view.getChildAt(i),x,y))return true
        return false
    }
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        if((screen=="home" || screen=="photo") && monitorOverlay==null && whiteLamp==null) {
            if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN) { swipeStartX=event.rawX;swipeStartY=event.rawY;swipeAt=event.eventTime;swipeBlocked=touchesControl(root,event.rawX,event.rawY) }
            if(event.actionMasked==android.view.MotionEvent.ACTION_UP && !swipeBlocked && event.eventTime-swipeAt<1000) {
                val dx=event.rawX-swipeStartX;val dy=event.rawY-swipeStartY
                val horizontal=kotlin.math.abs(dx)>dp(96) && kotlin.math.abs(dx)>kotlin.math.abs(dy)*1.5f
                val up=dy< -dp(120) && kotlin.math.abs(dy)>kotlin.math.abs(dx)*1.5f
                if(horizontal && (screen=="photo" || app.status.value.phase==Phase.IDLE) || up && screen=="home") {
                    val cancel=android.view.MotionEvent.obtain(event).apply { action=android.view.MotionEvent.ACTION_CANCEL };super.dispatchTouchEvent(cancel);cancel.recycle()
                    if(screen=="photo") {
                        val clip=currentClip
                        if(clip!=null)scope.launch {
                            val target=withContext(Dispatchers.IO) { val photos=app.repository!!.list().filter { it.isPhoto };val pair=PlaybackNavigation.neighbors(photos,clip,false);if(dx<0)pair.second else pair.first }
                            if(screen=="photo" && currentClip?.id==clip.id)target?.let(::showPhoto)
                        }
                    } else if(up)showArchive() else { val c=app.repository?.config() ?: RecordingConfig();val mode=when { c.mixedMode -> 4;c.screenMode -> 3;c.audioOnly -> 1;c.photoMode -> 2;else -> 0 };selectCaptureMode((mode+if(dx<0)1 else 4)%5) }
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
    private var shortcutMatcher:ShortcutMatcher?=null
    private var shortcutFocused=true
    private fun currentShortcut():ShortcutMatcher {
        val config=RecordingShortcut.read(this)
        if(shortcutMatcher?.config!=config) { resetShortcut();shortcutMatcher=ShortcutMatcher(config) }
        return shortcutMatcher!!
    }
    private fun shortcutCanRun()=activityVisible && shortcutFocused && NeonDialog.showing(this)?.isShowing!=true
    private fun resetShortcut() { mainHandler.removeCallbacks(shortcutHold);shortcutMatcher?.reset() }
    private fun triggerShortcut() {
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        toggleRecording(app.repository?.config()?.cameraMode ?: CameraMode.BOTH)
    }
    private val shortcutHold=Runnable {
        val matcher=shortcutMatcher
        if(shortcutCanRun() && matcher?.poll(SystemClock.elapsedRealtime())==true)triggerShortcut()
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(!shortcutCanRun())return super.onKeyDown(keyCode,event)
        val matcher=currentShortcut()
        if(!matcher.accepts(keyCode))return super.onKeyDown(keyCode,event)
        if(matcher.down(keyCode,SystemClock.elapsedRealtime(),event.repeatCount>0))triggerShortcut()
        matcher.dueAt?.let { due -> mainHandler.removeCallbacks(shortcutHold);mainHandler.postDelayed(shortcutHold,(due-SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
        return true
    }
    override fun onKeyUp(keyCode:Int,event:KeyEvent):Boolean {
        val matcher=shortcutMatcher
        if(matcher?.accepts(keyCode)!=true)return super.onKeyUp(keyCode,event)
        if(event.isCanceled)resetShortcut() else { matcher.up(keyCode);mainHandler.removeCallbacks(shortcutHold) }
        return true
    }
    override fun onWindowFocusChanged(hasFocus:Boolean) {
        super.onWindowFocusChanged(hasFocus);shortcutFocused=hasFocus
        if(!hasFocus)resetShortcut()
    }
    private fun timeInput(value:String)=EditText(this).apply {
        id=View.generateViewId();setText(value);textSize=16f;setTextColor(Palette.mint);setHintTextColor(0xffa0a0a0.toInt());background=null;setPadding(dp(8),dp(6),dp(8),dp(6));isSingleLine=true;layoutDirection=View.LAYOUT_DIRECTION_LTR;gravity=Gravity.START
        inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
    private fun normalDigits(value:String)=value.map { ch -> when(ch) { in '۰'..'۹' -> '0'+(ch-'۰');in '٠'..'٩' -> '0'+(ch-'٠');'٫' -> '.';else -> ch } }.joinToString("")
    private fun formatOffset(value:Long):String=String.format(Locale.US,"%02d:%02d:%02d.%03d",value/3_600_000,(value/60_000)%60,(value/1000)%60,value%1000)
    private fun parseOffset(value:String):Long {
        val parts=normalDigits(value).trim().split(':');require(parts.size in 1..3) { "Invalid time format." }
        var seconds=0.0
        parts.forEachIndexed { i,p -> val n=p.toDouble();require(n.isFinite() && n>=0 && (i==0 || n<60));seconds=seconds*60+n }
        return (seconds*1000).toLong()
    }
    private fun back() { if(monitorOverlay!=null) { collapseMonitor();return };if(whiteLamp!=null) { app.screenLightWanted=false;renderScreenLight();return };if(screen=="photo") { if(previousScreen=="archive")showArchive() else showHome();return };if(screen=="player") { if(previousScreen=="archive")showArchive() else showHome() } else if(screen=="details")showInfoTools() else if(screen=="battery_metrics")showDeviceStatus(0) else if(screen=="capture_timing")saveCaptureTiming() else if(screen in setOf("archive","health","schedule","tools"))showHome() else super.onBackPressed() }
    @Deprecated("Uses a deterministic single-activity back stack") override fun onBackPressed() { back() }
    private fun shareLatestCapture() {
        scope.launch { try {
            val clip=withContext(Dispatchers.IO) { app.repository!!.list().filter { matchesMode(it) && it.status==ClipStatus.READY }.maxByOrNull { it.startMs } }
            if(clip==null) { toast("No completed file to share.");return@launch }
            val uri=android.net.Uri.parse(clip.uri)
            val mime=if(clip.isPhoto)"image/jpeg" else if(clip.isAudio)"audio/aac" else "video/mp4"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).apply {
                clipData=android.content.ClipData.newRawUri(clip.name,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },"Share capture"))
        } catch(t:Throwable) { showError(t) } }
    }
    private fun updateScheduleIcons() {
        val automation=getSharedPreferences("automation",0)
        val armed=getSharedPreferences("home_start",0).getLong("at",0)>System.currentTimeMillis() || automation.getBoolean("armed",false) && automation.getLong("end",0)>System.currentTimeMillis()
        listOfNotNull(transport[HeaderIcon.SCHEDULE],scheduleShortcut).forEach { it.scheduleArmed=armed;it.stateDescription=if(armed)"Scheduled start stored" else "No scheduled start" }
        transport[HeaderIcon.BOOKMARK]?.badgeCount=CaptureMarkers(this).list().count { it.sessionStart==app.status.value.startedMs }
    }
    private val meterTicker=object:Runnable { override fun run() { if(screen=="player")updatePlaybackControls();mainHandler.postDelayed(this,80) } }
    private fun updateCaptureCounter() {
        captureCounter?.elapsedMs=app.captureClock.elapsedMs()
    }
    private fun maybeAutoPlayScreen() {
        val start=autoPlayScreenAfter ?: return
        if(autoPlayScreenBusy || app.status.value.phase!=Phase.IDLE || screen!="home" || !activityVisible)return
        autoPlayScreenBusy=true
        scope.launch {
            try {
                val clip=withContext(Dispatchers.IO) { app.repository?.list()?.filter { it.camera==CameraSource.SCREEN && it.status==ClipStatus.READY && it.startMs>=start-1000 }?.maxByOrNull { it.startMs } }
                autoPlayScreenAfter=null
                if(clip!=null && screen=="home")openPlayer(clip,false)
            } catch(t:Throwable) { autoPlayScreenAfter=null;showError(t) } finally { autoPlayScreenBusy=false }
        }
    }
    private val ticker=object:Runnable { override fun run() {
        checkSchedule();updateScheduleSummary();updateScheduleIcons();renderStatus();syncPlaybackRate();updatePlaybackControls();updateCaptureCounter();maybeAutoPlayScreen();if(screen in setOf("health","battery_metrics"))healthDetailRefresh?.invoke()
        mainHandler.postDelayed(this,1000)
    } }
    override fun onStart() { super.onStart();activityVisible=true;app.diagnostics.visibility(true);val schedule=getSharedPreferences("automation",0);if(schedule.getBoolean("armed",false)){RecordingAutomation.alarm(this,schedule.getLong("start",0),"start");RecordingAutomation.alarm(this,schedule.getLong("end",0),"end")};mainHandler.post(ticker);mainHandler.post(meterTicker);if(screen=="archive")refreshArchive();armArchiveRefresh();cameraPanels.values.forEach { it.resumePreview() } }
    override fun onResume() {
        super.onResume()
        app.uiVisible.value=true
        mixedPreviewOwner?.let { owner -> mixedSurface?.takeIf { it.isValid }?.let { app.preview(CameraSource.MIXED,owner,it) } }
        // Run after ActivityThread marks the visible activity resumed (Android 14+ camera FGS rule).
        mainHandler.post { if(activityVisible && !isFinishing && !isDestroyed)runCatching { RecordingService.resumeIfPending(this) }.onFailure { showError(it) } }
    }
    override fun onStop() { app.uiVisible.value=false;mixedPreviewOwner?.let { app.preview(CameraSource.MIXED,it,null) };resetShortcut();collapseMonitor();disableScreenLight();activityVisible=false;app.diagnostics.visibility(false);activeTrimDialog?.dismiss();mainHandler.removeCallbacks(ticker);mainHandler.removeCallbacks(meterTicker);mainHandler.removeCallbacks(archiveTicker);archiveScanJob?.cancel();archiveScanJob=null;player?.pause();cameraPanels.values.forEach { it.pausePreview() };super.onStop() }
    override fun onDestroy() { cameraPanels.values.forEach { it.dispose() };releasePlayer();scope.cancel();super.onDestroy() }
    private fun toast(message:String) {
        if(isDestroyed)return
        if(statusLabel!=null) { transientMessage=message;transientUntil=SystemClock.uptimeMillis()+3500;renderStatus() }
        else Toast.makeText(this,message,Toast.LENGTH_LONG).show()
    }
    private fun showError(error:Throwable) {
        if(error is CancellationException)return
        val message=error.message ?: "Operation failed."
        val dialog=NeonDialog.showing(this)
        if(dialog!=null) { dialog.showError(message);return }
        app.status.update { it.copy(message=message,error=true) };renderStatus()
        if(statusLabel==null)toast(message)
    }
}
