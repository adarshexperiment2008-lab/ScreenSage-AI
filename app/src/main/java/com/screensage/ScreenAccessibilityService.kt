package com.screensage

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ScreenAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var active:ScreenAccessibilityService?=null
        fun scrollDown(){ active?.rootInActiveWindow?.let { scroll(it,AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) } }
        fun scrollUp(){ active?.rootInActiveWindow?.let { scroll(it,AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) } }
        private fun scroll(node:AccessibilityNodeInfo,action:Int):Boolean {
            if(node.isScrollable && node.performAction(action)) return true
            for(i in 0 until node.childCount) { val child=node.getChild(i); if(child!=null && scroll(child,action)) return true }
            return false
        }
    }
    override fun onServiceConnected(){ active=this }
    override fun onAccessibilityEvent(event:AccessibilityEvent?){}
    override fun onInterrupt(){}
    override fun onDestroy(){ active=null; super.onDestroy() }
}
