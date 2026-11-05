package com.screensage

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class CaptureService : Service() {
    companion object { const val ACTION_CAPTURED="com.screensage.CAPTURED"; private const val CHANNEL="screensage_capture" }
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private val handler=Handler(Looper.getMainLooper())
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Screen capture",NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val notification=NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("ScreenSage AI").setContentText("Capturing one screen image").setOngoing(true).build()
        if(Build.VERSION.SDK_INT>=29) startForeground(7,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(7,notification)
        val code=intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("data",Intent::class.java) else @Suppress("DEPRECATION") (intent?.getParcelableExtra("data") as? Intent)
        if(data==null || code!=Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=manager.getMediaProjection(code,data)
        projection?.registerCallback(object:MediaProjection.Callback(){ override fun onStop(){ stopSelf() } },handler)
        val metrics=resources.displayMetrics
        reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,2)
        display=projection?.createVirtualDisplay("ScreenSageCapture",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
        handler.postDelayed({ saveFrame() },900)
        return START_NOT_STICKY
    }
    private fun saveFrame() {
        try {
            val image=reader?.acquireLatestImage()
            if(image==null) { handler.postDelayed({saveFrame()},350); return }
            val p=image.planes[0]; val pad=(p.rowStride-p.pixelStride*image.width)/p.pixelStride
            val wide=android.graphics.Bitmap.createBitmap(image.width+pad,image.height,android.graphics.Bitmap.Config.ARGB_8888)
            wide.copyPixelsFromBuffer(p.buffer); image.close()
            val bitmap=android.graphics.Bitmap.createBitmap(wide,0,0,imageWidth(wide),wide.height)
            val file=File(cacheDir,"screen_capture.jpg")
            FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,it) }
            sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName).putExtra("path",file.absolutePath))
        } catch(_:Exception) { }
        stopSelf()
    }
    private fun imageWidth(bitmap:android.graphics.Bitmap)=resources.displayMetrics.widthPixels.coerceAtMost(bitmap.width)
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
    override fun onBind(intent:Intent?)=null
}
