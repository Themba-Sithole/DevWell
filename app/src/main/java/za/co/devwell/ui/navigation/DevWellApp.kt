package za.co.devwell.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import za.co.devwell.ui.components.AnimatedGreenBackground
import za.co.devwell.ui.theme.AccentSurface
import za.co.devwell.ui.theme.BorderGreen
import za.co.devwell.ui.theme.BrandGradient
import za.co.devwell.ui.theme.CardWhite
import za.co.devwell.ui.theme.DevGreen
import za.co.devwell.ui.theme.InkMuted
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import za.co.devwell.model.*
import za.co.devwell.viewmodel.AuthViewModel
import za.co.devwell.ui.admin.AdminHomeScreen
import za.co.devwell.ui.auth.LoginScreen
import za.co.devwell.ui.auth.OnboardingScreen
import za.co.devwell.ui.auth.PendingApprovalScreen
import za.co.devwell.ui.engineer.CheckInScreen
import za.co.devwell.ui.engineer.EngineerHomeScreen
import za.co.devwell.ui.engineer.EngineerInsightsScreen
import za.co.devwell.ui.engineer.PrivacyPreviewScreen
import za.co.devwell.ui.engineer.TeamOverviewScreen
import za.co.devwell.ui.engineer.TrendsScreen
import za.co.devwell.ui.manager.JoinRequestsRoute
import za.co.devwell.ui.manager.ManagerHomeScreen
import za.co.devwell.ui.manager.ManagerInsightsScreen
import za.co.devwell.ui.manager.ManagerTeamScreen
import za.co.devwell.ui.manager.TeamSettingsRoute
import za.co.devwell.ui.projects.ProjectsScreen
import za.co.devwell.ui.settings.AccountSettingsScreen
import za.co.devwell.ui.settings.AiAssistantScreen
import za.co.devwell.ui.settings.ExercisesScreen
import za.co.devwell.ui.settings.HelpAboutScreen
import za.co.devwell.ui.settings.NotificationsScreen
import za.co.devwell.ui.settings.ProfileScreen
import za.co.devwell.ui.settings.SettingsScreen
import za.co.devwell.ui.settings.WellbeingTipsScreen
import za.co.devwell.ui.settings.WhatMyManagerSeesScreen
import za.co.devwell.viewmodel.DevWellViewModel
import za.co.devwell.viewmodel.ManagerViewModel
import za.co.devwell.viewmodel.ManagerViewModelFactory
import za.co.devwell.data.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import za.co.devwell.notifications.NotificationScheduler

private data class TabItem(val route: String, val label: String, val icon: ImageVector, val fab: Boolean = false)

