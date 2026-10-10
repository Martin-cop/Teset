package com.duorecord.app.ui

import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.text.method.DigitsKeyListener
import android.widget.EditText
import java.time.LocalDate

/** Validate typing and paste as well as the keyboard; allow deletion while editing. */
internal class ScheduleDateFilter:InputFilter {
    override fun filter(source:CharSequence,start:Int,end:Int,dest:Spanned,dstart:Int,dend:Int):CharSequence? {
        if(start==end)return null
        val inserted=source.subSequence(start,end).map { c -> when(c) { in '۰'..'۹' -> '0'+(c-'۰');in '٠'..'٩' -> '0'+(c-'٠');else -> c } }.joinToString("")
        var typed=inserted
        if(inserted.all { it.isDigit() || it in "- :" } && dend==dest.length) {
            val separators=mapOf(4 to '-',7 to '-',10 to ' ',13 to ':')
            val formatted=StringBuilder(dest.substring(0,dstart))
            for(ch in inserted) {
                val separator=separators[formatted.length]
                if(separator!=null && ch.isDigit())formatted.append(separator)
                formatted.append(ch)
            }
            typed=formatted.substring(dstart)
        }
        val candidate=dest.substring(0,dstart)+typed+dest.substring(dend)
        if(!validPrefix(candidate))return dest.subSequence(dstart,dend)
        return if(typed==source.subSequence(start,end).toString())null else typed
    }
    private fun validPrefix(s:String):Boolean {
        if(s.length>16)return false
        val separators=mapOf(4 to '-',7 to '-',10 to ' ',13 to ':')
        if(s.indices.any { i -> if(i in separators)s[i]!=separators[i] else s[i] !in '0'..'9' })return false
        for((offset,max) in listOf(5 to 12,8 to 31,11 to 23,14 to 59)) {
            if(s.length>offset && s[offset]-'0'>max/10)return false
            if(s.length>=offset+2) {
                val n=s.substring(offset,offset+2).toInt()
                if(n>max || (offset in listOf(5,8) && n==0))return false
            }
        }
        if(s.length>=10 && runCatching { LocalDate.parse(s.substring(0,10)) }.isFailure)return false
        return true
    }
}
internal fun Context.scheduleInput(value:String)=EditText(this).apply {
    id=android.view.View.generateViewId();isSingleLine=true
    inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_NORMAL
    keyListener=DigitsKeyListener.getInstance("0123456789۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩- :")
    filters=arrayOf(ScheduleDateFilter());setText(value)
    hint="YYYY-MM-DD HH:MM";contentDescription="Local date and time, YYYY-MM-DD HH:MM"
}
