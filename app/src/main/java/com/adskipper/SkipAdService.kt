package com.adskipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Следит за окнами YouTube и нажимает кнопку «Пропустить рекламу», как только она появляется.
 * Опционально выключает звук, пока идёт реклама.
 */
class SkipAdService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipper"
        private const val YT = "com.google.android.youtube"

        // Известные id кнопки пропуска в разных версиях YouTube
        private val SKIP_IDS = listOf(
            "$YT:id/skip_ad_button",
            "$YT:id/modern_skip_ad_text",
            "$YT:id/skip_ad_button_text",
            "$YT:id/skip_ad_button_container",
        )

        // Признаки того, что сейчас идёт реклама (id элементов в разных версиях YouTube)
        private val AD_IDS = listOf(
            "$YT:id/ad_progress_text",
            "$YT:id/ad_countdown",
            "$YT:id/ad_badge",
            "$YT:id/ad_text",
            "$YT:id/ad_attribution",
            "$YT:id/ad_info_button",
            "$YT:id/ad_cta_button",
            "$YT:id/player_learn_more_button",
            "$YT:id/visit_advertiser",
            "$YT:id/skip_ad_countdown",
        )

        // Метка рекламы в плеере на разных языках: «Реклама», «Ad», «Sponsored»…
        private const val AD_WORDS = "Реклама|Ad|Ads|Sponsored|Anzeige|Werbung|Annonce|Publicité|Anuncio|" +
            "Anúncio|Reklam|Reklāma|Reklama|Reklaam"

        // Чтобы не путать с пометкой «Реклама» в ленте, считаем рекламой только метку,
        // за которой идёт таймер или номер ролика: «Реклама · 0:15», «Ad 1 of 2», «Реклама 1 из 2».
        private val AD_LABEL = Regex(
            "^($AD_WORDS)(?![\\p{L}\\p{N}]).{0,12}(\\d{1,2}:\\d{2}|\\d+\\s*(of|из|з|von|de|sur|/|no|iš)\\s*\\d+)",
            RegexOption.IGNORE_CASE
        )

        // Запасной вариант: поиск по тексту (разные языки интерфейса)
        private val SKIP_TEXTS = listOf(
            "Пропустить", "Пропустить рекламу", "Skip", "Skip ad", "Skip ads",
            "Пропустити", "Пропустити рекламу", "Überspringen", "Werbung überspringen",
            "Passer", "Ignorer", "Saltar", "Omitir", "Saltar anuncio", "Pular", "Pular anúncio",
            "Ignorar", "Atla", "Reklamı atla", "Izlaist", "Izlaist reklāmu", "Praleisti",
            "Praleisti reklamą", "Jäta vahele", "Salta", "Pomiń",
        )

        @Volatile
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastClick = 0L
    private var mutedByUs = false
    private var polling = false

    // Пока реклама на экране — проверяем каждые 500 мс, даже если событий нет
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (scan()) handler.postDelayed(this, 300) else polling = false
        }
    }

    override fun onServiceConnected() {
        isRunning = true
        Log.i(TAG, "Сервис запущен")
        // Человек только что включил сервис из нашего экрана — сразу вернём его в приложение
        // (там будет «Всё работает»), чтобы не искать дорогу назад через настройки.
        val p = prefs()
        if (p.getBoolean(MainActivity.PREF_AWAITING, false)) {
            p.edit().putBoolean(MainActivity.PREF_AWAITING, false).apply()
            try {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось вернуться в приложение", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != YT) return
        val now = System.currentTimeMillis()
        if (now - lastScan < 150) return   // не нагружаем процессор на каждом событии
        lastScan = now
        if (scan() && !polling) {
            polling = true
            handler.postDelayed(pollRunnable, 500)
        }
    }

    /** @return true, если на экране сейчас реклама */
    private fun scan(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != YT) {
            updateMute(false)
            return false
        }

        val skipNode = findSkipButton(root)
        // Реклама видна сразу с первой секунды — по меткам и таймеру, не дожидаясь,
        // пока появится кнопка «Пропустить» (она появляется только через ~5 секунд).
        val adVisible = skipNode != null ||
            AD_IDS.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } } ||
            hasAdLabel(root)

        if (skipNode != null) clickNode(skipNode)
        updateMute(adVisible)
        return adVisible
    }

    /** Обходит экран и ищет метку вида «Реклама · 0:15». Ограничен по числу элементов, чтобы не тормозить. */
    private fun hasAdLabel(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 600) {
            val n = queue.removeFirst()
            seen++
            for (s in arrayOf(n.text, n.contentDescription)) {
                val t = s?.toString()?.trim() ?: continue
                if (t.length in 2..60 && AD_LABEL.containsMatchIn(t) && n.isVisibleToUser) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun findSkipButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in SKIP_IDS) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (text in SKIP_TEXTS) {
            root.findAccessibilityNodeInfosByText(text).firstOrNull { node ->
                val t = (node.text ?: node.contentDescription)?.toString()?.trim() ?: ""
                // только точное совпадение — чтобы не нажать на видео со словом «Skip» в названии
                node.isVisibleToUser && SKIP_TEXTS.any { it.equals(t, ignoreCase = true) }
            }?.let { return it }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastClick < 1000) return
        lastClick = now

        // 1) Обычный клик по элементу или его кликабельному родителю
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 5) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onSkipped(); return
            }
            n = n.parent; depth++
        }
        // 2) Запасной вариант — тап жестом по центру кнопки
        val r = Rect().also { node.getBoundsInScreen(it) }
        if (!r.isEmpty) tap(r.exactCenterX(), r.exactCenterY())
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onSkipped()
        }, null)
    }

    private fun onSkipped() {
        val p = prefs()
        p.edit().putInt("skipped", p.getInt("skipped", 0) + 1).apply()
        Log.i(TAG, "Реклама пропущена")
    }

    private fun updateMute(adVisible: Boolean) {
        val wantMute = adVisible && prefs().getBoolean("mute_ads", true)
        if (wantMute == mutedByUs) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (wantMute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        }
        mutedByUs = wantMute
    }

    private fun prefs() = getSharedPreferences("adskipper", Context.MODE_PRIVATE)

    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        updateMute(false)   // вернуть звук, если сервис выключили во время рекламы
        super.onDestroy()
    }
}
