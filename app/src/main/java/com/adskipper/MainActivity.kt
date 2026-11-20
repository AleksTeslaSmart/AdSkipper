package com.adskipper

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

/**
 * Один экран, который ведёт человека за руку:
 *  - сервис выключен → 1–2 шага, у каждого одна большая кнопка;
 *  - сервис включён  → большая зелёная галочка «Всё работает».
 * Все тексты лежат в res/values-xx/strings.xml.
 */
class MainActivity : Activity() {

    companion object {
        private const val REPO = "AleksTeslaSmart/AdSkipper"
        private const val APK_URL = "https://github.com/$REPO/releases/latest/download/AdSkipper.apk"
        private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

        private const val ACCENT = 0xFFF2541B.toInt()
        private const val GREEN = 0xFF1E9E5A.toInt()
        private const val INK = 0xFF12141C.toInt()
        private const val MUTED = 0xFF5A6072.toInt()
        private const val CARD = 0xFFFFFFFF.toInt()
        private const val PAPER = 0xFFF3F4F8.toInt()
        private const val LINE = 0xFFDADDE7.toInt()

        /** Ставится, когда человек ушёл включать сервис: сервис вернёт его сюда. */
        const val PREF_AWAITING = "awaiting_enable"
        /** Ставится, когда человек открывал экран «О приложении» (шаг 1). */
        private const val PREF_UNLOCK_VISITED = "unlock_visited"
    }

