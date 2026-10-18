package com.duorecord.app.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import com.duorecord.app.inEnglish
import com.duorecord.components.SpeedometerView

/** Stand-alone SpeedometerView demo behind its own launcher icon: slider, hold-to-repeat −/+ and the needle style switch. */
class SpeedometerDemoActivity : Activity() {
    override fun attachBaseContext(base:Context) { super.attachBaseContext(base.inEnglish()) }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val speedometer=SpeedometerView(this)
        val readout=label("0 km/h",28f,Palette.mint,true).apply { gravity=Gravity.CENTER }
        val seek=SeekBar(this).apply { max=SpeedometerView.MAX_SPEED.toInt() }
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar,value:Int,fromUser:Boolean) { speedometer.speed=value.toFloat();readout.text="$value km/h" }
            override fun onStartTrackingTouch(bar:SeekBar) {}
            override fun onStopTrackingTouch(bar:SeekBar) {}
        })
        // Tap steps 1 km/h; holding repeats every 50 ms until release.
        fun step(text:String,delta:Int)=button(text) { seek.progress+=delta }.apply {
            val repeat=object:Runnable { override fun run() { seek.progress+=delta;postDelayed(this,50) } }
            setOnLongClickListener { post(repeat);true }
            setOnTouchListener { _,e -> if(e.actionMasked==MotionEvent.ACTION_UP || e.actionMasked==MotionEvent.ACTION_CANCEL)removeCallbacks(repeat);false }
        }
        val steps=LinearLayout(this).apply {
            addView(step("−",-1),LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd=dp(6) })
            addView(step("+",1),LinearLayout.LayoutParams(0,dp(48),1f).apply { marginStart=dp(6) })
        }
        val needle=Switch(this).apply {
            text="Image needle (supplied 100×99 px picture)";textSize=15f;setTextColor(Palette.text)
            setOnCheckedChangeListener { _,on -> speedometer.needle=if(on)SpeedometerView.Needle.IMAGE else SpeedometerView.Needle.VECTOR }
        }
        // Matches the dial picture's corners so the square artwork blends into the page.
        val root=column().apply { setBackgroundColor(0xff1d2326.toInt());layoutDirection=View.LAYOUT_DIRECTION_LTR }
        root.addWide(speedometer);root.addGap(8);root.addWide(readout);root.addGap(16);root.addWide(seek);root.addGap(16);root.addWide(steps);root.addGap(16);root.addWide(needle)
        root.setOnApplyWindowInsetsListener { view,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(16)+bars.left,dp(16)+bars.top,dp(16)+bars.right,dp(16)+bars.bottom);insets
        }
        setContentView(root)
    }
}
