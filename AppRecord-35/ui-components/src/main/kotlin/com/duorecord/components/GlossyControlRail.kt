package com.duorecord.components

import android.content.Context
import android.graphics.*
import android.view.View

/** Reflective black rail aligned with the icon faces above it. */
class GlossyControlRail(context:Context):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    init { setLayerType(LAYER_TYPE_SOFTWARE,null);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c:Canvas) {
        val d=resources.displayMetrics.density;val b=RectF(4*d,d,width-4*d,4*d)
        p.shader=LinearGradient(0f,b.top,0f,b.bottom,intArrayOf(0xff51575b.toInt(),0xff030405.toInt(),Color.BLACK),floatArrayOf(0f,.30f,1f),Shader.TileMode.CLAMP)
        p.setShadowLayer(2*d,0f,2*d,0x6656d6ef);c.drawRoundRect(b,d,d,p);p.clearShadowLayer();p.shader=null
    }
}
