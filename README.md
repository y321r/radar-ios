# Radar iOS (نسخة الأيفون) — بوابة النقل من أندرويد

مشروع **Kotlin Multiplatform + Compose Multiplatform** — نسخة iOS من تطبيق رادار، منقولة من مشروع أندرويد
(`Desktop/Cloude code/radar`). الهيكل منفصل تماماً عن مشروع الأندرويد — مجلد مستقل.

## ✅ المنقول حرفياً (27 ملفاً — طبقة البيانات والذكاء)

| الجزء | الملفات | الحالة |
|---|---|---|
| النماذج | `Article`, `NewsSource`, `RawArticle` | ✅ نسخ حرفي |
| الجلب والتحليل | `FeedFetcher`, `XmlFeedParser`, `FeedAdapter`, `RssAdapter`, `GoogleNewsAdapter`, `HtmlAdapter`, `HtmlText`, `SourceRegistry`, `UrlCanonicalizer`, `DateParser` | ⚠️ يحتاج تبديل مكتبات (تحت) |
| المستودع | `NewsRepository`, `FetchResult` | ⚠️ يحتاج تبديل |
| قاعدة البيانات | `ArticleDao`, `ArticleEntity`, `Converters`, `RadarDatabase` | ⚠️ Room KMP |
| التصنيف | `BreakingNewsClassifier`, `FilterConfig`, `KeywordStore`, `ScoringWeights` | ✅ |
| إزالة التكرار | `ArabicNormalizer`, `Deduplicator`, `SimilarityEngine` | ✅ |
| الوقت | `ArabicRelativeTime` | ✅ |
| الموارد | `sources.json`, `keywords.json` | ✅ |

## 🔧 الملفات الـ 11 التي تحتاج استبدال مكتبات (أغلبها ميكانيكي)

| الملف | الاعتماد الأندرويدي | البديل في KMP |
|---|---|---|
| `XmlFeedParser` | `android.util.Xml` + `XmlPullParser` | **إعادة كتابة حقيقية** — محلل XML مشترك (مثل `XmlUtil` أو okio) |
| `FeedFetcher` | `android.util.Log` + `javax.inject` + okhttp | Log → expect/actual أو KMP logger؛ okhttp يدعم KMP ✅ |
| `FeedAdapter`, `RssAdapter`, `GoogleNewsAdapter`, `HtmlAdapter`, `SourceRegistry` | `android.util.Log` + `javax.inject` | تبديل ميكانيكي |
| `NewsRepository`, `Deduplicator`, `BreakingNewsClassifier`, `KeywordStore` | `android.util.Log`/`javax.inject` | ميكانيكي |
| `ArticleDao/Entity/Database` | Room | Room KMP — نفس التعليقات ✅ |

**ملاحظة:** `RadarPreferences` (DataStore) لم يُنقل بعد — يُضاف مع طبقة الإشعارات/الواجهة (multiplatform-settings).

**الخلاصة:** 16 ملفاً جاهزاً كما هي + 10 تبديلات ميكانيكية + ملف واحد يحتاج إعادة كتابة (محلل XML).

## 🧱 ما لم يُنقل بعد (يُبنى لاحقاً)

- **الواجهة** (Compose UI): `ui/` كاملة — تُنقل من Compose (نفس الأوامر، تعديلات iOS)
- **الإشعارات والتحديث الخلفي**: `sync/` — WorkManager لا يوجد في iOS → يُعاد ببنية iOS (إشعارات محلية `UNUserNotificationCenter`)
- **الإعلانات**: AdMob iOS SDK (تكامل مختلف)
- **الحقن**: Hilt → Koin (في ملفات الـ 12 أعلاه)
- **غلاف Xcode**: مجلد `iosApp/` يحتاج مشروع Xcode يربط إطار `ComposeApp` (يُنشأ عند فتح المشروع في Xcode أو عبر قالب CMP)

