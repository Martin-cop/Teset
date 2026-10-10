package com.duorecord.domain

enum class ShortcutGesture { PRESSES, HOLD }
data class ShortcutConfig(val enabled:Boolean=true,val keys:Set<Int>,val gesture:ShortcutGesture=ShortcutGesture.PRESSES,val count:Int=3,val holdMs:Long=800) {
    fun validate() {
        require(!enabled || keys.isNotEmpty()) { "Select at least one key." }
        require(gesture!=ShortcutGesture.PRESSES || keys.size<=1) { "Use Hold together for a combination of keys." }
        require(count in 2..5) { "Press count must be 2–5." }
        require(holdMs in 500..5000) { "Hold duration must be 500–5000 milliseconds." }
    }
}

/** Consumes explicit down/up edges; auto-repeat cannot count as multiple presses. */
class ShortcutMatcher(val config:ShortcutConfig) {
    private val pressed=mutableSetOf<Int>()
    private var count=0;private var firstPress=0L;private var fired=false
    var dueAt:Long?=null;private set
    fun accepts(key:Int)=config.enabled && key in config.keys
    fun down(key:Int,now:Long,repeat:Boolean=false):Boolean {
        if(!accepts(key) || repeat || !pressed.add(key))return false
        if(config.gesture==ShortcutGesture.HOLD) {
            if(!fired && pressed.containsAll(config.keys))dueAt=now+config.holdMs
            return false
        }
        if(count==0 || now-firstPress>2000) { count=0;firstPress=now }
        count++
        if(count>=config.count) { count=0;return true }
        return false
    }
    fun up(key:Int) {
        if(!accepts(key))return
        pressed.remove(key);dueAt=null
        if(pressed.isEmpty())fired=false
    }
    fun poll(now:Long):Boolean {
        if(config.gesture!=ShortcutGesture.HOLD || fired || dueAt?.let { now>=it }!=true || !pressed.containsAll(config.keys))return false
        dueAt=null;fired=true;return true
    }
    fun reset() { pressed.clear();dueAt=null;count=0;fired=false;firstPress=0 }
}
