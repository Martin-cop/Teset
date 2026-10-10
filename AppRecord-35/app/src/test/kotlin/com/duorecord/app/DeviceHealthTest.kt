package com.duorecord.app

import android.content.*
import android.os.BatteryManager
import android.os.PowerManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DeviceHealthTest {
    private fun reader(intent:Intent?):DeviceHealth {
        val context=object:ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun registerReceiver(receiver:BroadcastReceiver?,filter:IntentFilter):Intent?=intent
        }
        return DeviceHealthReader.read(context,10,1)
    }
    @Test fun batteryBroadcastIsScaledAndTemperatureIsTenthsCelsius() {
        val intent=Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,72).putExtra(BatteryManager.EXTRA_SCALE,80)
            .putExtra(BatteryManager.EXTRA_TEMPERATURE,345).putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_USB)
            .putExtra(BatteryManager.EXTRA_HEALTH,BatteryManager.BATTERY_HEALTH_GOOD)
        val health=reader(intent)
        assertEquals(90,health.batteryPercent);assertEquals(34.5,health.batteryTemperatureC!!,.001)
        assertTrue(health.charging);assertEquals("Good",health.batteryHealth)
        assertTrue(health.headerText().contains("90% + · 34.5°C"))
    }
    @Test fun missingReadingsStayUnknownRatherThanShowingZero() {
        val health=reader(null)
        assertNull(health.batteryPercent);assertNull(health.batteryTemperatureC)
        assertEquals("Unknown",health.batteryHealth);assertEquals("—",health.temperatureText())
        assertTrue(health.headerText().startsWith("Battery — · —"))
    }
    @Test fun chargingPreventsLowBatteryFlagAndActualHazardsAreHighlighted() {
        val intent=Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,5).putExtra(BatteryManager.EXTRA_SCALE,100)
        assertTrue(reader(intent).lowBattery)
        intent.putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_AC)
        assertFalse(reader(intent).lowBattery)
        val normal=DeviceHealth(80,35.0,false,"Good",PowerManager.THERMAL_STATUS_NONE,10_000_000,false,false)
        assertFalse(normal.danger)
        assertTrue(normal.copy(thermalStatus=PowerManager.THERMAL_STATUS_SEVERE).danger)
        assertTrue(normal.copy(batteryHealth="Overvoltage").danger)
        assertTrue(normal.copy(batteryHealth="Cold").danger)
        assertTrue(normal.copy(lowStorage=true).danger)
    }
    @Test fun chargingUsesReportedSignedBatteryCurrentAndVoltageWithoutGuessingAdapterRating() {
        val manager=org.robolectric.RuntimeEnvironment.getApplication().getSystemService(BatteryManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,1_500_000)
        val intent=Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,40).putExtra(BatteryManager.EXTRA_SCALE,100)
            .putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_AC).putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_CHARGING)
            .putExtra(BatteryManager.EXTRA_VOLTAGE,4200)
        val h=reader(intent)
        assertEquals(1500.0,h.batteryCurrentMa!!,.001);assertEquals(4.2,h.batteryVoltageV!!,.001);assertEquals(6.3,h.batteryPowerWatts!!,.001)
        assertTrue(h.chargePulse);assertTrue(h.chargingText().contains("+1.50 A"));assertTrue(h.chargingText().contains("Speed not reported"))
        assertFalse(h.chargingText().contains("Fast"));assertFalse(h.copy(batteryPercent=100).chargePulse)
        intent.putExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_NOT_CHARGING)
        assertFalse(reader(intent).chargePulse)
        org.robolectric.Shadows.shadowOf(manager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,-500_000)
        assertEquals(-2.1,reader(intent).batteryPowerWatts!!,.001)
    }
    @Test fun unsupportedOrInvalidChargeSensorsDoNotBecomeZeroWatts() {
        val manager=org.robolectric.RuntimeEnvironment.getApplication().getSystemService(BatteryManager::class.java)
        org.robolectric.Shadows.shadowOf(manager).setIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,Int.MIN_VALUE)
        val h=reader(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED,BatteryManager.BATTERY_PLUGGED_USB))
        assertNull(h.batteryCurrentMa);assertNull(h.batteryVoltageV);assertNull(h.batteryPowerWatts)
        assertTrue(h.chargingText().contains("Current unavailable"));assertFalse(h.chargePulse)
    }

    @Test fun expandedBatteryDetailsReadStandardExtrasAndDoNotInventMissingCycleCounts() {
        val h=reader(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL,73).putExtra(BatteryManager.EXTRA_SCALE,100)
            .putExtra(BatteryManager.EXTRA_PRESENT,true).putExtra(BatteryManager.EXTRA_TECHNOLOGY,"Li-ion")
            .putExtra(BatteryManager.EXTRA_CYCLE_COUNT,123).putExtra(BatteryManager.EXTRA_BATTERY_LOW,false))
        assertEquals(123,h.cycleCount);assertEquals("Li-ion",h.batteryTechnology);assertEquals(true,h.batteryPresent)
        val fields=h.batteryDetails().toMap();assertEquals("123",fields["Cycle count"])
        assertEquals("Not reported by Android",fields["Charger rated power"])
        val absent=reader(null).batteryDetails().toMap();assertEquals("Unavailable",absent["Cycle count"]);assertEquals("Unavailable",absent["Technology"])
    }
    @Test fun detailsDistinguishReportedEnergyFromCapacityEstimateAndUpdateAfterUnplug() {
        val h=DeviceHealth(50,35.0,true,"Good",0,null,false,false,batteryCurrentMa=1200.0,batteryVoltageV=4.0,estimatedCapacityMah=5000.0,reportedEnergyWh=9.8,chargeTimeRemainingMs=600000)
        val f=h.batteryDetails().toMap();assertEquals("9.80 Wh",f["Remaining energy · reported"]);assertEquals("10.00 Wh",f["Remaining energy · estimated"]);assertEquals("4.80 W",f["Battery power · signed"])
        val off=h.copy(charging=false).batteryDetails().toMap();assertEquals("Disconnected",off["Connection"]);assertEquals("Not charging",off["Time to full · estimated"])
    }

}
