package li.gkd.app.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.navigation.DocumentRoute
import li.gkd.app.ui.style.itemVerticalPadding
import org.jetbrains.compose.resources.stringResource

/**
 * In-app help center replacing the v7.15.14 help panel: a simple question list, every entry opens
 * a bundled local document, so it works offline and cannot confuse non-technical users.
 */
@Composable
fun HelpCenterPage() {
    val mainVm = MainViewModel.requireCurrent()
    val navigator = mainVm.navigator
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = navigator::pop,
                    )
                },
                title = { Text(text = stringResource(Res.string.help_center_title)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val topics =
                listOf(
                    LocalDocument.BackgroundRunning to stringResource(Res.string.help_q_not_skipping),
                    LocalDocument.WorkMode to stringResource(Res.string.help_q_modes),
                    LocalDocument.SnapshotHelp to stringResource(Res.string.help_q_snapshot),
                    LocalDocument.CookieHelp to stringResource(Res.string.help_q_cookie),
                    LocalDocument.Terms to stringResource(Res.string.help_q_terms),
                    LocalDocument.Privacy to stringResource(Res.string.help_q_privacy),
                )
            topics.forEach { (doc, question) ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { navigator.navigate(DocumentRoute(doc)) }
                            .padding(itemVerticalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GkIcon(
                        imageVector = GkIcons.HelpOutline,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(doc.titleResource),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            GkPageBottomSpace()
        }
    }
}
