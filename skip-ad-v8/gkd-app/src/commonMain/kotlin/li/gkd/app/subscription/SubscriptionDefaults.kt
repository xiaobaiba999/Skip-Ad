package li.gkd.app.subscription

import li.gkd.app.resources.*
import org.jetbrains.compose.resources.getString

/** Names used only when initialization creates missing built-in subscription files. */
data class SubscriptionDefaults(
    val localName: String,
    val memoryName: String,
    /** Bundled subscription content by id, used to seed embedded remote subscriptions. */
    val embeddedContents: Map<Long, String> = emptyMap(),
) {
    companion object {
        suspend fun fromResources(): SubscriptionDefaults =
            SubscriptionDefaults(
                localName = getString(Res.string.subscription_local),
                memoryName = getString(Res.string.subscription_memory),
            )
    }
}