    private val prefs by lazy { getSharedPreferences("adskipper", Context.MODE_PRIVATE) }
    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var root: LinearLayout
    private var updateVersion: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.statusBarColor = PAPER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = px(20)
            setPadding(p, px(28), p, p)
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(PAPER)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        // Вернулись из настроек, а сервис так и не включился — значит, скорее всего,
        // блокировка не снята: снова показываем шаг 1 активным.
        if (prefs.getBoolean(PREF_AWAITING, false) && !isServiceEnabled()) {
            prefs.edit().putBoolean(PREF_AWAITING, false).putBoolean(PREF_UNLOCK_VISITED, false).apply()
        }
        render()
        checkForUpdate()
    }

    // ---------- screen ----------

    private fun render() {
        root.removeAllViews()
        val on = isServiceEnabled()
        if (on) prefs.edit().putBoolean(PREF_AWAITING, false).apply()

        root.addView(text(getString(R.string.app_name), 26f, INK, bold = true).apply {
            gravity = Gravity.CENTER
        })
        root.addView(space(20))

        updateVersion?.let { v ->
            root.addView(bigButton(getString(R.string.btn_update, v), ACCENT) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
            })
            root.addView(space(16))
        }

        if (on) renderDone() else renderSteps()

        root.addView(space(24))
        root.addView(Switch(this).apply {
            text = getString(R.string.mute_ads)
            textSize = 16f
            setTextColor(INK)
            isChecked = prefs.getBoolean("mute_ads", true)
            setPadding(px(4), px(8), px(4), px(8))
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("mute_ads", v).apply() }
        })
        root.addView(space(8))
        root.addView(text(getString(R.string.skipped_count, prefs.getInt("skipped", 0)), 15f, MUTED).apply {
            setPadding(px(4), 0, 0, 0)
        })
        root.addView(space(32))
        root.addView(text(getString(R.string.version, installedVersionName()), 13f, MUTED).apply {
            gravity = Gravity.CENTER
        })
    }

    private fun installedVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"

    private fun renderDone() {
        root.addView(card {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = "✓"
                textSize = 44f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                background = round(GREEN, 999f)
                layoutParams = LinearLayout.LayoutParams(px(88), px(88))
            })
            addView(space(16))
            addView(text(getString(R.string.done_title), 22f, INK, bold = true).apply { gravity = Gravity.CENTER })
            addView(space(8))
            addView(text(getString(R.string.done_text), 16f, MUTED).apply { gravity = Gravity.CENTER })
        })
    }

    private fun renderSteps() {
        // Блокировка «настроек с ограниченным доступом» есть с Android 13.
        // Спрашиваем у системы, снята ли она на самом деле. Если система не отвечает
        // (редкие прошивки), считаем шаг выполненным, только если человек заходил туда,
        // а неудачная попытка включить сервис снова делает шаг 1 активным (см. onResume).
        val unlocked = restrictedSettingsUnlocked()
        val needsUnlock = Build.VERSION.SDK_INT >= 33 && unlocked != true
        val unlockVisited = unlocked == null && prefs.getBoolean(PREF_UNLOCK_VISITED, false)
        var n = 1

        if (needsUnlock) {
            val active = !unlockVisited
            root.addView(step(n++, getString(R.string.step1_title), getString(R.string.step1_text),
                getString(R.string.step1_btn), active, done = unlockVisited) {
                prefs.edit().putBoolean(PREF_UNLOCK_VISITED, true).apply()
                openAppInfo()
            })
            root.addView(space(12))
        }

        val active2 = !needsUnlock || unlockVisited
        root.addView(step(if (needsUnlock) n else 0, getString(R.string.step2_title), getString(R.string.step2_text),
            getString(R.string.step2_btn), active2, done = false) {
            prefs.edit().putBoolean(PREF_AWAITING, true).apply()
            openServiceSettings()
        })
    }

    /** Карточка шага: номер, заголовок, пояснение и одна кнопка. */
    private fun step(
        number: Int, title: String, body: String, button: String,
        active: Boolean, done: Boolean, onClick: () -> Unit,
    ): View = card {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (number > 0) {
                addView(TextView(context).apply {
                    text = if (done) "✓" else number.toString()
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(if (active || done) Color.WHITE else MUTED)
                    background = round(if (done) GREEN else if (active) ACCENT else LINE, 999f)
                    layoutParams = LinearLayout.LayoutParams(px(30), px(30)).apply { marginEnd = px(12) }
                })
            }
            addView(text(title, 18f, INK, bold = true))
        })
        addView(space(8))
        addView(text(body, 15f, MUTED))
        addView(space(14))
        addView(bigButton(button, if (active) ACCENT else INK, filled = active, onClick = onClick))
    }

    // ---------- actions ----------

    /** Экран «О приложении», где в меню ⋮ есть «Разрешить настройки с ограниченным доступом». */
    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    /** Сразу страница нашего сервиса (Android 12+), иначе список с подсвеченным AdSkipper. */
    private fun openServiceSettings() {
        val component = ComponentName(this, SkipAdService::class.java)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                startActivity(Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString()))
                return
            } catch (e: Exception) {
                // на некоторых прошивках этого экрана нет — откроем общий список ниже
            }
        }
        val key = component.flattenToString()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) }))
    }

    /**
     * true — блокировки нет или она снята; false — блокировка стоит; null — система не ответила.
     * Android хранит это как разрешение (app-op) «access_restricted_settings» нашего приложения.
     */
    private fun restrictedSettingsUnlocked(): Boolean? {
        if (Build.VERSION.SDK_INT < 33) return true
        return try {
            val ops = getSystemService(AppOpsManager::class.java)
            when (ops.unsafeCheckOpNoThrow("android:access_restricted_settings", applicationInfo.uid, packageName)) {
                AppOpsManager.MODE_ALLOWED, AppOpsManager.MODE_DEFAULT -> true
                AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED -> false
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, SkipAdService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    /** Спрашивает у GitHub номер последней версии; если она новее — сверху появится кнопка «Обновить». */
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
                    updateVersion = "1.0.$latest"
                    if (!isFinishing) render()
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long {
        val info = packageManager.getPackageInfo(packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    // ---------- tiny view helpers ----------

    private fun px(v: Int) = (v * dp).toInt()

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(h))
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f)
    }

    private fun round(color: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * dp
        if (stroke != null) setStroke(px(1), stroke)
    }

    private fun card(build: LinearLayout.() -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD, 18f, LINE)
        setPadding(px(18), px(18), px(18), px(18))
        build()
    }

    private fun bigButton(label: String, color: Int, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 17f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        setTextColor(if (filled) Color.WHITE else color)
        background = if (filled) round(color, 14f) else round(Color.TRANSPARENT, 14f, LINE)
        minHeight = px(56)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56))
        setOnClickListener { onClick() }
    }
}
