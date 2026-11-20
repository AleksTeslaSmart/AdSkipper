package com.adskipper

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** Простой экран: статус сервиса, кнопка включения, переключатель «без звука», счётчик. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var counter: TextView
    private lateinit var toggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("adskipper", Context.MODE_PRIVATE)
        val pad = (24 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "AdSkipper"
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
            text = "Выключать звук во время рекламы"
            textSize = 16f
            isChecked = prefs.getBoolean("mute_ads", true)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("mute_ads", on).apply() }
        }
        val help = TextView(this).apply {
            textSize = 14f
            text = "Как включить:\n" +
                "1. Нажмите кнопку выше.\n" +
                "2. Найдите «AdSkipper» в разделе «Скачанные приложения» / «Установленные службы».\n" +
                "3. Включите переключатель и подтвердите.\n\n" +
                "Если переключатель серый (Android 13+): Настройки → Приложения → AdSkipper → " +
                "⋮ (вверху справа) → «Разрешить ограниченные настройки», затем повторите."
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            addView(title); addView(status); addView(counter)
            addView(toggle); addView(mute); addView(help)
        })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        status.text = if (on) "✅ Сервис включён" else "❌ Сервис выключен"
        toggle.text = if (on) "Открыть настройки специальных возможностей" else "Включить сервис"
        val n = getSharedPreferences("adskipper", Context.MODE_PRIVATE).getInt("skipped", 0)
        counter.text = "Пропущено реклам: $n"
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, SkipAdService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
