package za.co.devwell.ui.navigation

import androidx.compose.foundation.layout.padding
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
import za.co.devwell.model.UserRole
import za.co.devwell.ui.admin.AdminHomeScreen
import za.co.devwell.ui.auth.LoginScreen
import za.co.devwell.ui.auth.OnboardingScreen
import za.co.devwell.ui.auth.PendingApprovalScreen
import za.co.devwell.ui.engineer.CheckInScreen
import za.co.devwell.ui.engineer.EngineerHomeScreen
import za.co.devwell.ui.engineer.PrivacyPreviewScreen
import za.co.devwell.ui.engineer.TrendsScreen
import za.co.devwell.ui.manager.JoinRequestsScreen
import za.co.devwell.ui.manager.ManagerHomeScreen
import za.co.devwell.ui.manager.TeamSettingsScreen
import za.co.devwell.ui.projects.ProjectsScreen
import za.co.devwell.ui.settings.AiAssistantScreen
import za.co.devwell.ui.settings.NotificationsScreen
import za.co.devwell.ui.settings.ProfileScreen
import za.co.devwell.viewmodel.DevWellViewModel
//added below
import androidx.compose.ui.platform.LocalContext
import za.co.devwell.notifications.NotificationScheduler

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun DevWellApp(viewModel: DevWellViewModel) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val checkIns by viewModel.checkIns.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val aggregate by viewModel.teamAggregate.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val managedTeam by viewModel.managedTeam.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPrefs.collectAsStateWithLifecycle()
    val adminStats by viewModel.adminStats.collectAsStateWithLifecycle()
    val companies by viewModel.companies.collectAsStateWithLifecycle()
    val teams by viewModel.teams.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    // Reset to role home whenever the signed-in user changes.
    LaunchedEffect(user?.id, user?.role) {
        when (user?.role) {
            null -> navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
            UserRole.ENGINEER -> navController.navigate(Routes.ENGINEER_HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            UserRole.MANAGER -> navController.navigate(Routes.MANAGER_HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            UserRole.ADMIN -> navController.navigate(Routes.ADMIN_HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in setOf(
        Routes.ENGINEER_HOME, Routes.TRENDS, Routes.PROJECTS, Routes.ENGINEER_PROFILE,
        Routes.MANAGER_HOME, Routes.MANAGER_PROFILE,
        Routes.ADMIN_HOME, Routes.ADMIN_PROFILE,
    )

    val tabs = when (user?.role) {
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
        null -> emptyList()
    }

    Scaffold(
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
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(onLogin = viewModel::loginAs)
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    companies = companies,
                    teams = teams,
                    onSubmit = { companyId, teamId ->
                        viewModel.submitJoinRequest(companyId, teamId)
                        navController.navigate(Routes.PENDING)
                    },
                )
            }
            composable(Routes.PENDING) {
                PendingApprovalScreen(onBackToLogin = viewModel::signOut)
            }

            // Engineer
            composable(Routes.ENGINEER_HOME) {
                EngineerHomeScreen(
                    name = user?.name.orEmpty(),
                    onCheckIn = { navController.navigate(Routes.CHECK_IN) },
                    onManagerPreview = { navController.navigate(Routes.PRIVACY_PREVIEW) },
                    onAiAssistant = { navController.navigate(Routes.AI_ASSISTANT) },
                )
            }
            composable(Routes.CHECK_IN) {
                CheckInScreen(
                    onCancel = { navController.popBackStack() },
                    onSubmit = {
                        viewModel.saveCheckIn(it)
                        navController.navigate(Routes.TRENDS) {
                            popUpTo(Routes.ENGINEER_HOME)
                        }
                    },
                )
            }
            composable(Routes.TRENDS) { TrendsScreen(checkIns) }
            composable(Routes.PROJECTS) {
                ProjectsScreen(projects, viewModel::toggleTask)
            }
            composable(Routes.PRIVACY_PREVIEW) { PrivacyPreviewScreen(aggregate) }
            composable(Routes.AI_ASSISTANT) {
                AiAssistantScreen(onAsk = viewModel::askAi)
            }
            composable(Routes.ENGINEER_PROFILE) {
                ProfileScreen(
                    role = UserRole.ENGINEER,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = viewModel::signOut,
                )
            } // added notification scheduler
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

            // Manager
            composable(Routes.MANAGER_HOME) {
                ManagerHomeScreen(
                    aggregate = aggregate,
                    onJoinRequests = { navController.navigate(Routes.JOIN_REQUESTS) },
                    onTeamSettings = { navController.navigate(Routes.TEAM_SETTINGS) },
                )
            }
            composable(Routes.JOIN_REQUESTS) {
                JoinRequestsScreen(joinRequests, viewModel::reviewJoinRequest)
            }
            composable(Routes.TEAM_SETTINGS) {
                TeamSettingsScreen(managedTeam, viewModel::updateTeamSettings)
            }
            composable(Routes.MANAGER_PROFILE) {
                ProfileScreen(
                    role = UserRole.MANAGER,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = viewModel::signOut,
                )
            }

            // Admin
            composable(Routes.ADMIN_HOME) { AdminHomeScreen(adminStats) }
            composable(Routes.ADMIN_PROFILE) {
                ProfileScreen(
                    role = UserRole.ADMIN,
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onSignOut = viewModel::signOut,
                )
            }
        }
    }
}
