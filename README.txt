حزمة تطبيق مواقيت الصلاة والأذكار (واجهة HTML + إشعارات نظام أندرويد)
==========================================================================

محتويات الحزمة:
1. app/src/main/AndroidManifest.xml
   يحتوي على الصلاحيات المطلوبة (POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM)
   وتعريف MainActivity و PrayerAlarmReceiver و BootReceiver.

2. app/src/main/java/com/example/mawaqit/MawaqitApp.kt
   يجمع:
   - PrayerScheduler: جدولة إشعارات النظام لمواقيت الصلاة وأذكار الصباح والمساء.
   - PrayerAlarmReceiver: إظهار إشعار النظام خارج التطبيق وإعادة الجدولة لليوم التالي.
   - BootReceiver: إعادة الجدولة تلقائياً بعد إعادة تشغيل الهاتف.
   - PrayerWebBridge: استقبال تحديث المواقيت والمديرية وأوقات الأذكار مباشرة من WebView.
   - MainActivity: عرض واجهة HTML داخل WebView وطلب إذن الإشعارات.

3. app/src/main/assets/
   يحتوي على index.html ومجلد audio (adhan1.m4a, adhan2.m4a) وملفات التطبيق.

طريقة الاستخدام في مشروع أندرويد:
- انسخ مجلد app داخل مشروع Android Studio (أو استبدل الملفات المطابقة في مجلد app).
- تأكد من تفعيل ViewBinding / AppCompat / Material3 في إعدادات Gradle الخاصة بالمشروع.
