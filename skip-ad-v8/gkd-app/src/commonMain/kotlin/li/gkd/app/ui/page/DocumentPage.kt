package li.gkd.app.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.resources.*
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkIconButton
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkScaffold
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.navigation.DocumentRoute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** In-app documents replacing the upstream web help pages. */
enum class LocalDocument(
    val titleResource: StringResource,
    val bodyResource: StringResource,
) {
    Terms(Res.string.doc_terms_title, Res.string.doc_terms_body),
    Privacy(Res.string.doc_privacy_title, Res.string.doc_privacy_body),
    WorkMode(Res.string.doc_work_mode_title, Res.string.doc_work_mode_body),
    CookieHelp(Res.string.doc_cookie_title, Res.string.doc_cookie_body),
    SnapshotHelp(Res.string.doc_snapshot_title, Res.string.doc_snapshot_body),
    BackgroundRunning(Res.string.doc_background_title, Res.string.doc_background_body),
}

@Composable
fun DocumentPage(route: DocumentRoute) {
    val mainVm = MainViewModel.requireCurrent()
    val document = route.document
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    GkScaffold(
        modifier = Modifier.gkTopAppBarScroll(scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    GkIconButton(
                        imageVector = GkIcons.ArrowBack,
                        onClick = mainVm.navigator::pop,
                    )
                },
                title = { Text(text = stringResource(document.titleResource)) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            stringResource(document.bodyResource)
                .split("\n\n")
                .forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            GkPageBottomSpace()
        }
    }
}
