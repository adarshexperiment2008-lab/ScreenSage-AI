package com.screensage

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    private var wm:WindowManager?=null
    private var bubble:View?=null
    override fun onCreate() {
        super.onCreate()
        val channel="screensage_overlay"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel,"Floating assistant",NotificationManager.IMPORTANCE_LOW))
        startForeground(8,NotificationCompat.Builder(this,channel).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ScreenSage assistant").setContentText("Tap bubble to open app").build())
        if(!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        bubble=TextView(this).apply {
            text="S"; textSize=20f; gravity=Gravity.CENTER; setTextColor(-1); setBackgroundColor(0xff6750a4.toInt())
            setOnClickListener { startActivity(Intent(this@FloatingService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        val params=WindowManager.LayoutParams(58,58,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
            .apply { gravity=Gravity.TOP or Gravity.END; x=18; y=220 }
        wm?.addView(bubble,params)
    }
    override fun onDestroy() { if(bubble!=null) wm?.removeView(bubble); super.onDestroy() }
    override fun onBind(intent:Intent?)=null
}
