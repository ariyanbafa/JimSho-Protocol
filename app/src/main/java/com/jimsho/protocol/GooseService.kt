package com.jimsho.protocol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class GooseService : Service() {
    private var process: Process? = null
    private val CHANNEL_ID = "JimShoChannel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            sendLog("در حال قطع اتصال...")
            process?.destroy()
            process = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // شروع سرویس پیش‌زمینه (جلوگیری از بسته شدن توسط اندروید)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JimSho VPN")
            .setContentText("تونل در حال اجرا است...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(1, notification)

        val deploymentId = intent?.getStringExtra("DEPLOYMENT_ID") ?: ""
        
        Thread {
            try {
                sendLog("شروع فرآیند...")
                val binaryFile = File(applicationInfo.nativeLibraryDir, "libgoose-client.so")
                
                if (!binaryFile.exists()) {
                    sendLog("❌ فایل باینری یافت نشد!")
                    return@Thread
                }

                val configFile = File(filesDir, "client_config.json")
                val configJson = """
                    {
                      "name": "JimSho",
                      "socks_host": "127.0.0.1",
                      "socks_port": 1080,
                      "google_host": "216.239.38.120",
                      "sni": ["www.google.com", "mail.google.com"],
                      "script_keys": ["$deploymentId"],
                      "tunnel_key": "a7f3e9c2b8d4156f0e7a9c3d5b1f8e2a4c6d9b0e3f7a1c5d8b2e6f4a9c0d3b7e",
                      "coalesce_step_ms": 0,
                      "idle_slots_per_bucket": 2
                    }
                """.trimIndent()
                configFile.writeText(configJson)
                
                sendLog("اجرای موتور...")
                val pb = ProcessBuilder(binaryFile.absolutePath, "-config", configFile.absolutePath)
                pb.redirectErrorStream(true)
                process = pb.start()

                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                var line: String?
                var isReady = false

                while (reader.readLine().also { line = it } != null) {
                    val logLine = line ?: ""
                    sendLog(logLine)
                    
                    if (logLine.contains("listening on") && !isReady) {
                        isReady = true
                        sendLog("✅ تونل برقرار شد!")
                        sendBroadcast(Intent("GOOSE_READY"))
                    }
                }
            } catch (e: Exception) {
                sendLog("❌ خطا: ${e.message}")
            }
        }.start()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "JimSho Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun sendLog(message: String) {
        val intent = Intent("GOOSE_LOG")
        intent.putExtra("log", message)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        process?.destroy()
        process = null
    }
}
