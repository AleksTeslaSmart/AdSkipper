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
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Следит за окнами YouTube и нажимает кнопку «Пропустить рекламу», как только она появляется.
 * Опционально выключает звук, пока идёт реклама.
 *
 * Экономия батареи:
 *  - экран проверяется не чаще 1 раза в секунду (во время рекламы — 2 раза, чтобы быстрее нажать «Пропустить»);
 *  - события от YouTube не обрабатываются по одному: сколько бы их ни пришло, будет одна проверка;
 *  - экран целиком не перебирается: YouTube сам ищет нужные элементы по id и по тексту (один запрос на слово);
 *  - ищутся только слова на языке телефона и на английском, а не на всех 11 языках.
 */
class SkipAdService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipper"
        private const val YT = "com.google.android.youtube"

        private const val IDLE_INTERVAL_MS = 1000L   // обычная проверка: 1 раз в секунду
        private const val AD_INTERVAL_MS = 500L      // пока идёт реклама

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

        // Все варианты надписи на кнопке пропуска — для точного сравнения
        private val SKIP_TEXTS = listOf(
            "Пропустить", "Пропустить рекламу", "Skip", "Skip ad", "Skip ads",
            "Пропустити", "Пропустити рекламу", "Überspringen", "Werbung überspringen",
            "Passer", "Ignorer", "Saltar", "Omitir", "Saltar anuncio", "Pular", "Pular anúncio",
            "Ignorar", "Atla", "Reklamı atla", "Izlaist", "Izlaist reklāmu", "Praleisti",
            "Praleisti reklamą", "Jäta vahele", "Salta", "Pomiń",
        )

        // Что искать на каждом языке: короткое слово находит и «Пропустить», и «Пропустить рекламу»
        private val SKIP_SEARCH = mapOf(
            "ru" to listOf("Пропустить"), "uk" to listOf("Пропустити"), "de" to listOf("Überspringen"),
            "fr" to listOf("Passer", "Ignorer"), "es" to listOf("Saltar", "Omitir"), "pt" to listOf("Pular", "Ignorar"),
            "tr" to listOf("atla"), "lv" to listOf("Izlaist"), "lt" to listOf("Praleisti"), "et" to listOf("Jäta vahele"),
            "it" to listOf("Salta"), "pl" to listOf("Pomiń"),
        )
        private val AD_SEARCH = mapOf(
            "ru" to listOf("Реклама"), "uk" to listOf("Реклама"), "de" to listOf("Anzeige", "Werbung"),
            "fr" to listOf("Annonce", "Publicité"), "es" to listOf("Anuncio"), "pt" to listOf("Anúncio"),
            "tr" to listOf("Reklam"), "lv" to listOf("Reklāma"), "lt" to listOf("Reklama"), "et" to listOf("Reklaam"),
        )

        @Volatile
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScan = 0L
    private var lastClick = 0L
    private var mutedByUs = false
    private var scanPending = false
    private var adActive = false

    // Слова для поиска: язык телефона + английский (YouTube обычно говорит на языке телефона)
    private lateinit var skipWords: List<String>
    private lateinit var adWords: List<String>

    private val scanRunnable = Runnable {
        scanPending = false
        lastScan = SystemClock.uptimeMillis()
        adActive = scan()
        // Пока реклама на экране, проверяем дальше сами, даже если YouTube молчит
        if (adActive) schedule(AD_INTERVAL_MS)
    }

    override fun onServiceConnected() {
        isRunning = true
        val lang = Locale.getDefault().language
        skipWords = (SKIP_SEARCH[lang] ?: emptyList()) + "Skip"
        adWords = (AD_SEARCH[lang] ?: emptyList()) + listOf("Ad", "Sponsored")
        Log.i(TAG, "Сервис запущен ($lang)")
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
        // Все события до ближайшей проверки сливаются в одну проверку
        schedule(if (adActive) AD_INTERVAL_MS else IDLE_INTERVAL_MS)
    }

    /** Ставит одну проверку не раньше, чем через [interval] после предыдущей. */
    private fun schedule(interval: Long) {
        if (scanPending) return
        scanPending = true
        val wait = (lastScan + interval - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(scanRunnable, wait)
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
        // Проверки идут от дешёвых к дорогим и останавливаются на первой сработавшей.
        val adVisible = skipNode != null ||
            AD_IDS.any { id -> root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser } } ||
            hasAdLabel(root)

        if (skipNode != null) clickNode(skipNode)
        updateMute(adVisible)
        return adVisible
    }

    /** Ищет метку вида «Реклама · 0:15»: YouTube сам находит элементы с этим словом, мы только проверяем текст. */
    private fun hasAdLabel(root: AccessibilityNodeInfo): Boolean {
        for (word in adWords) {
            for (n in root.findAccessibilityNodeInfosByText(word)) {
                for (s in arrayOf(n.text, n.contentDescription)) {
                    val t = s?.toString()?.trim() ?: continue
                    if (t.length in 2..60 && AD_LABEL.containsMatchIn(t) && n.isVisibleToUser) return true
                }
            }
        }
        return false
    }

    /**
     * Ищет кнопку «Пропустить», которую уже можно нажать.
     * Нажимаем только то, на чём действительно написано «Пропустить»/«Skip»: по одному id
     * YouTube может держать скрытую или ещё не готовую кнопку, и нажатие «вслепую»
     * попадало по соседним кнопкам плеера (например, по субтитрам «CC»).
     */
    private fun findSkipButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in SKIP_IDS) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { n ->
                n.isVisibleToUser && n.isEnabled && hasSkipText(n, depth = 2)
            }?.let { return it }
        }
        for (word in skipWords) {
            root.findAccessibilityNodeInfosByText(word).firstOrNull { n ->
                // только точное совпадение — чтобы не нажать на видео или субтитры со словом «Skip»,
                // и только если это сама кнопка или текст прямо на ней
                n.isVisibleToUser && isSkipText(n) && (n.isClickable || n.parent?.isClickable == true)
            }?.let { return it }
        }
        return null
    }

    private fun isSkipText(n: AccessibilityNodeInfo): Boolean {
        val t = (n.text ?: n.contentDescription)?.toString()?.trim() ?: return false
        return SKIP_TEXTS.any { it.equals(t, ignoreCase = true) }
    }

    /** Есть ли надпись «Пропустить» на самом элементе или на его детях (кнопка = рамка + текст внутри). */
    private fun hasSkipText(n: AccessibilityNodeInfo, depth: Int): Boolean {
        if (isSkipText(n)) return true
        if (depth == 0) return false
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            if (hasSkipText(c, depth - 1)) return true
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        val now = SystemClock.uptimeMillis()
        if (now - lastClick < 1000) return
        lastClick = now

        // 1) Обычный клик по самой кнопке или её ближайшему родителю (не выше — выше уже плеер целиком)
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 2) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onSkipped(); return
            }
            n = n.parent; depth++
        }
        // 2) Запасной вариант — тап по центру кнопки, но только если она похожа на кнопку «Пропустить»:
        //    небольшая и не в верхней четверти экрана (там CC, настройки и трансляция).
        val r = Rect().also { node.getBoundsInScreen(it) }
        val screenH = resources.displayMetrics.heightPixels
        val screenW = resources.displayMetrics.widthPixels
        val looksRight = !r.isEmpty && r.height() < screenH / 4 && r.width() < screenW / 2 && r.top > screenH / 4
        if (looksRight) tap(r.exactCenterX(), r.exactCenterY())
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
