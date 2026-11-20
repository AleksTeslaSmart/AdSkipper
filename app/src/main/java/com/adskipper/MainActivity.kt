package com.adskipper

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Простой экран: статус сервиса, кнопка включения, переключатель «без звука», счётчик.
 *  Все тексты лежат в res/values-xx/strings.xml — язык выбирается по языку телефона. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("adskipper", Context.MODE_PRIVATE)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad / 2)
        }
        counter = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        toggle = Button(this).apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val mute = Switch(this).apply {
            text = getString(R.string.mute_ads)
            textSize = 16f
            isChecked = prefs.getBoolean("mute_ads", true)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("mute_ads", on).apply() }
        }
        val help = TextView(this).apply {
            textSize = 14f
            text = getString(R.string.help)
        }

        updateBtn = Button(this).apply {
            visibility = View.GONE
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(title); addView(status); addView(counter)
            addView(updateBtn)
            addView(toggle); addView(mute); addView(help)
        })
    }

    companion object {
        private const val REPO = "AleksTeslaSmart/AdSkipper"
        private const val APK_URL = "https://github.com/$REPO/releases/latest/download/AdSkipper.apk"
        private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    }

    private lateinit var updateBtn: Button

    /** Спрашивает у GitHub номер последней версии и, если она новее, показывает кнопку «Обновить». */
    private fun checkForUpdate() {
        val installed = installedVersionCode()
        Thread {
            val latest = try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Regex("\"tag_name\"\\s*:\\s*\"v1\\.0\\.(\\d+)\"").find(body)?.groupValues?.get(1)?.toLongOrNull()
            } catch (e: Exception) {
                null   // нет интернета — просто не показываем кнопку
            }
            if (latest != null && latest > installed) {
                runOnUiThread {
                    updateBtn.text = getString(R.string.btn_update, "1.0.$latest")
                    updateBtn.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = getString(if (on) R.string.status_on else R.string.status_off)
        toggle.text = getString(if (on) R.string.btn_settings else R.string.btn_enable)
        val n = getSharedPreferences("adskipper", Context.MODE_PRIVATE).getInt("skipped", 0)
        counter.text = getString(R.string.skipped_count, n)
        checkForUpdate()
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, SkipAdService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
