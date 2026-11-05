package com.screensage

import android.app.Activity
import android.content.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64

class MainActivity : AppCompatActivity() {
    private val captureRequest = 401
    private val voiceRequest = 402
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var question: EditText
    private lateinit var backendUrl: EditText
    private lateinit var entry: EditText
    private lateinit var exitPremium: EditText
    private lateinit var quantity: EditText
    private lateinit var charges: EditText
    private lateinit var lossLimit: EditText
    private var imageBase64: String? = null
    private var ocrText = ""
    private val http = OkHttpClient()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("path")?.let { processCapture(File(it)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        fun label(s: String, size: Float = 16f) = TextView(this).apply { text=s; textSize=size; setPadding(0,12,0,6) }
        fun field(s: String, numeric: Boolean=false) = EditText(this).apply {
            hint=s
            if(numeric) inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        fun button(s: String, action: () -> Unit) = Button(this).apply { text=s; setOnClickListener { action() } }

        page.addView(label("ScreenSage AI",28f))
        page.addView(label("Your screen, understood in Hinglish.",14f))
        status=label("Ready. Capture starts only when you tap the button.",14f); page.addView(status)
        page.addView(label("AI screen assistant"))
        backendUrl=field("Gemini backend HTTPS URL"); backendUrl.setSingleLine(true)
        backendUrl.setText(getPreferences(MODE_PRIVATE).getString("backend_url",""))
        page.addView(backendUrl)
        page.addView(button("Capture current screen") { requestCapture() })
        question=field("Ask about the captured screen…"); page.addView(question)
        page.addView(button("Ask AI (Hinglish)") { askAI() })
        page.addView(button("Ask by voice") { askVoice() })
        output=label("OCR text and AI answers appear here.",15f); page.addView(output)

        page.addView(label("Trading Coach — Options Buying"))
        page.addView(label("Manual Groww trade journal. No live broker connection.",13f))
        entry=field("Buy premium (₹)",true); exitPremium=field("Exit premium (₹)",true)
        quantity=field("Quantity (units)",true); charges=field("Total charges (₹)",true)
        lossLimit=field("Daily max loss limit (₹)",true)
        page.addView(entry); page.addView(exitPremium); page.addView(quantity); page.addView(charges); page.addView(lossLimit)
        page.addView(button("Calculate P&L and risk guidance") { calculateTrade() })
        page.addView(button("Enable floating assistant") { enableFloating() })
        page.addView(button("Enable scroll assistance") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            status.text="Enable ScreenSage in Accessibility settings, then return."
        })
        page.addView(button("Scroll down (manual)") { ScreenAccessibilityService.scrollDown() })
        page.addView(button("Scroll up (manual)") { ScreenAccessibilityService.scrollUp() })

        setContentView(ScrollView(this).apply { addView(page, ViewGroup.LayoutParams(-1,-2)) })
        ContextCompat.registerReceiver(this, receiver, IntentFilter(CaptureService.ACTION_CAPTURED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun requestCapture() {
        val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(),captureRequest)
    }

    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==captureRequest && resultCode==Activity.RESULT_OK && data!=null) {
            val i=Intent(this,CaptureService::class.java).putExtra("resultCode",resultCode).putExtra("data",data)
            ContextCompat.startForegroundService(this,i)
            status.text="Capture approved. Reading one frame…"
        } else if(requestCode==voiceRequest && resultCode==Activity.RESULT_OK) {
            question.setText(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty())
        } else if(requestCode==captureRequest) status.text="Capture permission not granted."
    }

    private fun processCapture(file:File) {
        val bitmap=android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: run { status.text="Image read failed."; return }
        val stream=java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,78,stream)
        imageBase64=Base64.getEncoder().encodeToString(stream.toByteArray())
        status.text="Reading screen text…"
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromBitmap(bitmap,0))
            .addOnSuccessListener { result ->
                ocrText=result.text
                output.text="SCREEN TEXT\n\n"+ocrText.ifBlank { "Readable text nahi mila." }
                status.text="Capture complete. Ab sawaal poochho."
            }.addOnFailureListener { status.text="OCR error: ${it.localizedMessage}" }
    }

    private fun askAI() {
        val base=backendUrl.text.toString().trim().trimEnd('/')
        val image=imageBase64
        if(base.isBlank() || image==null) { status.text="HTTPS backend URL enter karo aur screen capture karo."; return }
        getPreferences(MODE_PRIVATE).edit().putString("backend_url",base).apply()
        status.text="AI screen analyse kar raha hai…"
        val body=JSONObject().put("imageBase64",image).put("ocrText",ocrText)
            .put("question",question.text.toString().ifBlank { "Is screen par kya dikh raha hai? Hinglish mein samjhao." })
        val request=Request.Builder().url("$base/analyze")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                http.newCall(request).execute().use { response ->
                    val text=response.body?.string().orEmpty()
                    if(!response.isSuccessful) throw Exception("HTTP ${response.code}: $text")
                    val answer=JSONObject(text).optString("answer","AI response nahi mila.")
                    withContext(Dispatchers.Main) { output.text="AI ANSWER\n\n$answer\n\n--- OCR ---\n$ocrText"; status.text="Analysis complete." }
                }
            } catch(e:Exception) { withContext(Dispatchers.Main) { status.text="AI failed: ${e.message}" } }
        }
    }

    private fun askVoice() {
        val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hi-IN")
        try { startActivityForResult(i,voiceRequest) } catch(_:Exception) { status.text="Voice recognition unavailable." }
    }

    private fun calculateTrade() {
        val buy=entry.text.toString().toDoubleOrNull()
        val sell=exitPremium.text.toString().toDoubleOrNull()
        val qty=quantity.text.toString().toDoubleOrNull()
        val fee=charges.text.toString().toDoubleOrNull() ?: 0.0
        val limit=lossLimit.text.toString().toDoubleOrNull()
        if(buy==null || sell==null || qty==null || buy<=0 || sell<0 || qty<=0) {
            status.text="Valid premium aur quantity enter karo."; return
        }
        val pnl=(sell-buy)*qty-fee
        val note=when {
            pnl<0 && limit!=null && -pnl>=limit -> "NO TRADE: entered daily loss limit hit. Aaj trading rokna safer hai."
            pnl<0 -> "REVIEW: loss recover karne ke liye impulsive next trade mat lena. Predefined stop-loss follow karo."
            pnl>0 -> "Profit charges ke baad hai. Isse next trade ka green signal mat samjho."
            else -> "Flat after charges. Fresh setup ke bina trade mat lo."
        }
        output.text="TRADE REVIEW\n\nNet P&L: ₹${"%.2f".format(pnl)}\nPremium exposure: ₹${"%.2f".format(buy*qty)}\n\n$note\n\nManual calculation only. No live Groww feed, option Greeks or order execution."
        status.text="Calculation complete."
    }

    private fun enableFloating() {
        if(!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        else { ContextCompat.startForegroundService(this,Intent(this,FloatingService::class.java)); status.text="Floating launcher enabled." }
    }

    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
}
