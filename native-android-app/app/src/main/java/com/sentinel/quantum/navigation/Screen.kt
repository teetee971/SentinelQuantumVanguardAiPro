package com.sentinel.quantum.navigation

import android.net.Uri
import androidx.annotation.StringRes
import com.sentinel.quantum.R

sealed class Screen(val route: String, @StringRes val titleRes: Int) {
    object Home : Screen("home", R.string.nav_home)
    object OsintFeed : Screen("osint_feed", R.string.nav_osint)
    object SecurityAudit : Screen("security_audit", R.string.nav_audit)
    object LocalLogs : Screen("local_logs", R.string.nav_logs)
    object PhoneSecurity : Screen("phone_security", R.string.phone_security_title)
    object CallBlocking : Screen("call_blocking", R.string.call_blocking_title)
    object CallFilterHistory : Screen("call_filter_history", R.string.call_history_title)
    object EmailSecurity : Screen("email_security", R.string.email_security_title)
    object SmsScanner : Screen("sms_scanner", R.string.sms_scanner_title)
    object AppPermissionAnalyzer : Screen("app_permission_analyzer", R.string.permission_analyzer_title)
    object NetworkSurveillance : Screen("network_surveillance", R.string.network_surveillance_title)
    object About : Screen("about", R.string.nav_about)
    object Compliance : Screen("compliance", R.string.nav_compliance)
    object Settings : Screen("settings", R.string.nav_settings)

    object OsintDetail : Screen("osint_detail/{$ARG_ITEM_ID}", R.string.osint_detail_title) {
        /** Builds a concrete route; the identifier is a URL, so it must be encoded. */
        fun createRoute(itemId: String): String = "osint_detail/${Uri.encode(itemId)}"
    }

    companion object {
        const val ARG_ITEM_ID = "itemId"
    }
}
