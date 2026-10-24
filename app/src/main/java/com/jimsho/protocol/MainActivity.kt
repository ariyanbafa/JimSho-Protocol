package com.jimsho.protocol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var connectBtn: FloatingActionButton
    private lateinit var nekoBtn: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var deploymentInput: TextInputEditText

    // دریافت سیگنال از سرویس پس‌زمینه وقتی تونل آماده شد
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "GOOSE_READY") {
                progressBar.visibility = View.GONE
                connectBtn.visibility = View.GONE
                nekoBtn.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity, "تونل با موفقیت ساخته شد!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // اتصال متغیرها به المان‌های طراحی شده در XML
        connectBtn = findViewById(R.id.connectBtn)
        nekoBtn = findViewById(R.id.nekoBtn)
        progressBar = findViewById(R.id.progressBar)
        deploymentInput = findViewById(R.id.deploymentIdInput)

        // ثبت شنونده برای دریافت پیام موفقیت از سرویس
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter("GOOSE_READY"), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter("GOOSE_READY"))
        }

        // عملکرد دکمه گرد (شروع)
        connectBtn.setOnClickListener {
            val id = deploymentInput.text.toString().trim()
            if (id.isEmpty()) {
                Toast.makeText(this, "لطفاً Deployment ID را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // نمایش نوار لودینگ و مخفی کردن دکمه شروع
            progressBar.visibility = View.VISIBLE
            connectBtn.visibility = View.GONE

            // ارسال دستور به سرویس برای اجرای موتور Goose
            val intent = Intent(this, GooseService::class.java)
            intent.putExtra("DEPLOYMENT_ID", id)
            startService(intent)
        }

        // عملکرد دکمه اتصال به NekoBox
        nekoBtn.setOnClickListener {
            // ساخت لینک استاندارد SOCKS5 برای پورت 1080
            // با این کار NekoBox خودش کانفیگ را می‌سازد
            val socksUri = Uri.parse("socks://127.0.0.1:1080#JimSho_Tunnel")
            val intent = Intent(Intent.ACTION_VIEW, socksUri)
            
            // نام پکیج رسمی NekoBox
            intent.setPackage("moe.nb4a")

            try {
                startActivity(intent)
                Toast.makeText(this, "کانفیگ به NekoBox ارسال شد. لطفاً تایید کنید.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // اگر NekoBox لینک را قبول نکرد، فقط برنامه را باز کن
                val launchIntent = packageManager.getLaunchIntentForPackage("moe.nb4a")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    Toast.makeText(this, "لطفاً در NekoBox به صورت دستی SOCKS5 با پورت 1080 بسازید.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "NekoBox نصب نیست! لطفاً آن را نصب کنید.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // حذف شنونده هنگام بسته شدن اپ برای جلوگیری از نشت حافظه
        unregisterReceiver(receiver)
    }
}
