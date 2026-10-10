package com.duorecord.app

import android.content.Context

/** USB/AC is a transport classification, not proof of a wall charger or power bank. */
internal enum class ChargingSourceChoice(val label:String) { UNKNOWN("Not selected"), WALL("Wall charger"), POWER_BANK("Power bank");
    companion object {
        fun read(context:Context)=runCatching { valueOf(context.getSharedPreferences("charge_source",0).getString("manual","UNKNOWN")!!) }.getOrDefault(UNKNOWN)
        fun select(context:Context,value:ChargingSourceChoice) { context.getSharedPreferences("charge_source",0).edit().putString("manual",value.name).apply() }
        fun active(connected:Boolean,selected:ChargingSourceChoice,icon:ChargingSourceChoice)=connected && selected==icon && selected!=UNKNOWN
    }
}
