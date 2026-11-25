package com.example.mawaqit

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

data class ScheduledAlert(
    val id: Int,
    val title: String,
    val body: String,
    val hour: Int,
    val minute: Int,
    val actionType: String
)

object PrayerScheduler {
    fun defaultAlerts(cityName: String = "تعز"): List<ScheduledAlert> = listOf(
        ScheduledAlert(101, "حان الآن موعد أذان الفجر", "مواقيت الصلاة — $cityName", 4, 55, "prayer"),
        ScheduledAlert(102, "أذكار الصباح", "حان وقت أذكار الصباح — اضغط لقراءتها", 6, 0, "morning"),
        ScheduledAlert(103, "حان الآن موعد أذان الظهر", "مواقيت الصلاة — $cityName", 11, 55, "prayer"),
        ScheduledAlert(104, "حان الآن موعد أذان العصر", "مواقيت الصلاة — $cityName", 15, 10, "prayer"),
        ScheduledAlert(105, "أذكار المساء", "حان وقت أذكار المساء — اضغط لقراءتها", 17, 0, "evening"),
        ScheduledAlert(106, "حان الآن موعد أذان المغرب", "مواقيت الصلاة — $cityName", 17, 45, "prayer"),
        ScheduledAlert(107, "حان الآن موعد أذان العشاء", "مواقيت الصلاة — $cityName", 19, 0, "prayer")
    )

    fun scheduleAll(context: Context, alerts: List<ScheduledAlert> = defaultAlerts()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (alert in alerts) {
            val triggerCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alert.hour)
                set(Calendar.MINUTE, alert.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                putExtra("alert_id", alert.id)
                putExtra("title", alert.title)
                putExtra("body", alert.body)
                putExtra("action_type", alert.actionType)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, alert.id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pendingIntent)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCal.timeInMillis, pendingIntent)
            }
        }
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    companion object { const val CHANNEL_ID = "prayer_alerts_channel" }

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getIntExtra("alert_id", 100)
        val title = intent.getStringExtra("title") ?: "مواقيت الصلاة والأذكار"
        val body = intent.getStringExtra("body") ?: "حان وقت التنبيه"
        val actionType = intent.getStringExtra("action_type") ?: "prayer"

        createNotificationChannel(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action_type", actionType)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, alertId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val hasNotifPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasNotifPermission) {
            NotificationManagerCompat.from(context).notify(alertId, notification)
        }
        PrayerScheduler.scheduleAll(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID, "تنبيهات الصلاة والأذكار", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات مواقيت الصلاة وأذكار الصباح والمساء خارج التطبيق"
                enableVibration(true)
                setSound(soundUri, audioAttrs)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            PrayerScheduler.scheduleAll(context)
        }
    }
}

class PrayerWebBridge(private val context: Context) {

    @JavascriptInterface
    fun sendTestNotificationNow() {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("alert_id", 999)
            putExtra("title", "مواقيت الصلاة والأذكار")
            putExtra("body", "إشعار تجريبي من نظام أندرويد خارج التطبيق")
            putExtra("action_type", "prayer")
        }
        context.sendBroadcast(intent)
    }

    @JavascriptInterface
    fun updateScheduleFromWeb(
        cityName: String,
        fajrMin: Int,
        dhuhrMin: Int,
        asrMin: Int,
        maghribMin: Int,
        ishaMin: Int,
        morningMin: Int,
        eveningMin: Int,
        azkarEnabled: Boolean
    ) {
        val list = mutableListOf(
            ScheduledAlert(101, "حان الآن موعد أذان الفجر", "مواقيت الصلاة — $cityName", fajrMin / 60, fajrMin % 60, "prayer"),
            ScheduledAlert(103, "حان الآن موعد أذان الظهر", "مواقيت الصلاة — $cityName", dhuhrMin / 60, dhuhrMin % 60, "prayer"),
            ScheduledAlert(104, "حان الآن موعد أذان العصر", "مواقيت الصلاة — $cityName", asrMin / 60, asrMin % 60, "prayer"),
            ScheduledAlert(106, "حان الآن موعد أذان المغرب", "مواقيت الصلاة — $cityName", maghribMin / 60, maghribMin % 60, "prayer"),
            ScheduledAlert(107, "حان الآن موعد أذان العشاء", "مواقيت الصلاة — $cityName", ishaMin / 60, ishaMin % 60, "prayer")
        )
        if (azkarEnabled) {
            list.add(ScheduledAlert(102, "أذكار الصباح", "حان وقت أذكار الصباح — اضغط لقراءتها", morningMin / 60, morningMin % 60, "morning"))
            list.add(ScheduledAlert(105, "أذكار المساء", "حان وقت أذكار المساء — اضغط لقراءتها", eveningMin / 60, eveningMin % 60, "evening"))
        }
        PrayerScheduler.scheduleAll(context, list)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            PrayerScheduler.scheduleAll(this)
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(PrayerWebBridge(this), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        PrayerScheduler.scheduleAll(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
