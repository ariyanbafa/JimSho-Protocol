package com.jimsho.protocol

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var startBtn: MaterialButton
    private lateinit var stopBtn: MaterialButton
    private lateinit var nekoBtn: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var secretKeyInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var copyLocalBtn: MaterialButton
    private lateinit var copyLanBtn: MaterialButton

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "GOOSE_STATUS" -> {
                    val msg = intent.getStringExtra("status") ?: ""
                    statusText.text = msg
                    statusText.setTextColor(getColor(R.color.text_sub))
                }
                "GOOSE_READY" -> {
                    progressBar.progress = 100
                    statusText.text = "اتصال با موفقیت برقرار شد ✅"
                    statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                    startBtn.visibility = View.GONE
                    stopBtn.isEnabled = true
                    nekoBtn.visibility = View.VISIBLE
                }
                "GOOSE_ERROR" -> {
                    progressBar.visibility = View.GONE
                    statusText.text = "خطا در اتصال! ❌"
                    statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                    startBtn.isEnabled = true
                    stopBtn.isEnabled = false
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
        statusText = findViewById(R.id.statusText)
        val telegramText: TextView = findViewById(R.id.telegramText)
        copyLocalBtn = findViewById(R.id.copyLocalBtn)
        copyLanBtn = findViewById(R.id.copyLanBtn)

        val prefs = getSharedPreferences("JimShoPrefs", MODE_PRIVATE)
        val savedKey = prefs.getString("SECRET_KEY", "")
        secretKeyInput.setText(savedKey)

        telegramText.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/JimShoVPN")))
            } catch (e: Exception) {
                Toast.makeText(this, "تلگرام نصب نیست", Toast.LENGTH_SHORT).show()
            }
        }

        // دکمه کپی لینک برای همین گوشی
        copyLocalBtn.setOnClickListener {
            val link = "tg://socks?server=127.0.0.1&port=1080"
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Proxy Link", link))
            Toast.makeText(this, "لینک کپی شد! در مرورگر پیست کنید.", Toast.LENGTH_LONG).show()
        }

        // دکمه کپی لینک برای دستگاه‌های دیگر (هات‌اسپات)
        copyLanBtn.setOnClickListener {
            val ip = getLocalIpAddress()
            if (ip != null) {
                val link = "tg://socks?server=$ip&port=2080"
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Proxy Link", link))
                Toast.makeText(this, "لینک کپی شد!\nIP: $ip", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "وای‌فای یا هات‌اسپات روشن نیست!", Toast.LENGTH_SHORT).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        val filter = IntentFilter().apply {
            addAction("GOOSE_STATUS")
            addAction("GOOSE_READY")
            addAction("GOOSE_ERROR")
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

            prefs.edit().putString("SECRET_KEY", key).apply()
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            statusText.text = "در حال اتصال..."
            statusText.setTextColor(getColor(R.color.text_sub))
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
            statusText.text = "اتصال قطع شد"
            statusText.setTextColor(getColor(R.color.text_sub))
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

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("172.20.10."))) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}
