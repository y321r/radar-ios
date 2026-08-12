package com.radar.news.ui

import com.radar.news.ui.Strings
/**
 * KMP shell replacement for Android string resources — the Android project's `values/strings.xml`
 * verbatim (Arabic-only, so it lives in the default `values/` there). Kept as plain constants
 * so the shared UI compiles without a resource pipeline; platform shells may later move these
 * into real localized resources.
 */
object Strings {
    const val app_name = "رادار"

    // top bar
    const val brand = "رادار"
    const val live = "مباشر"
    const val cd_notifications_on = "إيقاف إشعارات الأخبار العاجلة"
    const val cd_notifications_off = "تفعيل إشعارات الأخبار العاجلة"
    const val cd_pulse_logo = "شعار رادار"

    // feed states
    const val empty_title = "لا توجد أخبار عاجلة حالياً"
    const val empty_subtitle = "اسحب للأسفل للتحديث"
    const val error_title = "تعذّر تحديث الأخبار"
    const val error_retry = "إعادة المحاولة"
    const val error_strip_dismiss = "إخفاء"

    // article row
    const val cd_share = "مشاركة الخبر"
    const val cd_open_source = "فتح الخبر من المصدر"
    const val share_via = "عبر تطبيق رادار"
    const val share_chooser = "مشاركة عبر"
    /** e.g. "+2 مصادر" under a deduplicated item — use String.format(Strings.extra_sources, n) */
    const val extra_sources = "+%1\$d مصادر"

    // "new posts" pill — Arabic plural categories; use String.format(..., count)
    const val new_posts_one = "خبر جديد"
    const val new_posts_two = "خبران جديدان"
    const val new_posts_few = "%1\$d أخبار جديدة"
    const val new_posts_many = "%1\$d خبراً جديداً"
    const val cd_scroll_to_top = "الانتقال إلى أحدث الأخبار"

    // ads
    const val ad_badge = "إعلان"

    // onboarding
    const val onboarding_title = "هل تريد تلقي إشعارات الأخبار العاجلة؟"
    const val onboarding_body = "سنرسل لك تنبيهاً فورياً عند ورود خبر عاجل سياسي أو اقتصادي."
    const val onboarding_accept = "موافق"
    const val onboarding_later = "لاحقاً"

    // notifications
    const val notif_channel_name = "الأخبار العاجلة"
    const val notif_channel_description = "تنبيهات فورية عند ورود خبر عاجل سياسي أو اقتصادي."
    const val notif_summary = "%1\$d أخبار عاجلة جديدة"

    // contact us (News and Magazines policy requirement)
    const val cd_contact = "التواصل معنا"
    const val contact_title = "تواصل معنا"
    const val contact_subtitle = "لأي استفسار أو ملاحظة، راسلنا مباشرة"
    const val contact_email = "lyxlabs.support@gmail.com"
    const val contact_send = "إرسال بريد إلكتروني"
    const val contact_privacy = "سياسة الخصوصية"
    const val contact_privacy_url = "https://y321r.github.io/radar-privacy/"
    const val contact_developer = "مطوّر التطبيق: Lyx Labs"
}
