package com.duorecord.domain

import org.junit.Assert.*
import org.junit.Test

class ShortcutGestureTest {
    private fun hold()=ShortcutMatcher(ShortcutConfig(keys=setOf(1,2),gesture=ShortcutGesture.HOLD,holdMs=800))
    @Test fun bothSelectedKeysMustBeHeldForTheWholeDuration() {
        val m=hold();m.down(1,0);assertNull(m.dueAt);assertFalse(m.poll(5000))
        m.down(2,5000);assertEquals(5800L,m.dueAt)
        assertFalse(m.poll(5799));assertTrue(m.poll(5800));assertFalse(m.poll(9000))
    }
    @Test fun releasingEitherKeyEarlyCancelsTheCombination() {
        val m=hold();m.down(1,0);m.down(2,1);m.up(2)
        assertFalse(m.poll(2000));m.down(2,2000)
        assertFalse(m.poll(2799));assertTrue(m.poll(2800))
    }
    @Test fun firedHoldCannotRepeatUntilAllKeysAreReleased() {
        val m=hold();m.down(1,0);m.down(2,0);assertTrue(m.poll(800))
        m.down(1,2000,true);assertFalse(m.poll(3000))
        m.up(1);m.down(1,4000);assertFalse(m.poll(5000))
        m.up(1);m.up(2);m.down(1,6000);m.down(2,6000);assertTrue(m.poll(6800))
    }
    @Test fun autorepeatIsNotThreePressesAndTwoSecondWindowExpires() {
        val m=ShortcutMatcher(ShortcutConfig(keys=setOf(1)))
        assertFalse(m.down(1,0));assertFalse(m.down(1,100,true));assertFalse(m.down(1,200,true));m.up(1)
        assertFalse(m.down(1,300));m.up(1);assertTrue(m.down(1,400));m.up(1)
        assertFalse(m.down(1,1000));m.up(1);assertFalse(m.down(1,3101));m.up(1)
        assertFalse(m.down(1,3200));m.up(1);assertTrue(m.down(1,3300))
    }
    @Test fun resetOnLostFocusCancelsPendingHoldAndPresses() {
        val m=hold();m.down(1,0);m.down(2,0);m.reset();assertFalse(m.poll(1000));assertNull(m.dueAt)
        val presses=ShortcutMatcher(ShortcutConfig(keys=setOf(1),count=2));presses.down(1,0);presses.up(1);presses.reset();assertFalse(presses.down(1,100))
    }
    @Test fun disabledShortcutAndUnselectedKeysNeverTrigger() {
        val m=ShortcutMatcher(ShortcutConfig(enabled=false,keys=setOf(1),count=2));assertFalse(m.accepts(1))
        assertFalse(m.down(1,0));m.up(1);assertFalse(m.down(1,100))
        val active=hold();assertFalse(active.accepts(3));active.down(3,0);assertNull(active.dueAt)
    }
    @Test fun combinationsRequireHoldAndValuesAreValidatedBeforeSaving() {
        listOf(ShortcutConfig(keys=emptySet()),ShortcutConfig(keys=setOf(1,2)),ShortcutConfig(keys=setOf(1),count=1),ShortcutConfig(keys=setOf(1),holdMs=100)).forEach { config ->
            try { config.validate();fail("Invalid shortcut was accepted") } catch(_:IllegalArgumentException) {}
        }
        ShortcutConfig(keys=setOf(1,2),gesture=ShortcutGesture.HOLD).validate()
    }
}
