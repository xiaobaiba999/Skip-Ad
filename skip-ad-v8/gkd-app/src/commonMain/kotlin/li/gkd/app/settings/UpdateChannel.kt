package li.gkd.app.settings

import li.gkd.app.model.Option
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Explicit values are part of the persisted settings contract. The stable channel fetches the
 * update manifest from the project repository; the beta channel was removed with the manifest
 * migration.
 */
enum class UpdateChannel(
    override val value: Int,
    override val labelResource: StringResource,
) : Option<Int> {
    Stable(
        0,
        Res.string.update_channel_stable,
    );

    override val options
        get() = entries
}
