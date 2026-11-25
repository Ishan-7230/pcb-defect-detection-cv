package com.industry4.inspection.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.industry4.inspection.Industry4Application
import com.industry4.inspection.ui.dashboard.DashboardScreen
import com.industry4.inspection.ui.history.HistoryScreen
import com.industry4.inspection.ui.history.InspectionDetailScreen
import com.industry4.inspection.ui.imageupload.ImageUploadScreen
import com.industry4.inspection.ui.inspection.LiveInspectionScreen
import com.industry4.inspection.ui.performance.PerformanceScreen
import com.industry4.inspection.ui.settings.SettingsScreen
import com.industry4.inspection.viewmodel.DashboardViewModel
import com.industry4.inspection.viewmodel.HistoryViewModel
import com.industry4.inspection.viewmodel.ImageUploadViewModel
import com.industry4.inspection.viewmodel.InspectionViewModel
import com.industry4.inspection.viewmodel.PerformanceViewModel
import com.industry4.inspection.viewmodel.SettingsViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    app: Industry4Application
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            val vm: DashboardViewModel = viewModel(
                factory = DashboardViewModel.provideFactory(app.modelManager, app.repository)
            )
            DashboardScreen(
                viewModel = vm,
                onNavigateToLive = { navController.navigate(Screen.LiveInspection.route) },
                onNavigateToUpload = { navController.navigate(Screen.ImageUpload.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToPerformance = { navController.navigate(Screen.Performance.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onSelectInspection = { id -> navController.navigate(Screen.InspectionDetail.createRoute(id)) }
            )
        }

        composable(Screen.LiveInspection.route) {
            val vm: InspectionViewModel = viewModel(
                factory = InspectionViewModel.provideFactory(app.modelManager, app.repository)
            )
            LiveInspectionScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ImageUpload.route) {
            val vm: ImageUploadViewModel = viewModel(
                factory = ImageUploadViewModel.provideFactory(app.modelManager, app.repository)
            )
            ImageUploadScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.History.route) {
            val vm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.provideFactory(app.repository)
            )
            HistoryScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() },
                onSelectInspection = { id -> navController.navigate(Screen.InspectionDetail.createRoute(id)) }
            )
        }

        composable(
            route = Screen.InspectionDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: ""
            val vm: HistoryViewModel = viewModel(
                factory = HistoryViewModel.provideFactory(app.repository)
            )
            InspectionDetailScreen(
                inspectionId = id,
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Performance.route) {
            val vm: PerformanceViewModel = viewModel(
                factory = PerformanceViewModel.provideFactory(app.modelManager, app.repository)
            )
            PerformanceScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(app.modelManager)
            )
            SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
