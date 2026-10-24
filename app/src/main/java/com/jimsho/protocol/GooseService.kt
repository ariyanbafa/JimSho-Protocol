package com.jimsho.protocol

import android.app.Service
import android.content.Intent
import android.os.IBinder
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class GooseService : Service() {
    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // بررسی دستور قطع کردن
        if (intent?.action == "STOP") {
            sendLog("در حال قطع اتصال...")
            process?.destroy()
            process = null
            stopSelf()
            return START_NOT_STICKY
        }

        val deploymentId = intent?.getStringExtra("DEPLOYMENT_ID") ?: ""
        
        Thread {
            try {
                sendLog("شروع آماده‌سازی...")
                
                val binaryFile = File(filesDir, "goose-client")
                if (!binaryFile.exists()) {
                    val inputStream = assets.open("goose-client")
                    val outputStream = FileOutputStream(binaryFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                }
                
                binaryFile.setExecutable(true)
                sendLog("مجوز اجرا داده شد.")

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
                
                sendLog("تنظیمات ساخته شد. اجرای موتور با پاسخ خودکار Y...")

                // ترفند پاسخ خودکار Y در لینوکس (echo y |)
                val cmd = arrayOf("sh", "-c", "echo y | ${binaryFile.absolutePath} -config ${configFile.absolutePath}")
                val pb = ProcessBuilder(*cmd)
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
                        sendLog("✅ تونل با موفقیت برقرار شد!")
                        sendBroadcast(Intent("GOOSE_READY"))
                    }
                }

            } catch (e: Exception) {
                sendLog("❌ خطا: ${e.message}")
            }
        }.start()

        return START_STICKY
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
        sendLog("سرویس متوقف شد.")
    }
}