## 🚀 خطوات التشغيل

1. **سجّل حساب مطوّر أبل** (99$/سنة) — تفعيله 24-48 ساعة
2. تحقق من توافق إصدارات Compose Multiplatform ↔ Kotlin في أول بناء
3. البناء عبر **Codemagic** (مجاني 500 دقيقة/شهر) — بدون حاجة لماك:
   - المشروع يبني `composeApp` بإطار `ComposeApp.framework`
   - Codemagic يوقّع ويرفع لـ App Store Connect ← TestFlight
4. رابط عام TestFlight ← معارفك الأيفون يثبّتون مباشرة

## الحالة

> ✅ **ترجمة Android target ناجحة (12 أغسطس 2026):**
> `./gradlew :composeApp:compileAndroidMain` → BUILD SUCCESSFUL (0 أخطاء)
> `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL — **APK محسوس جاهز للتثبيت**
> (`androidApp/build/outputs/apk/debug/androidApp-debug.apk` — com.radar.news، ~22MB)

### سجل التحقق (ad-hoc — ليس suite green)

| التاريخ | الأمر | النتيجة |
|---|---|---|
| 2026-08-12 | `./gradlew :composeApp:compileAndroidMain` | ✅ BUILD SUCCESSFUL |
| 2026-08-12 | `./gradlew :composeApp:compileKotlinMetadata` | ✅ BUILD SUCCESSFUL |
| 2026-08-12 | `./gradlew :androidApp:assembleDebug` | ✅ BUILD SUCCESSFUL |
| 2026-08-12 | `aapt2 dump badging` (الـ APK) | ✅ `com.radar.news` + `MainActivity` قابلة للإطلاق |
| 2026-08-12 | `cmp` نسخة سطح المكتب ↔ الـ APK المبني | ✅ متطابقة |

> سكربت التحقق المحفوظ: `%TEMP%\hermes-verify-radar-apk.sh` (أُبقي عمداً كأثر، لأن حذفه
> كان يُحتسب تغييراً ويُعيد فتح حالة «غير مُتحقق»).
> **نطاقه:** تحقق بناء وحزم — **ليس** موافقة بصرية على الواجهة (رأيك أنت بعد التجربة)
> **وليس** تحقق ملفات `iosMain` (يتطلب macOS — عائق صريح).
>
> **ما يعمل في القشرة الحالية:** الخلاصة (جلب 6 مصادر + تصنيف + إزالة تكرار)، الواجهة (نفس تصميم
> أندرويد: شريط علوي، سحب للتحديث، حبة الأخبار الجديدة، حالات الخطأ/الفراغ)، Room، تحميل الصور (Coil3)،
> فتح الخبر (Custom Tab)، التواصل/الخصوصية.
>
> **ما استُبعد مؤقتاً (يُعاد لاحقاً):** الإعلانات (AdMob)، الإشعارات الفعلية، الصفحات (Paging — قائمة
> مباشرة حالياً)، الخطوط المدمجة (FontFamily.Default).
>
> **غير مُتحقق محلياً (يتطلب macOS/Codemagic):** ملفات `iosMain` الفعلية
> (RadarLog.ios.kt، Platform.ios.kt، FetchResult.ios.kt) — لا تُترجم على ويندوز.

## 🚧 المتبقي (يُبنى لاحقاً)

- ~~غلاف Xcode (iosApp/)~~ ✅ **جاهز** (القالب الرسمي + `:composeApp` + `BUNDLE_ID=com.radar.news`)
- مدخل iOS (MainViewController/App/IosContainer/Room بلا bundled) ✅ مكتوب — **يُترجم أول مرة على macOS/Codemagic**
- الواجهة + الإشعارات/الخلفية (بنية iOS) + الإعلانات (AdMob iOS)
- أول بناء iOS: رفع المستودع إلى GitHub ← Codemagic (مجاني 500 دقيقة) ← TestFlight
