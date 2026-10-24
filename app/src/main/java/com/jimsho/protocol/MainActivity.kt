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
    private lateinit var deploymentInput: TextInputEditText
    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "GOOSE_LOG" -> {
                    val logMsg = intent.getStringExtra("log") ?: ""
                    logText.append("\n$logMsg")
                    logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
                    
                    if (logMsg.contains("آماده‌سازی")) progressBar.progress = 20
                    if (logMsg.contains("مجوز")) progressBar.progress = 40
                    if (logMsg.contains("تنظیمات")) progressBar.progress = 60
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
        deploymentInput = findViewById(R.id.deploymentIdInput)
        logText = findViewById(R.id.logText)
        logScroll = findViewById(R.id.logScroll)

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
            val id = deploymentInput.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "Deployment ID را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
            logText.text = "[System] در حال شروع..."
            startBtn.isEnabled = false
            stopBtn.isEnabled = true
            nekoBtn.visibility = View.GONE

            val intent = Intent(this, GooseService::class.java)
            intent.putExtra("DEPLOYMENT_ID", id)
            startService(intent)
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, GooseService::class.java))
            startBtn.isEnabled = true
            startBtn.visibility = View.VISIBLE
            stopBtn.isEnabled = false
            nekoBtn.visibility = View.GONE
            progressBar.visibility = View.GONE
            logText.text = "[System] اتصال قطع شد."
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
