package com.sarcasmo

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule

class CapturaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CapturaAccessibilityService"
        private const val API_URL = "https://b818c21e-0a56-455b-afa9-ace0493a0696-00-1r1ou3r2wrszv.worf.replit.dev/analyze"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBubble: FrameLayout
    private lateinit var bubbleTextView: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initializeFloatingBubble()
    }

    private fun initializeFloatingBubble() {
        floatingBubble = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null) as FrameLayout
        bubbleTextView = floatingBubble.findViewById(R.id.bubble_text)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingBubble, params)

        floatingBubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingBubble, params)
                    true
                }
                else -> false
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED || 
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val visibleText = StringBuilder()
                collectVisibleText(rootNode, visibleText)
                if (visibleText.isNotEmpty()) {
                    val text = visibleText.toString()
                    Log.d(TAG, "Captured Text: $text")
                    analyzeText(text)
                }
            }
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, visibleText: StringBuilder) {
        if (node.isVisibleToUser && node.text != null && isNodeWithinScreenBounds(node)) {
            visibleText.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                collectVisibleText(childNode, visibleText)
                childNode.recycle()
            }
        }
    }

    private fun isNodeWithinScreenBounds(node: AccessibilityNodeInfo): Boolean {
        val screenRect = Rect()
        node.getBoundsInScreen(screenRect)

        val screenSize = Point()
        windowManager.defaultDisplay.getSize(screenSize)

        return screenRect.intersect(0, 0, screenSize.x, screenSize.y)
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service Interrupted")
    }

    private fun analyzeText(text: String) {
        val sanitizedText = text.replace("[\\u0000-\\u001F]".toRegex(), "")
        val client = OkHttpClient()
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = RequestBody.create(mediaType, "{\"text\":\"$sanitizedText\"}")
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API Request Failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.d(TAG, "Text Analysis Response: $responseData")
                    responseData?.let {
                        val jsonResponse = JSONObject(it)
                        val compound = jsonResponse.getDouble("compound")
                        updateBubble(compound)
                        sendEventToReactNative("TextAnalysisEvent", it)
                    }
                }
            }
        })
    }

    private fun updateBubble(compound: Double) {
        handler.post {
            val green = (255 * ((compound + 1) / 2)).toInt()
            val red = (255 * (1 - (compound + 1) / 2)).toInt()
            val color = 0xFF000000.toInt() or (red shl 16) or (green shl 8)

            bubbleTextView.setBackgroundColor(color)
            bubbleTextView.text = String.format("%.2f", compound)
        }
    }

    private fun sendEventToReactNative(eventName: String, eventData: String) {
        val reactContext = (applicationContext as MainApplication).reactNativeHost.reactInstanceManager.currentReactContext
        reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            ?.emit(eventName, eventData)
    }
}
