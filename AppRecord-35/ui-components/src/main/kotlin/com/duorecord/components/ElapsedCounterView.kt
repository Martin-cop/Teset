package com.duorecord.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView
import java.util.Locale

/** Session elapsed time, in a reusable framed digital counter. */
class ElapsedCounterView(context:Context):TextView(context) {
    var elapsedMs=0L;set(value) { field=value.coerceAtLeast(0);render() }
    init {
        gravity=Gravity.CENTER;includeFontPadding=false;textSize=28f;typeface=Typeface.create("monospace",Typeface.NORMAL)
        setTextColor(0xff65ff99.toInt())
        background=GradientDrawable().apply { setColor(0xff0b1014.toInt());cornerRadius=8*resources.displayMetrics.density;setStroke(resources.displayMetrics.density.toInt().coerceAtLeast(1),0xff65ff99.toInt()) }
        render()
    }
    private fun render() { val seconds=elapsedMs/1000;text=String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60);contentDescription="Session elapsed $text" }
}
