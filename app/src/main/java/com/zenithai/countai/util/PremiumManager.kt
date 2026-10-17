package com.zenithai.countai.util

import com.zenithai.countai.BuildConfig

/**
 * Freemium feature flags.
 *
 * Basic counting stays free forever. Premium features (video counting,
 * difference detection, advanced reports, dashboard, cloud history) are
 * unlocked in DEBUG builds so everything can be tested; in release builds
 * they are locked until a real purchase flow exists.
 *
 * The UI shows a small "Premium" tag on locked features instead of a
 * frustrating paywall — the free version is never crippled.
 */
object PremiumManager {

    enum class Feature {
        VIDEO_COUNT,
        DIFFERENCE_DETECTOR,
        ADVANCED_REPORTS,
        BUSINESS_DASHBOARD,
        CLOUD_HISTORY,
    }

    /** True in debug builds; in release, consult the (future) purchase state. */
    fun isUnlocked(feature: Feature): Boolean {
        if (BuildConfig.DEBUG) return true
        // TODO: check real purchase state (Play Billing) here when it exists.
        return false
    }
}