@Composable
fun DevWellApp(viewModel: DevWellViewModel, authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val state by authViewModel.ui.collectAsStateWithLifecycle()
    val destination = entryDestination(state.user)
    LaunchedEffect(state.user?.id, destination) {
        if (destination == EntryDestination.ONBOARDING) authViewModel.refreshDirectory()
    }
    if (state.starting) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text("Opening DevWell...")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    // Auth screens are outside the protected graph. Back cannot reopen a previous session.
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            fadeIn(tween(300, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(180, easing = FastOutSlowInEasing))
        },
        label = "entryDestination",
        modifier = Modifier.fillMaxSize(),
    ) { target ->
        when (target) {
            EntryDestination.LOGIN -> LoginScreen(state, authViewModel::signIn, authViewModel::signUp,
                authViewModel::clearFeedback, authViewModel::startPreview)
            EntryDestination.ONBOARDING -> OnboardingScreen(state, authViewModel::submitJoinRequest,
                authViewModel::refreshDirectory, authViewModel::refreshProfile, authViewModel::signOut)
            EntryDestination.PENDING -> PendingApprovalScreen(state, authViewModel::refreshProfile,
                authViewModel::signOut, authViewModel::previewReview)
            else -> {
                // Keep the user for the duration of the outgoing cross-fade so sign-out
                // does not tear down the protected graph mid-animation.
                val activeUser = remember(target) { state.user }
                if (activeUser != null) {
                    key(activeUser.id, activeUser.role, state.preview) {
                        // activeUser only pins the outgoing user so sign-out cannot tear
                        // down the graph mid-animation; state.user tracks live profile edits.
                        RoleApp(viewModel, state.user ?: activeUser, authViewModel, state.preview)
                    }
                }
            }
        }
    }
}
@Composable
private fun RoleApp(
    viewModel: DevWellViewModel,
    user: DevWellUser,
    authViewModel: AuthViewModel,
    preview: Boolean,
) {
    val onSignOut = authViewModel::signOut
    val account by authViewModel.ui.collectAsStateWithLifecycle()
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val aggregate by viewModel.teamAggregate.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val notificationSyncStatus by viewModel.notificationSyncStatus.collectAsStateWithLifecycle()
    val notificationContext = LocalContext.current
    LaunchedEffect(user.id, notificationPrefs) {
        NotificationScheduler.applyPreferences(notificationContext, notificationPrefs)
    }
    val adminStats by viewModel.adminStats.collectAsStateWithLifecycle()
    val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
    val adminLoading by viewModel.adminLoading.collectAsStateWithLifecycle()
    val adminBusyUserId by viewModel.adminBusyUserId.collectAsStateWithLifecycle()
    val adminErrorMessage by viewModel.adminErrorMessage.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()

    val todaysCheckIn = TrendsAnalysis.todayCheckIn(checkIns)

    val navController = rememberNavController()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.ENGINEER_HOME, Routes.PROJECTS, Routes.AI_ASSISTANT, Routes.ENGINEER_PROFILE,
        Routes.MANAGER_HOME, Routes.MANAGER_TEAM, Routes.MANAGER_INSIGHTS, Routes.MANAGER_PROFILE,
        Routes.ADMIN_HOME, Routes.ADMIN_PROFILE,
    )

    val tabs = when (user.role) {
        UserRole.ENGINEER -> listOf(
            TabItem(Routes.ENGINEER_HOME, "Home", Icons.Outlined.Home),
            TabItem(Routes.PROJECTS, "Projects", Icons.Outlined.Folder),
            TabItem(Routes.CHECK_IN, "Check-in", Icons.Outlined.CheckCircle, fab = true),
            TabItem(Routes.AI_ASSISTANT, "AI", Icons.Outlined.AutoAwesome),
            TabItem(Routes.ENGINEER_PROFILE, "Profile", Icons.Outlined.Person),
        )
        UserRole.MANAGER -> listOf(
            TabItem(Routes.MANAGER_HOME, "Overview", Icons.Outlined.Home),
            TabItem(Routes.MANAGER_TEAM, "Team", Icons.Outlined.Groups),
            TabItem(Routes.MANAGER_INSIGHTS, "Insights", Icons.Outlined.Lightbulb),
            TabItem(Routes.MANAGER_PROFILE, "Profile", Icons.Outlined.Person),
        )
        UserRole.ADMIN -> listOf(
            TabItem(Routes.ADMIN_HOME, "Admin", Icons.Outlined.Dashboard),
            TabItem(Routes.ADMIN_PROFILE, "Profile", Icons.Outlined.Person),
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Text(if (preview) "Student preview - sample data" else "Student prototype - sample dashboard data",
                modifier = Modifier.statusBarsPadding().padding(8.dp), style = MaterialTheme.typography.labelSmall)
        },
        bottomBar = {
            if (showBottomBar && tabs.isNotEmpty()) {
                Surface(
                    color = CardWhite,
                    shadowElevation = 18.dp,
                    border = BorderStroke(1.dp, BorderGreen),
                ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = DevGreen,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(tabs.first().route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.fab) {
                                    Box(
                                        Modifier.size(44.dp)
                                            .shadow(8.dp, CircleShape)
                                            .background(Brush.linearGradient(BrandGradient), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            tab.icon, contentDescription = tab.label,
                                            Modifier.size(22.dp), tint = Color.White,
                                        )
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = DevGreen,
                                unselectedIconColor = InkMuted,
                                selectedTextColor = DevGreen,
                                unselectedTextColor = InkMuted,
                                indicatorColor = AccentSurface,
                            ),
                        )
                    }
                }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        AnimatedGreenBackground(Modifier.matchParentSize())
        NavHost(
            navController = navController,
            startDestination = when (user.role) {
                UserRole.ENGINEER -> Routes.ENGINEER_HOME
                UserRole.MANAGER -> Routes.MANAGER_HOME
                UserRole.ADMIN -> Routes.ADMIN_HOME
            },
            modifier = Modifier.padding(padding),
            enterTransition = forwardEnter,
            exitTransition = forwardExit,
            popEnterTransition = backwardEnter,
            popExitTransition = backwardExit,
        ) {
            if (user.role == UserRole.ENGINEER) {
            // Engineer
            tabComposable(Routes.ENGINEER_HOME) {
                EngineerHomeScreen(
                    name = user.name,
                    onCheckIn = {
                        navController.navigate(Routes.CHECK_IN) { launchSingleTop = true }
                    },
                    onTrends = { navController.navigate(Routes.TRENDS) },
                    onInsights = { navController.navigate(Routes.INSIGHTS) },
                    onTeamOverview = { navController.navigate(Routes.TEAM_OVERVIEW) },
                    onProfile = { navController.navigate(Routes.ENGINEER_PROFILE) },
                    checkedInToday = todaysCheckIn != null,
                )
            }
                composable(Routes.CHECK_IN) {
                    val checkInState by viewModel.checkInSaveState.collectAsStateWithLifecycle()

                    LaunchedEffect(checkInState) {
                        if (checkInState is za.co.devwell.viewmodel.SaveState.Success) {
                            navController.navigate(Routes.TRENDS) {
                                popUpTo(Routes.ENGINEER_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                            viewModel.resetCheckInSaveState()
                        }
                    }

                    CheckInScreen(
                        onCancel = {
                            viewModel.resetCheckInSaveState()
                            navController.popBackStack()
                        },
                        onSubmit = { viewModel.saveCheckIn(it) },
                        isSaving = checkInState is za.co.devwell.viewmodel.SaveState.Loading,
                        errorMessage = (checkInState as? za.co.devwell.viewmodel.SaveState.Error)?.message,
                        initial = todaysCheckIn?.values,
                    )
                }
            composable(Routes.TRENDS) {
                TrendsScreen(checkIns, onBack = { navController.popBackStack() })
            }
            composable(Routes.INSIGHTS) {
                EngineerInsightsScreen(checkIns, user.name, onBack = { navController.popBackStack() })
            }
            composable(Routes.TEAM_OVERVIEW) {
                TeamOverviewScreen(aggregate, onBack = { navController.popBackStack() })
            }
            tabComposable(Routes.PROJECTS) {
                ProjectsScreen(
                    projects = projects,
                    onToggle = viewModel::toggleTask,
                    onCreateProject = viewModel::createProject,
                    onUpdateProject = viewModel::updateProject,
                    onDeleteProject = viewModel::deleteProject,
                    onAddTask = viewModel::addTask,
                    onUpdateTask = viewModel::updateTask,
                    onDeleteTask = viewModel::deleteTask,
                )
            }
            tabComposable(Routes.AI_ASSISTANT) {
                val chat by viewModel.aiChat.collectAsStateWithLifecycle()
                AiAssistantScreen(
                    messages = chat.messages,
                    sending = chat.sending,
                    error = chat.error,
                    liveAssistantAvailable = !preview && user.status == AccountStatus.ACTIVE,
                    previewAssistantAvailable = preview,
                    onSend = { text -> viewModel.sendAiMessage(text, preview) },
                    onRetry = { viewModel.retryAi(preview) },
                    onNewChat = viewModel::clearAiChat,
                )
            }
            tabComposable(Routes.ENGINEER_PROFILE) {
                val context = LocalContext.current
                ProfileScreen(
                    role = UserRole.ENGINEER,
                    onSignOut = onSignOut,
                    name = user.name,
                    email = user.email,
                    prefs = notificationPrefs,
                    onToggleDaily = { enabled ->
                        val updated = notificationPrefs.copy(dailyEnabled = enabled)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onReminderTimeChange = { time ->
                        val updated = notificationPrefs.copy(reminderTime = time)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onTips = { navController.navigate(Routes.TIPS) },
                    onExercises = { navController.navigate(Routes.EXERCISES) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            }
            if (user.role == UserRole.MANAGER) {
            // Manager
            tabComposable(Routes.MANAGER_HOME) {
                ManagerHomeScreen(
                    aggregate = aggregate,
                    onJoinRequests = { navController.navigate(Routes.JOIN_REQUESTS) },
                    onTeamSettings = { navController.navigate(Routes.TEAM_SETTINGS) },
                    onProfile = { navController.navigate(Routes.MANAGER_PROFILE) },
                    managerName = user.name,
                )
            }
            tabComposable(Routes.MANAGER_TEAM) {
                ManagerTeamScreen(
                    aggregate = aggregate,
                    onJoinRequests = { navController.navigate(Routes.JOIN_REQUESTS) },
                    onTeamSettings = { navController.navigate(Routes.TEAM_SETTINGS) },
                )
            }
            tabComposable(Routes.MANAGER_INSIGHTS) { ManagerInsightsScreen(aggregate) }
            composable(Routes.JOIN_REQUESTS) {
                val managerViewModel: ManagerViewModel = viewModel(factory = ManagerViewModelFactory(AppContainer.manager))
                JoinRequestsRoute(managerViewModel)
            }
            composable(Routes.TEAM_SETTINGS) {
                val managerViewModel: ManagerViewModel = viewModel(factory = ManagerViewModelFactory(AppContainer.manager))
                TeamSettingsRoute(managerViewModel)
            }
            tabComposable(Routes.MANAGER_PROFILE) {
                val context = LocalContext.current
                ProfileScreen(
                    role = UserRole.MANAGER,
                    onSignOut = onSignOut,
                    name = user.name,
                    email = user.email,
                    prefs = notificationPrefs,
                    onToggleDaily = { enabled ->
                        val updated = notificationPrefs.copy(dailyEnabled = enabled)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onReminderTimeChange = { time ->
                        val updated = notificationPrefs.copy(reminderTime = time)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            }
            if (user.role == UserRole.ADMIN) {
            // Admin
            tabComposable(Routes.ADMIN_HOME) {
                AdminHomeScreen(
                    stats = adminStats,
                    users = adminUsers,
                    adminUserId = user.id,
                    isLoading = adminLoading,
                    busyUserId = adminBusyUserId,
                    errorMessage = adminErrorMessage,
                    onRefresh = { viewModel.refreshAdminDashboard() },
                    onDismissError = viewModel::clearAdminError,
                    onSetUserStatus = viewModel::setUserStatus,
                )
            }
            tabComposable(Routes.ADMIN_PROFILE) {
                val context = LocalContext.current
                ProfileScreen(
                    role = UserRole.ADMIN,
                    onSignOut = onSignOut,
                    name = user.name,
                    email = user.email,
                    prefs = notificationPrefs,
                    onToggleDaily = { enabled ->
                        val updated = notificationPrefs.copy(dailyEnabled = enabled)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onReminderTimeChange = { time ->
                        val updated = notificationPrefs.copy(reminderTime = time)
                        viewModel.updateNotificationPrefs(updated)
                        NotificationScheduler.applyPreferences(context, updated)
                    },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            }
            // Shared: the manager preview is reachable from any role's settings.
            composable(Routes.PRIVACY_PREVIEW) {
                PrivacyPreviewScreen(aggregate, onBack = { navController.popBackStack() })
            }
            composable(Routes.NOTIFICATIONS) {
                val context = LocalContext.current
                NotificationsScreen(
                    prefs = notificationPrefs,
                    syncStatus = notificationSyncStatus,
                    onSave = { prefs ->
                        viewModel.updateNotificationPrefs(prefs)
                        NotificationScheduler.applyPreferences(context, prefs)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.TIPS) {
                WellbeingTipsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.EXERCISES) {
                ExercisesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    role = user.role,
                    name = user.name,
                    email = user.email,
                    onAccountProfile = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                    onManagerVisibility = { navController.navigate(Routes.MANAGER_VISIBILITY) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onHelpAbout = { navController.navigate(Routes.HELP_ABOUT) },
                    onTeamSettings = { navController.navigate(Routes.TEAM_SETTINGS) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ACCOUNT_SETTINGS) {
                AccountSettingsScreen(
                    name = user.name,
                    email = user.email,
                    busy = account.busy,
                    error = account.error,
                    message = account.message,
                    onSaveProfile = authViewModel::updateProfileDetails,
                    onChangePassword = authViewModel::updatePassword,
                    onDismissFeedback = authViewModel::clearFeedback,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MANAGER_VISIBILITY) {
                WhatMyManagerSeesScreen(
                    onBack = { navController.popBackStack() },
                    onManagerPreview = { navController.navigate(Routes.PRIVACY_PREVIEW) },
                )
            }
            composable(Routes.HELP_ABOUT) {
                HelpAboutScreen(onBack = { navController.popBackStack() })
            }

        }
        }
    }
}
