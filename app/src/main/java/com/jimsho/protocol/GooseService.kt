package com.jimsho.protocol

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class GooseService : Service() {
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deploymentId = intent?.getStringExtra("DEPLOYMENT_ID") ?: ""
        
        Thread {
            try {
                // 1. استخراج فایل goose-client از داخل APK
                val binaryFile = File(filesDir, "goose-client")
                if (!binaryFile.exists()) {
                    val inputStream = assets.open("goose-client")
                    val outputStream = FileOutputStream(binaryFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                }
                
                // 2. دادن مجوز اجرا
                binaryFile.setExecutable(true)

                // 3. ساخت فایل تنظیمات client_config.json
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

                // 4. اجرای پروسه
                val pb = ProcessBuilder(binaryFile.absolutePath, "-config", configFile.absolutePath)
                pb.redirectErrorStream(true)
                process = pb.start()

                // ارسال پیام موفقیت به رابط کاربری
                val successIntent = Intent("GOOSE_READY")
                sendBroadcast(successIntent)

            } catch (e: Exception) {
                Log.e("GooseService", "Error: ${e.message}")
            }
        }.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        process?.destroy()
    }
}
