package za.co.devwell.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import za.co.devwell.ui.engineer.PrivacyPreviewScreen
import za.co.devwell.ui.engineer.TrendsScreen
import za.co.devwell.ui.manager.JoinRequestsRoute
import za.co.devwell.ui.manager.ManagerHomeScreen
import za.co.devwell.ui.manager.TeamSettingsRoute
import za.co.devwell.ui.projects.ProjectsScreen
import za.co.devwell.ui.settings.AiAssistantScreen
import za.co.devwell.ui.settings.NotificationsScreen
import za.co.devwell.ui.settings.ProfileScreen
import za.co.devwell.viewmodel.DevWellViewModel
import za.co.devwell.viewmodel.ManagerViewModel
import za.co.devwell.viewmodel.ManagerViewModelFactory
import za.co.devwell.data.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import za.co.devwell.notifications.NotificationScheduler

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

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
    when (destination) {
        EntryDestination.LOGIN -> LoginScreen(state, authViewModel::signIn, authViewModel::signUp,
            authViewModel::clearFeedback, authViewModel::startPreview)
        EntryDestination.ONBOARDING -> OnboardingScreen(state, authViewModel::submitJoinRequest,
            authViewModel::refreshDirectory, authViewModel::refreshProfile, authViewModel::signOut)
        EntryDestination.PENDING -> PendingApprovalScreen(state, authViewModel::refreshProfile,
            authViewModel::signOut, authViewModel::previewReview)
        else -> key(state.user!!.id, state.user!!.role, state.preview) {
            RoleApp(viewModel, state.user!!, authViewModel::signOut, state.preview)
        }
    }
}
@Composable
private fun RoleApp(viewModel: DevWellViewModel, user: DevWellUser, onSignOut: () -> Unit, preview: Boolean) {

    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val aggregate by viewModel.teamAggregate.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val adminStats by viewModel.adminStats.collectAsStateWithLifecycle()
    val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.ENGINEER_HOME, Routes.TRENDS, Routes.PROJECTS, Routes.ENGINEER_PROFILE,
        Routes.MANAGER_HOME, Routes.MANAGER_PROFILE,
        Routes.ADMIN_HOME, Routes.ADMIN_PROFILE,
    )

    val tabs = when (user.role) {
        UserRole.ENGINEER -> listOf(
            TabItem(Routes.ENGINEER_HOME, "Home", Icons.Outlined.Home),
            TabItem(Routes.TRENDS, "Trends", Icons.AutoMirrored.Outlined.ShowChart),
            TabItem(Routes.PROJECTS, "Projects", Icons.Outlined.Checklist),
            TabItem(Routes.ENGINEER_PROFILE, "Profile", Icons.Outlined.Person),
        )
        UserRole.MANAGER -> listOf(
            TabItem(Routes.MANAGER_HOME, "Dashboard", Icons.Outlined.Dashboard),
            TabItem(Routes.MANAGER_PROFILE, "Profile", Icons.Outlined.Person),
        )
        UserRole.ADMIN -> listOf(
            TabItem(Routes.ADMIN_HOME, "Admin", Icons.Outlined.Dashboard),
            TabItem(Routes.ADMIN_PROFILE, "Profile", Icons.Outlined.Person),
        )
    }

    Scaffold(
        topBar = {
            Text(if (preview) "Student preview - sample data" else "Student prototype - sample dashboard data",
                modifier = Modifier.statusBarsPadding().padding(8.dp), style = MaterialTheme.typography.labelSmall)
        },
        bottomBar = {
            if (showBottomBar && tabs.isNotEmpty()) {
                NavigationBar {
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
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = when (user.role) {
                UserRole.ENGINEER -> Routes.ENGINEER_HOME
                UserRole.MANAGER -> Routes.MANAGER_HOME
                UserRole.ADMIN -> Routes.ADMIN_HOME
            },
            modifier = Modifier.padding(padding),
        ) {
            if (user.role == UserRole.ENGINEER) {
            // Engineer
            composable(Routes.ENGINEER_HOME) {
                EngineerHomeScreen(
                    name = user.name,
                    onCheckIn = {
                        navController.navigate(Routes.CHECK_IN) { launchSingleTop = true }
                    },
                    onManagerPreview = { navController.navigate(Routes.PRIVACY_PREVIEW) },
                    onAiAssistant = { navController.navigate(Routes.AI_ASSISTANT) },
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
                    )
                }
            composable(Routes.TRENDS) { TrendsScreen(checkIns) }
            composable(Routes.PROJECTS) {
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
            composable(Routes.PRIVACY_PREVIEW) { PrivacyPreviewScreen(aggregate) }
            composable(Routes.AI_ASSISTANT) {
                AiAssistantScreen(
                    onAsk = { question, isPreview, callback ->
                        viewModel.askAi(question, isPreview, callback)
                    },
                    liveAssistantAvailable = !preview && user.status == AccountStatus.ACTIVE,
                    previewAssistantAvailable = preview,
                )
            }
            composable(Routes.ENGINEER_PROFILE) {
                ProfileScreen(
                    role = UserRole.ENGINEER,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = onSignOut,
                )
            }
            }
            if (user.role == UserRole.MANAGER) {
            // Manager
            composable(Routes.MANAGER_HOME) {
                ManagerHomeScreen(
                    aggregate = aggregate,
                    onJoinRequests = { navController.navigate(Routes.JOIN_REQUESTS) },
                    onTeamSettings = { navController.navigate(Routes.TEAM_SETTINGS) },
                )
            }
            composable(Routes.JOIN_REQUESTS) {
                val managerViewModel: ManagerViewModel = viewModel(factory = ManagerViewModelFactory(AppContainer.manager))
                JoinRequestsRoute(managerViewModel)
            }
            composable(Routes.TEAM_SETTINGS) {
                val managerViewModel: ManagerViewModel = viewModel(factory = ManagerViewModelFactory(AppContainer.manager))
                TeamSettingsRoute(managerViewModel)
            }
            composable(Routes.MANAGER_PROFILE) {
                ProfileScreen(
                    role = UserRole.MANAGER,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = onSignOut,
                )
            }

            }
            if (user.role == UserRole.ADMIN) {
            // Admin
            composable(Routes.ADMIN_HOME) {
                AdminHomeScreen(
                    stats = adminStats,
                    users = adminUsers,
                    onSetUserStatus = viewModel::setUserStatus,
                )
            }
            composable(Routes.ADMIN_PROFILE) {
                ProfileScreen(
                    role = UserRole.ADMIN,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = onSignOut,
                )
            }
            }
            composable(Routes.NOTIFICATIONS) {
                val context = LocalContext.current
                NotificationsScreen(
                    prefs = notificationPrefs,
                    onSave = { prefs ->
                        viewModel.updateNotificationPrefs(prefs)
                        NotificationScheduler.applyPreferences(context, prefs)
                    },
                )
            }

        }
    }
}
