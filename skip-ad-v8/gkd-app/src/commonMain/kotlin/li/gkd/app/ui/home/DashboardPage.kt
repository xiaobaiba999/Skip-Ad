package li.gkd.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import li.gkd.app.META
import li.gkd.app.app.AppInfoRepository
import li.gkd.app.model.findOption
import li.gkd.app.network.AppLinks
import li.gkd.app.resources.*
import li.gkd.app.rule.RuleGroupSummary
import li.gkd.app.rule.ruleGroupState
import li.gkd.app.settings.AutomatorMode
import li.gkd.app.settings.SettingsRepository
import li.gkd.app.state.Loadable
import li.gkd.app.subscription.SubscriptionRepository
import li.gkd.app.ui.MainViewModel
import li.gkd.app.ui.component.GkGroupNameText
import li.gkd.app.ui.component.GkIcon
import li.gkd.app.ui.component.GkIcons
import li.gkd.app.ui.component.GkPageBottomSpace
import li.gkd.app.ui.component.GkPermissionRestrictionDialog
import li.gkd.app.ui.component.GkRuleStats
import li.gkd.app.ui.component.GkRuleStatsData
import li.gkd.app.ui.component.GkRotatingLoadingIcon
import li.gkd.app.ui.component.GkSwitch
import li.gkd.app.ui.component.GkTooltipIconButtonBox
import li.gkd.app.ui.component.GkTopAppBar
import li.gkd.app.ui.component.gkTopAppBarScroll
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.component.textSize
import li.gkd.app.ui.icon.GkPrivilegeConnectionIcon
import li.gkd.app.ui.navigation.ActionLogRoute
import li.gkd.app.ui.navigation.ActivityLogRoute
import li.gkd.app.ui.navigation.AppConfigRoute
import li.gkd.app.ui.navigation.HelpCenterRoute
import li.gkd.app.ui.navigation.KeepAliveRoute
import li.gkd.app.ui.navigation.PrivilegeServiceRoute
import li.gkd.app.ui.navigation.WorkModeRoute
import li.gkd.app.ui.platform.LocalUiHost
import li.gkd.app.ui.platform.ignoresBatteryOptimizations
import li.gkd.app.ui.platform.setStatusServiceEnabled
import li.gkd.app.ui.platform.switchAutomator
import li.gkd.app.ui.share.launchUi
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.db.RuleGroupType
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun dashboardSummary(groups: RuleGroupSummary): String? =
    listOfNotNull(
            if (groups.globalGroups.isNotEmpty())
                stringResource(
                    Res.string.subscription_global_count,
                    groups.globalGroups.size.toString(),
                )
            else null,
            if (groups.appGroupSize > 0)
                stringResource(
                    Res.string.subscription_app_rule_counts,
                    groups.appSize.toString(),
                    groups.appGroupSize.toString(),
                )
            else null,
        )
        .joinToString("/")
        .takeIf { it.isNotEmpty() }

