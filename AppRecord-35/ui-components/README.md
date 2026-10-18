# Reusable Android components

This Android library has no dependency on the AppRecord application, repository,
service, permissions, or health readers. Its only project dependency is the small
pure Kotlin `domain` module for `AudioMeter` (and the optional `PcmMeter` analyzer).
To use it in another Android application, copy `ui-components` and `domain`, include
both in settings, and add `implementation(project(":ui-components"))`.

```kotlin
val spectrum = AudioSpectrumView(context)
spectrum.sourceLabel = "FILE • MONO SPECTRUM"
spectrum.meter = PcmMeter.analyze(pcm16Mono, pcm16Mono.size, sampleRate)
val battery = GaugeView(context).apply {
    kind = GaugeView.Kind.BATTERY
    fraction = .73f
}
val temperature = GaugeView(context).apply {
    kind = GaugeView.Kind.THERMOMETER
    fraction = 37.5f / 60f
}
val timer = ElapsedCounterView(context).apply { elapsedMs = 90_000 }
val speedometer = SpeedometerView(context).apply { speed = 60f }
```

`GaugeDrawing.draw` exposes Canvas geometry for compact/expanded custom layouts.
`GaugeView` is the standalone battery/thermometer/storage component. Update values
on the UI thread; caller owns sensor acquisition and timing. Fraction clamps to
0..1. `AudioSpectrumView` takes actual meter data and colors its lower 40% green,
next 25% yellow, and upper 35% red; this is visual styling, not clipping detection.
CLIP and RMS/peak dBFS still report actual PCM. The counter reports elapsed session
time, including paused intervals. No fabricated signal is generated.

`AnalogPowerView` renders actual nullable V/A/W readings using a calibrated display range; unavailable values show — without a needle. It does not estimate charger rating.

AnalogPowerView.showUnitInValue=false omits duplicate unit suffixes below the dial when a shared formula already identifies units.

`GlossyControlRail` is a standalone glossy black rail with a subtle concealed blue light, reusable beneath media control rows.

The audio spectrum/counter use green borders. AnalogPowerView reserves 6dp for its brighter underlight; use 12dp parent padding and 6dp sibling gaps to achieve equal 18dp visible spacing.


AppRecord 30 adds `HistoryChartView` for nullable timestamped measurements. `AudioSpectrumView.displayAmplitude` is a visual-only gain curve: actual dBFS/PCM/clipping remain unmodified. `AnalogPowerView` uses concealed yellow illumination on all four sides. The app's `CaptureClock` uses monotonic active time and is shared by counter wrappers. No sensor simulation is used for unavailable values.

`SpeedometerView` draws the old-car dial in `res/drawable-nodpi/speedometer_dial.webp` with the needle pivoted on the dial's own hub; the hub cap is redrawn over the needle root. `speed` (km/h, clamped 0..100) swings the needle with a damped spring (about 3% overshoot) onto stop pins at 0 and 100; `snapTo` moves it at once. The printed scale is uneven, so angles interpolate the 21 ticks measured on the dial (`angleFor`). `needle = Needle.VECTOR` (default) draws a sharp path in the colours of the supplied needle; `Needle.IMAGE` draws `speedometer_needle.png`, which is 100×99 px and looks soft when enlarged about 12×. With `wrap_content` height it keeps the dial's 1248:1228 aspect; it can also be inflated from XML. The app module shows it behind a separate “Speedometer” launcher icon (`SpeedometerDemoActivity`: slider, hold-to-repeat −/+, needle switch).

```kotlin
seekBar.max = SpeedometerView.MAX_SPEED.toInt()
seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { speedometer.speed = value.toFloat() }
    override fun onStartTrackingTouch(bar: SeekBar) {}
    override fun onStopTrackingTouch(bar: SeekBar) {}
})
```
