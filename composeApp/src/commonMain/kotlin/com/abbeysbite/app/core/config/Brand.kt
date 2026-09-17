package com.abbeysbite.app.core.config

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for branding. Changing the product name, accent
 * color, or core copy should only ever require edits in this file (plus the
 * platform launcher icons / display-name entries listed in README).
 */
object Brand {
    const val appName: String = "Abbey’s Bite"
    const val tagline: String = "Eat what you love. Add what helps."

    /** Shown on share cards and other outbound surfaces. */
    const val shareFooter: String = "Made with Abbey’s Bite"

    /** Restrained accent — a deep, calm green. */
    val accent = Color(0xFF2E7D5B)
    val accentSoft = Color(0xFFDCEDE4)
    val accentDark = Color(0xFF6FBF9A)      // accent adapted for dark surfaces
    val accentSoftDark = Color(0xFF1E3A2E)

    /** Nutrient identity colors — gentle, never alarming. */
    val protein = Color(0xFF5B7DB1)
    val fibre = Color(0xFF7A9B57)
    val healthyFat = Color(0xFFC79A3B)

    // Legal/support URLs are configuration (PRIVACY_POLICY_URL etc.) surfaced
    // through LegalContent — full canonical page text is bundled in-app and
    // exported to `legal/` for hosting. No placeholder domains anywhere.

    object Copy {
        const val improveHeading = "What are you eating?"
        const val improveSubtitle = "Snap your meal and we’ll find easy ways to make it more satisfying."
        const val orTellMe = "Or just tell me."
        const val yourPlate = "Your plate"
        const val easyAdditions = "Easy additions"
        const val somethingWrong = "Something wrong?"
        const val useWhatIHave = "Use what I have"
        const val yourRhythm = "Your rhythm"
        const val estimateDisclaimer =
            "Estimates only — photo-based analysis is approximate and not medical or dietary advice."
        const val allergenDisclaimer =
            "We can’t guarantee allergen safety. Always verify ingredients yourself, especially for severe allergies."
    }
}