@Composable
fun dashboardPage(
    vm: HomeViewModel,
    latestState: Loadable<LatestActionSnapshot>,
): ScaffoldExt {
    val host = LocalUiHost.current
    val mainVm = MainViewModel.requireCurrent()
    val scope = rememberCoroutineScope()
    val appName = META.appName
    val store by SettingsRepository.settings.collectAsStateWithLifecycle()
    val platform = dashboardPlatformState()
    val showRestrictionDetails by vm.showRestrictionDetails.collectAsStateWithLifecycle()
    if (showRestrictionDetails) {
        GkPermissionRestrictionDialog(
            privilegeAvailable = platform.privilegeAvailable,
            capabilities = platform.privilegeCapabilities,
            appRestrictions = platform.appRestrictions,
            onDismiss = { vm.setShowRestrictionDetails(false) },
            onPrivilege = { mainVm.navigator.navigate(PrivilegeServiceRoute) },
        )
    }
    val rules by ruleGroupState.collectAsStateWithLifecycle()
    val actionCount by SettingsRepository.actionCount.collectAsStateWithLifecycle()
    val appCatalog by AppInfoRepository.state.collectAsStateWithLifecycle()
    val subscriptions by SubscriptionRepository.snapshotFlow.collectAsStateWithLifecycle()
    val latest = latestState.value?.record
    val scroll = rememberColumnScrollState()
    ResetPageScrollOnRequest(vm.homeState, BottomNavItem.Dashboard, scroll::resetScrollAndAwait)
    val privilegeLabel = stringResource(platform.privilegeStatus.labelResource)
    val openPrivilege = stringResource(Res.string.privilege_service_open)
    val openHomepageLabel = stringResource(Res.string.homepage_open)
    val connected = platform.privilegeStatus == DashboardPrivilegeStatus.Connected
    val warningTitle =
        when {
            connected && platform.privilegeCapabilities?.restricted == true ->
                Res.string.adb_permission_restricted
            !connected && platform.restricted -> Res.string.app_permission_restricted
            else -> null
        }
    // Keep the outgoing text until the exit transition finishes.
    var retainedWarningTitle by remember { mutableStateOf(warningTitle) }
    if (warningTitle != null) retainedWarningTitle = warningTitle
    val openRestrictionDetails = stringResource(Res.string.restriction_details_open)
    return ScaffoldExt(
        BottomNavItem.Dashboard,
        modifier = Modifier.gkTopAppBarScroll(scroll.scrollBehavior),
        topBar = {
            GkTopAppBar(
                scrollBehavior = scroll.scrollBehavior,
                title = { Text(appName) },
                actions = {
                    GkTooltipIconButtonBox(openHomepageLabel) {
                        IconButton(
                            onClick = { mainVm.navigator.openWebPage(AppLinks.Home) },
                            modifier =
                                Modifier.semantics {
                                    onClick(
                                        label = openHomepageLabel,
                                        action = null,
                                    )
                                },
                        ) {
                            GkIcon(
                                imageVector = GkIcons.OpenInNew,
                                contentDescription = openHomepageLabel,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    GkTooltipIconButtonBox(privilegeLabel) {
                        IconButton(
                            onClick = { mainVm.navigator.navigate(PrivilegeServiceRoute) },
                            modifier =
                                Modifier.semantics {
                                    onClick(
                                        label = openPrivilege,
                                        action = null,
                                    )
                                },
                        ) {
                            GkPrivilegeConnectionIcon(
                                connected = connected,
                                contentDescription = privilegeLabel,
                                tint =
                                    if (
                                        platform.privilegeStatus ==
                                            DashboardPrivilegeStatus.DisconnectedDesired
                                    )
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.verticalScroll(scroll.scrollState)
                .padding(padding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2),
        ) {
            Column {
                AnimatedVisibility(
                    visible = warningTitle != null,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    DashboardWarningCard(
                        title = stringResource(requireNotNull(retainedWarningTitle)),
                        description =
                            stringResource(
                                if (retainedWarningTitle == Res.string.adb_permission_restricted)
                                    Res.string.adb_restricted_privilege_notice
                                else Res.string.permission_restricted_privilege_notice,
                            ),
                        onClickLabel = openRestrictionDetails,
                        onClick = { vm.setShowRestrictionDetails(true) },
                    )
                }
                if (ignoresBatteryOptimizations() == false) {
                    DashboardWarningCard(
                        title = stringResource(Res.string.battery_optimization_ignore),
                        description =
                            stringResource(Res.string.battery_optimization_dashboard_notice),
                        onClickLabel = stringResource(Res.string.keep_alive_strategy_open),
                        onClick = { mainVm.navigator.navigate(KeepAliveRoute) },
                    )
                }
                GuardianHeroCard(
                    statusText = stringResource(platform.subtitle(store)),
                    running = platform.serviceEnabled,
                    actionCount = actionCount,
                    onToggle = { enabled ->
                        when (
                            val route =
                                platform.authorizationRoute(
                                    enabled,
                                    store,
                                )
                        ) {
                            PrivilegeServiceRoute -> vm.setShowRestrictionDetails(true)
                            null -> switchAutomator()
                            else -> mainVm.navigator.navigate(route)
                        }
                    },
                    mode = stringResource(
                        AutomatorMode.entries.findOption(store.automatorMode).labelResource,
                    ),
                    onModeClick = { mainVm.navigator.navigate(WorkModeRoute) },
                )
            }
            PageSwitchItemCard(
                GkIcons.Notifications,
                stringResource(Res.string.persistent_notification),
                stringResource(Res.string.status_statistics_description),
                platform.statusRunning && store.enableStatusService,
                host::setStatusServiceEnabled,
            )
            TriggerOverviewCard(
                {
                    val groups = rules.value?.groups
                    AnimatedVisibility(
                        visible = groups != null,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = ExitTransition.None,
                    ) {
                        if (groups != null) {
                            GkRuleStats(
                                stats =
                                    GkRuleStatsData(
                                        globalGroups = groups.globalGroups.size,
                                        apps = groups.appSize,
                                        appGroups = groups.appGroupSize,
                                    ),
                                triggerCount = actionCount,
                                emptyText = "",
                                textStyle = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (rules is Loadable.Failure) {
                        Text(stringResource(Res.string.data_load_failed))
                    }
                },
                HomeDataText.latest(
                    latest,
                    subscriptions.value?.subscriptions.orEmpty(),
                    appCatalog.snapshot?.apps.orEmpty(),
                ),
                (latest?.groupType == RuleGroupType.Global),
                { mainVm.navigator.navigate(ActionLogRoute()) },
                {
                    latest?.let {
                        mainVm.navigator.navigate(AppConfigRoute(it.appId, focusLog = it))
                    }
                },
                (latestState is Loadable.Failure),
            )
            if (platform.activityRunning) {
                PageItemCard(
                    GkIcons.Layers,
                    stringResource(Res.string.activity_log_title),
                    stringResource(Res.string.activity_record_description),
                    stringResource(Res.string.activity_log_open),
                    { mainVm.navigator.navigate(ActivityLogRoute) },
                )
            }
            val uploading by mainVm.feedbackUpload.uploadingFlow.collectAsStateWithLifecycle()
            UploadReportCard(
                uploading = uploading,
                onUpload = {
                    scope.launchUi {
                        val confirmed =
                            mainVm.dialogRequests.confirm(
                                title = getString(Res.string.upload_feedback_confirm_title),
                                text = getString(Res.string.upload_feedback_confirm_text),
                                confirmText = getString(Res.string.action_continue),
                            )
                        if (confirmed) {
                            mainVm.feedbackUpload.upload("手动日志")
                        }
                    }
                },
            )
            PageItemCard(
                GkIcons.HelpOutline,
                stringResource(Res.string.help_center_title),
                stringResource(Res.string.help_center_subtitle),
                stringResource(Res.string.help_center_open),
                { mainVm.navigator.navigate(HelpCenterRoute) },
            )
            GkPageBottomSpace()
        }
    }
}

@Composable
private fun DashboardWarningCard(
    title: String,
    description: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().padding(bottom = itemHorizontalPadding / 2).semantics(
                mergeDescendants = true,
            ) {
                onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GkIcon(GkIcons.WarningAmber)
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            GkIcon(GkIcons.KeyboardArrowRight)
        }
    }
}

@Composable
private fun PageItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(imageVector = imageVector) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageSwitchItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onClick = { onCheckedChange(!checked) }
    val toggleLabel = stringResource(Res.string.item_toggle_description, title)
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                this.onClick(label = toggleLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(imageVector = imageVector) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            GkSwitch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun GuardianHeroCard(
    statusText: String,
    running: Boolean,
    actionCount: Long,
    onToggle: (Boolean) -> Unit,
    mode: String,
    onModeClick: () -> Unit,
) {
    val toggleLabel = stringResource(Res.string.service_state_toggle)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        onClickLabel = toggleLabel,
                        onClick = { onToggle(!running) },
                    )
                    .padding(top = 24.dp, bottom = 14.dp)
                    .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier.size(88.dp)
                        .clip(CircleShape)
                        .background(
                            if (running) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(
                            onClickLabel = toggleLabel,
                            onClick = { onToggle(!running) },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                GkIcon(
                    imageVector = if (running) GkIcons.VerifiedUser else GkIcons.Stop,
                    modifier = Modifier.size(40.dp),
                    tint =
                        if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text =
                    stringResource(
                        if (running) Res.string.hero_status_running
                        else Res.string.hero_status_stopped
                    ),
                style = MaterialTheme.typography.titleLarge,
                color =
                    if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = actionCount.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.hero_action_count_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        HorizontalDivider(
            modifier =
                Modifier.padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                ),
        )
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        onClickLabel = stringResource(Res.string.work_mode_open),
                        onClick = onModeClick,
                    )
                    .padding(
                        start = itemVerticalPadding,
                        end = itemVerticalPadding,
                        top = 10.dp,
                        bottom = 10.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GkIcon(
                imageVector = GkIcons.AutoMode,
                modifier = Modifier.padding(horizontal = 10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Text(
                text = stringResource(Res.string.work_mode_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = mode,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GkIcon(
                imageVector = GkIcons.KeyboardArrowRight,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun UploadReportCard(
    uploading: Boolean,
    onUpload: () -> Unit,
) {
    val onClickLabel = stringResource(Res.string.upload_feedback)
    Card(
        modifier =
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = { if (!uploading) onUpload() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(itemVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uploading) {
                GkRotatingLoadingIcon(
                    loading = true,
                    imageVector = GkIcons.CloudUpload,
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp)
                            .size(24.dp),
                )
            } else {
                GkIcon(
                    imageVector = GkIcons.CloudUpload,
                    modifier =
                        Modifier.clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp)
                            .size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.upload_feedback),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text =
                        stringResource(
                            if (uploading) Res.string.upload_feedback_in_progress
                            else Res.string.upload_feedback_manual_subtitle
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GkIcon(
            imageVector = imageVector,
            modifier =
                Modifier.clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(itemHorizontalPadding))
        content()
    }
}

@Composable
private fun TriggerOverviewCard(
    statistics: @Composable () -> Unit,
    latestRecordDesc: String?,
    latestRecordIsGlobal: Boolean,
    onOpenActionLog: () -> Unit,
    onOpenLatestRecord: () -> Unit,
    latestLoadFailed: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = surfaceCardColors,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        onClickLabel = stringResource(Res.string.action_log_open),
                        onClick = onOpenActionLog,
                    )
                    .padding(
                        start = itemVerticalPadding,
                        end = itemVerticalPadding,
                        top = itemVerticalPadding,
                        bottom = itemVerticalPadding / 2,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GkIcon(
                imageVector = GkIcons.History,
                modifier =
                    Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp)
                        .size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.action_log_title),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(Res.string.action_log_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GkIcon(
                imageVector = GkIcons.KeyboardArrowRight,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null,
            )
        }
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable(
                        enabled = latestRecordDesc != null,
                        onClickLabel = stringResource(Res.string.app_rule_summary_open),
                        onClick = onOpenLatestRecord,
                    )
                    .padding(horizontal = itemVerticalPadding + 8.dp)
                    .padding(bottom = itemVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            statistics()

            if (latestLoadFailed) {
                Text(
                    text =
                        stringResource(Res.string.action_log_recent_prefix) +
                            stringResource(Res.string.data_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        GkGroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = stringResource(Res.string.action_log_recent_prefix),
                            isGlobal = latestRecordIsGlobal,
                            text = latestRecordDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    GkIcon(
                        imageVector = GkIcons.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
