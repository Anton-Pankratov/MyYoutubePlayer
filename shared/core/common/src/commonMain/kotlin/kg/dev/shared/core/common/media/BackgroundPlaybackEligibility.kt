package kg.dev.shared.core.common.media

/**
 * Policy only: whether a Direct source is eligible for future native background-audio hosts.
 * Platform support remains a separate capability.
 */
enum class DirectBackgroundEligibility {
    Eligible,
    ForegroundOnly,
}

fun directMimeBackgroundEligibility(mimeType: String?): DirectBackgroundEligibility {
    val normalized = mimeType?.trim().orEmpty()
    return if (normalized.startsWith("audio/", ignoreCase = true)) {
        DirectBackgroundEligibility.Eligible
    } else {
        DirectBackgroundEligibility.ForegroundOnly
    }
}
