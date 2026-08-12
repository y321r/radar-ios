package com.radar.news.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.radar.news.ui.Strings

/** Holds the application context for platform actions (set by the Android app shell). */
object AndroidAppContext {
    lateinit var context: Context
}

actual object ArticleActions {

    actual fun openArticle(url: String) {
        val context = AndroidAppContext.context
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        // Only ever hand http(s) to the system. A feed is remote input, and an intent:// or
        // file:// URL slipping through would be handing an arbitrary intent to the launcher.
        if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) return

        val customTabs = CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()

        try {
            customTabs.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }

    actual fun shareArticle(title: String, url: String) {
        val context = AndroidAppContext.context
        val text = buildString {
            append('«').append(title).append('»')
            append("\n\n").append(url)
            append("\n\n").append(Strings.share_via)
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }

        runCatching {
            context.startActivity(
                Intent.createChooser(send, Strings.share_chooser),
            )
        }
    }

    actual fun displayDomain(url: String): String = articleHost(url)

    actual fun openEmail(address: String) {
        val context = AndroidAppContext.context
        val uri = runCatching { Uri.parse("mailto:$address") }.getOrNull() ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
    }

    private val ALLOWED_SCHEMES = setOf("http", "https")
}
