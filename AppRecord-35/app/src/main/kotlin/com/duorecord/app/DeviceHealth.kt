package com.duorecord.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.util.Locale

internal data class DeviceHealth(
    val batteryPercent:Int?,val batteryTemperatureC:Double?,val charging:Boolean,
    val batteryHealth:String,val thermalStatus:Int?,val freeBytes:Long?,val lowBattery:Boolean,val lowStorage:Boolean,val totalBytes:Long?=null,val cpuTemperatureC:Double?=null,
    val rollingBytes:Long?=null,val rollingLimitBytes:Long=8L*1073741824,
    val batteryCurrentMa:Double?=null,val batteryVoltageV:Double?=null,val chargingActive:Boolean=false,val chargeSource:String="Unknown",val estimatedCapacityMah:Double?=null,
    val batteryStatus:String="Unknown",val batteryPresent:Boolean?=null,val batteryTechnology:String?=null,
    val remainingChargeMah:Double?=null,val averageCurrentMa:Double?=null,val reportedEnergyWh:Double?=null,
    val chargeTimeRemainingMs:Long?=null,val cycleCount:Int?=null,val chargingStatusCode:Int?=null,
    val systemBatteryLow:Boolean?=null,val powerSaveMode:Boolean?=null,val rawLevel:Int?=null,val rawScale:Int?=null
) {
    val batteryPowerWatts:Double? get()=batteryCurrentMa?.let { ma -> batteryVoltageV?.let { ma/1000*it } }
    fun powerFormula():String {
        val voltage=batteryVoltageV?.let { kotlin.math.round(it*100)/100 }
        val amps=batteryCurrentMa?.let { kotlin.math.round(it/10)/100 }
        fun number(n:Double?)=n?.let { String.format(Locale.US,"%.2f",it) } ?: "—"
        return "${number(voltage)} V × ${number(amps)} A = ${number(if(voltage!=null && amps!=null)voltage*amps else null)} W"
    }
    fun batteryDetails():List<Pair<String,String>> {
        fun number(n:Double?,unit:String)=n?.let { String.format(Locale.US,"%.2f %s",it,unit) } ?: "Unavailable"
        fun flag(b:Boolean?)=b?.let { if(it)"Yes" else "No" } ?: "Unavailable"
        return listOf(
            "Charge" to (batteryPercent?.let { "$it%" } ?: "Unavailable"),
            "Battery temperature" to temperatureText(),"Battery health" to batteryHealth,
            "Battery status" to batteryStatus,"Battery present" to flag(batteryPresent),
            "Technology" to (batteryTechnology ?: "Unavailable"),
            "Connection" to if(charging)"Connected" else "Disconnected",
            "Connection type" to if(charging)chargeSource else "—",
            "Charging" to if(chargingActive)"Yes" else if(charging && batteryPercent==100)"Full" else "No",
            "Battery voltage" to number(batteryVoltageV,"V"),
            "Battery current" to number(batteryCurrentMa?.div(1000),"A"),
            "Average battery current" to number(averageCurrentMa?.div(1000),"A"),
            "Battery power · signed" to number(batteryPowerWatts,"W"),
            "Remaining charge · reported" to number(remainingChargeMah,"mAh"),
            "Full capacity · estimated" to number(estimatedCapacityMah,"mAh"),
            "Remaining energy · reported" to number(reportedEnergyWh,"Wh"),
            "Remaining energy · estimated" to number(estimatedCapacityMah?.let { m -> batteryVoltageV?.let { v -> batteryPercent?.let { m*v*it/100000 } } },"Wh"),
            "Time to full · estimated" to if(!charging)"Not charging" else chargeTimeRemainingMs?.let { "${it/60000} min" } ?: "Unavailable",
            "Cycle count" to (cycleCount?.toString() ?: "Unavailable"),
            "Charging status · Android code" to (chargingStatusCode?.toString() ?: "Unavailable"),
            "System low-battery flag" to flag(systemBatteryLow),"Battery saver" to flag(powerSaveMode),
            "Raw charge / scale" to if(rawLevel!=null && rawScale!=null)"$rawLevel / $rawScale" else "Unavailable",
            "Device thermal status" to thermalLabel,
            "Charger rated power" to "Not reported by Android",
            "Charging protocol / speed" to "Not reported by Android",
            "Capacity accuracy" to "Estimate, not design capacity",
            "Current direction" to "Positive: charging; negative: discharging"
        )
    }
    val chargePulse:Boolean get()=charging && chargingActive && (batteryPercent ?: 100)<100
    fun chargingText():String=if(!charging)"Not connected" else "${if(chargingActive)"Charging" else if(batteryPercent==100)"Full" else "Connected"} · $chargeSource\nSpeed not reported"+
        (batteryCurrentMa?.let { String.format(Locale.US,"\n%+.2f A · battery",it/1000) } ?: "\nCurrent unavailable")
    val thermalLabel:String get()=when(thermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }
    val danger:Boolean get()=(thermalStatus ?: 0)>=PowerManager.THERMAL_STATUS_SEVERE || batteryHealth in setOf("Overheated","Overvoltage","Failed","Dead","Cold") || lowBattery || lowStorage
    fun temperatureText()=batteryTemperatureC?.let { String.format(Locale.US,"%.1f°C",it) } ?: "—"
    fun headerText():String {
        val free=freeBytes?.let { String.format(Locale.US,"%.1f GB",it/1073741824.0) } ?: "—"
        return "Battery ${batteryPercent?.let { "$it%" } ?: "—"}${if(charging)" +" else ""} · ${temperatureText()}\nHeat $thermalLabel · Free $free"
    }
}
internal object DeviceHealthReader {
    /** Charge counter is remaining microamp-hours; full capacity is only an estimate. */
    fun estimateCapacityMah(remainingUah:Int?,percent:Int?):Double? {
        if(remainingUah==null || remainingUah<=0 || percent==null || percent !in 5..100)return null
        return (remainingUah/(10.0*percent)).takeIf { it.isFinite() && it in 500.0..30000.0 }
    }
    fun read(context:Context,stopBattery:Int,reserveBytes:Long):DeviceHealth {
        val intent=runCatching { context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level=intent?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1
        val scale=intent?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        val percent=if(scale>0 && level in 0..scale)(level*100L/scale).toInt() else null
        val temp=intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperature=if(temp!=Int.MIN_VALUE && temp in -500..1500)temp/10.0 else null
        val charging=(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0) ?: 0)!=0
        val health=when(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH,BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Overvoltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        val thermal=runCatching { context.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull()
        val free=if(Environment.getExternalStorageState()==Environment.MEDIA_MOUNTED)runCatching {
            val disk=StatFs(Environment.getExternalStorageDirectory().absolutePath)
            if(disk.totalBytes>0)disk.availableBytes else null
        }.getOrNull() else null
        val manager=context.getSystemService(BatteryManager::class.java)
        val current=runCatching { manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrNull()
            ?.takeIf { it!=Int.MIN_VALUE && it in -30_000_000..30_000_000 }?.div(1000.0)
        val charge=runCatching { manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }.getOrNull()
        val capacity=estimateCapacityMah(charge,percent)
        val voltage=intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0)?.takeIf { it in 1000..20000 }?.div(1000.0)
        val status=intent?.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN)
        val source=when(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            else -> "Unknown source"
        }
        fun intExtra(key:String):Int?=intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key,-1)?.takeIf { it>=0 }
        fun boolExtra(key:String):Boolean?=intent?.takeIf { it.hasExtra(key) }?.getBooleanExtra(key,false)
        val average=runCatching { manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) }.getOrNull()?.takeIf { it!=Int.MIN_VALUE && it in -30_000_000..30_000_000 }?.div(1000.0)
        val energy=runCatching { manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) }.getOrNull()?.takeIf { it>=0 && it!=Long.MAX_VALUE }?.div(1_000_000_000.0)
        val time=if(charging)runCatching { manager?.computeChargeTimeRemaining() }.getOrNull()?.takeIf { it>=0 } else null
        return DeviceHealth(percent,temperature,charging,health,thermal,free,!charging && percent!=null && percent<=stopBattery,free!=null && free<=reserveBytes,runCatching { StatFs(Environment.getExternalStorageDirectory().absolutePath).totalBytes.takeIf { it>0 } }.getOrNull(),runCatching {
            context.getSystemService(android.os.HardwarePropertiesManager::class.java)?.getDeviceTemperatures(android.os.HardwarePropertiesManager.DEVICE_TEMPERATURE_CPU,android.os.HardwarePropertiesManager.TEMPERATURE_CURRENT)?.filter { it.isFinite() && it in -50f..150f }?.maxOrNull()?.toDouble()
        }.getOrNull(),batteryCurrentMa=current,batteryVoltageV=voltage,chargingActive=status==BatteryManager.BATTERY_STATUS_CHARGING,chargeSource=source,estimatedCapacityMah=capacity,
            batteryStatus=when(status) { BatteryManager.BATTERY_STATUS_CHARGING -> "Charging";BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging";BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging";BatteryManager.BATTERY_STATUS_FULL -> "Full";else -> "Unknown" },
            batteryPresent=boolExtra(BatteryManager.EXTRA_PRESENT),batteryTechnology=intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            remainingChargeMah=charge?.takeIf { it>=0 && it!=Int.MIN_VALUE }?.div(1000.0),averageCurrentMa=average,reportedEnergyWh=energy,chargeTimeRemainingMs=time,
            cycleCount=if(android.os.Build.VERSION.SDK_INT>=34)intExtra(BatteryManager.EXTRA_CYCLE_COUNT) else null,
            chargingStatusCode=if(android.os.Build.VERSION.SDK_INT>=34)intExtra(BatteryManager.EXTRA_CHARGING_STATUS) else null,
            systemBatteryLow=boolExtra(BatteryManager.EXTRA_BATTERY_LOW),powerSaveMode=runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode }.getOrNull(),
            rawLevel=level.takeIf { it>=0 },rawScale=scale.takeIf { it>0 }).also { PowerHistory(context.applicationContext).record(it);(context.applicationContext as? DuoApp)?.observeBatteryTemperature(it.batteryTemperatureC) }
    }
}
