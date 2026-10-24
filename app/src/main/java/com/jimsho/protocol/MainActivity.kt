package com.jimsho.protocol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var nekoBtn: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var secretKeyInput: TextInputEditText
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "GOOSE_LOG" -> {
                    val logMsg = intent.getStringExtra("log") ?: ""
                    logText.append("\n$logMsg")
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                    
                    if (logMsg.contains("شروع")) progressBar.progress = 20
                    if (logMsg.contains("اجرای موتور")) progressBar.progress = 80
                }
                "GOOSE_READY" -> {
                    progressBar.progress = 100
                    startBtn.visibility = View.GONE
                    stopBtn.isEnabled = true
                    nekoBtn.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        nekoBtn = findViewById(R.id.nekoBtn)
        progressBar = findViewById(R.id.progressBar)
        secretKeyInput = findViewById(R.id.secretKeyInput)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)
        val telegramText: TextView = findViewById(R.id.telegramText)

        // لود کردن کلید ذخیره شده
        val prefs = getSharedPreferences("JimShoPrefs", MODE_PRIVATE)
        val savedKey = prefs.getString("SECRET_KEY", "")
        secretKeyInput.setText(savedKey)

        // باز کردن لینک تلگرام
        telegramText.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/JimShoVPN"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "تلگرام نصب نیست", Toast.LENGTH_SHORT).show()
            }
        }

        // درخواست دسترسی نوتیفیکیشن (اندروید ۱۳+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val filter = IntentFilter().apply {
            addAction("GOOSE_LOG")
            addAction("GOOSE_READY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        startBtn.setOnClickListener {
            val key = secretKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Secret Key را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ذخیره کلید برای دفعات بعد
            prefs.edit().putString("SECRET_KEY", key).apply()

            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            logText.text = "[System] در حال شروع..."
            startBtn.isEnabled = false
            stopBtn.isEnabled = true
            nekoBtn.visibility = View.GONE

            val intent = Intent(this, GooseService::class.java)
            intent.putExtra("DEPLOYMENT_ID", key)
            startService(intent)
        }

        stopBtn.setOnClickListener {
            val intent = Intent(this, GooseService::class.java)
            intent.action = "STOP"
            startService(intent)
            
            startBtn.isEnabled = true
            startBtn.visibility = View.VISIBLE
            stopBtn.isEnabled = false
            nekoBtn.visibility = View.GONE
            progressBar.visibility = View.GONE
            logText.text = "[System] متوقف شد."
            Toast.makeText(this, "تونل قطع شد", Toast.LENGTH_SHORT).show()
        }

        nekoBtn.setOnClickListener {
            val socksUri = Uri.parse("socks://127.0.0.1:1080#JimSho_Tunnel")
            val intent = Intent(Intent.ACTION_VIEW, socksUri)
            intent.setPackage("moe.nb4a")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val launchIntent = packageManager.getLaunchIntentForPackage("moe.nb4a")
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this, "NekoBox نصب نیست!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
