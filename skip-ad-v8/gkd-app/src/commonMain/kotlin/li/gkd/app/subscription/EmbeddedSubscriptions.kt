package li.gkd.app.subscription

/**
 * Bundled community subscriptions. Seeded into local storage on first launch (content read from
 * packaged assets) and afterwards kept current by the regular subscription update machinery.
 *
 * Selection: the three most starred gkd subscription sources that are still actively maintained
 * (checked 2026-11): Lin-arm/GKD_subscription 7.6k stars (daily updates), aoguai/subscription
 * 1.1k stars, ganlinte/GKD-subscription 0.9k stars.
 */
object EmbeddedSubscriptions {
    data class Seed(val id: Long, val updateUrl: String)

    val seeds =
        listOf(
            Seed(
                id = 667L,
                updateUrl = "https://gkd-subscription-667.pages.dev/gkd.json5",
            ),
            Seed(
                id = 86L,
                updateUrl =
                    "https://cdn.jsdelivr.net/gh/aoguai/subscription@custom/dist/aoguai_gkd.json5",
            ),
            Seed(
                id = 233L,
                updateUrl =
                    "https://registry.npmmirror.com/@ganlinte/gkd-subscription/latest/files/dist/ganlin_gkd.json5",
            ),
        )

    val ids: Set<Long> = seeds.mapTo(mutableSetOf()) { it.id }
}
